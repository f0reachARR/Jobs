package me.f0reach.jobs.command.admin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.f0reach.jobs.JobsServices;
import me.f0reach.jobs.Permissions;
import me.f0reach.jobs.api.query.TimeRange;
import me.f0reach.jobs.domain.job.JobDefinition;
import me.f0reach.jobs.domain.job.JobId;
import me.f0reach.jobs.modifier.variety.VarietyPenaltyEvaluator;
import me.f0reach.jobs.persistence.ActionLogRepository;
import me.f0reach.jobs.persistence.dto.ActionLogRow;
import me.f0reach.jobs.persistence.dto.PlayerJobRow;
import me.f0reach.jobs.ui.DialogTexts;
import me.f0reach.jobs.util.AsyncExecutor;
import me.f0reach.jobs.util.MiniMessages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * /jobs admin サブコマンドのツリー構築と実行 handler。
 *
 * spec/08-permissions.md 「管理系」の Tier 1 + Tier 2 に対応する。
 * Phase 13-A では inspect / stats / actions の 3 コマンドを実装する。
 * 残る set / reset-cooldown / pay / reset-daily-cap / reset-variety / flush は
 * Phase 13-B 以降で追加する。
 */
public final class AdminCommands {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DEFAULT_ACTIONS_LIMIT = 20;
    private static final int MAX_ACTIONS_LIMIT = 100;
    private static final int DEFAULT_ACTIONS_SINCE_HOURS = 1;

    private final AtomicReference<JobsServices> services;

    public AdminCommands(AtomicReference<JobsServices> services) {
        this.services = services;
    }

    /** /jobs admin の subtree を返す。JobsCommands から .then(build()) で組み込む。 */
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("admin")
                .then(buildInspect())
                .then(buildStats())
                .then(buildActions());
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildInspect() {
        return Commands.literal("inspect")
                .requires(s -> s.getSender().hasPermission(Permissions.ADMIN_INSPECT))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(this::suggestPlayerNames)
                        .executes(this::executeInspect));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildActions() {
        return Commands.literal("actions")
                .requires(s -> s.getSender().hasPermission(Permissions.ADMIN_ACTIONS))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(this::suggestPlayerNames)
                        .executes(this::executeActions)
                        .then(Commands.argument("hours", IntegerArgumentType.integer(1, 24 * 30))
                                .executes(this::executeActions)
                                .then(Commands.argument("limit", IntegerArgumentType.integer(1, MAX_ACTIONS_LIMIT))
                                        .executes(this::executeActions))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildStats() {
        return Commands.literal("stats")
                .requires(s -> s.getSender().hasPermission(Permissions.ADMIN_STATS))
                .executes(this::executeStats)
                .then(Commands.argument("job", StringArgumentType.word())
                        .suggests(this::suggestJobIds)
                        .executes(this::executeStatsForJob));
    }

    // --- executors ---

    private int executeInspect(CommandContext<CommandSourceStack> ctx) {
        JobsServices bound = requireBound(ctx);
        if (bound == null) return Command.SINGLE_SUCCESS;
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "player");

        OfflinePlayer target = resolveOfflinePlayer(name);
        if (target == null) {
            sender.sendMessage(bound.i18n().format(sender, DialogTexts.COMMAND_ADMIN_UNKNOWN_PLAYER,
                    Placeholder.parsed("name", name)));
            return Command.SINGLE_SUCCESS;
        }

        UUID uuid = target.getUniqueId();
        String displayName = target.getName() == null ? uuid.toString() : target.getName();

        sender.sendMessage(bound.i18n().format(sender, DialogTexts.COMMAND_ADMIN_INSPECT_HEADER,
                Placeholder.parsed("name", displayName)));

        PlayerJobRow row = bound.playerJobRepository().find(uuid).orElse(null);
        if (row == null) {
            sender.sendMessage(bound.i18n().format(sender, DialogTexts.COMMAND_ADMIN_INSPECT_NO_SPECIALTY));
            return Command.SINGLE_SUCCESS;
        }

        sender.sendMessage(bound.i18n().format(sender, DialogTexts.COMMAND_ADMIN_INSPECT_CURRENT,
                Placeholder.parsed("job", row.jobId())));
        sender.sendMessage(bound.i18n().format(sender, DialogTexts.COMMAND_ADMIN_INSPECT_COOLDOWN_BASE,
                Placeholder.parsed("base_at", formatInstant(row.cooldownBaseAt()))));

        // オフライン相手でも DB 上の cooldown_base_at から nextAvailable を計算する。
        Instant next = bound.specialtyService().nextAvailableFrom(row.cooldownBaseAt());
        sender.sendMessage(bound.i18n().format(sender, DialogTexts.COMMAND_ADMIN_INSPECT_NEXT_CHANGE,
                Placeholder.parsed("next_at", formatInstant(next))));

        // 当日累計は daily_reward_total を DB 直参照。scope は問わず TOTAL 表示だけ出す。
        double dailyTotal = bound.dailyRewardTotalRepository().getTotal(uuid, LocalDate.now(ZoneId.systemDefault()));
        sender.sendMessage(bound.i18n().format(sender, DialogTexts.COMMAND_ADMIN_INSPECT_DAILY_TOTAL,
                Placeholder.parsed("total", bound.amountFormatter().format(dailyTotal))));

        // variety は memory-only。オフラインなら snapshot が null。
        VarietyPenaltyEvaluator.Snapshot snap = bound.varietyPenaltyEvaluator()
                .snapshot(uuid, new JobId(row.jobId()));
        if (snap == null) {
            sender.sendMessage(bound.i18n().format(sender, DialogTexts.COMMAND_ADMIN_INSPECT_VARIETY_OFFLINE));
        } else {
            sender.sendMessage(bound.i18n().format(sender, DialogTexts.COMMAND_ADMIN_INSPECT_VARIETY_SNAPSHOT,
                    Placeholder.parsed("size", Integer.toString(snap.size())),
                    Placeholder.parsed("capacity", Integer.toString(snap.capacity())),
                    Placeholder.parsed("ratio", String.format(Locale.ROOT, "%.0f", snap.topRatio() * 100.0))));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int executeActions(CommandContext<CommandSourceStack> ctx) {
        JobsServices bound = requireBound(ctx);
        if (bound == null) return Command.SINGLE_SUCCESS;
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "player");
        int sinceHours = getOptionalInt(ctx, "hours", DEFAULT_ACTIONS_SINCE_HOURS);
        int limit = getOptionalInt(ctx, "limit", DEFAULT_ACTIONS_LIMIT);

        OfflinePlayer target = resolveOfflinePlayer(name);
        if (target == null) {
            sender.sendMessage(bound.i18n().format(sender, DialogTexts.COMMAND_ADMIN_UNKNOWN_PLAYER,
                    Placeholder.parsed("name", name)));
            return Command.SINGLE_SUCCESS;
        }
        UUID uuid = target.getUniqueId();
        String displayName = target.getName() == null ? uuid.toString() : target.getName();
        Instant to = Instant.now();
        Instant from = to.minus(Duration.ofHours(sinceHours));
        TimeRange range = new TimeRange(from, to);

        // async で DB を叩き、結果を main thread に戻して chat 出力する。
        AsyncExecutor executor = bound.asyncExecutor();
        int finalSinceHours = sinceHours;
        int finalLimit = limit;
        CompletableFuture<List<ActionLogRow>> future = executor.supplyAsync(
                () -> bound.actionLogRepository().recent(uuid, range, finalLimit));
        future.whenComplete((rows, err) -> executor.runOnMain(() -> {
            if (err != null) {
                sender.sendMessage(bound.i18n().format(sender, DialogTexts.COMMAND_ADMIN_ACTIONS_ERROR,
                        Placeholder.parsed("error", String.valueOf(err.getMessage()))));
                return;
            }
            sender.sendMessage(bound.i18n().format(sender, DialogTexts.COMMAND_ADMIN_ACTIONS_HEADER,
                    Placeholder.parsed("name", displayName),
                    Placeholder.parsed("limit", Integer.toString(finalLimit)),
                    Placeholder.parsed("since_hours", Integer.toString(finalSinceHours))));
            if (rows.isEmpty()) {
                sender.sendMessage(bound.i18n().format(sender, DialogTexts.COMMAND_ADMIN_ACTIONS_EMPTY));
                return;
            }
            for (ActionLogRow r : rows) {
                String rareLabel = r.rareHit() ? " [rare]" : "";
                sender.sendMessage(bound.i18n().format(sender, DialogTexts.COMMAND_ADMIN_ACTIONS_ROW,
                        Placeholder.parsed("time", formatInstant(r.occurredAt())),
                        Placeholder.parsed("job", r.jobId()),
                        Placeholder.parsed("action_key", r.actionKey()),
                        Placeholder.parsed("reward", bound.amountFormatter().format(r.finalReward())),
                        Placeholder.parsed("rare", rareLabel)));
            }
        }));
        return Command.SINGLE_SUCCESS;
    }

    private int executeStats(CommandContext<CommandSourceStack> ctx) {
        JobsServices bound = requireBound(ctx);
        if (bound == null) return Command.SINGLE_SUCCESS;
        CommandSender sender = ctx.getSource().getSender();
        AsyncExecutor executor = bound.asyncExecutor();
        Instant from = LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant to = Instant.now();
        TimeRange range = new TimeRange(from, to);

        CompletableFuture<StatsResult> future = executor.supplyAsync(() -> new StatsResult(
                bound.playerJobRepository().countByJob(),
                bound.actionLogRepository().rareHitStats(range, null)));
        future.whenComplete((result, err) -> executor.runOnMain(() -> {
            if (err != null) {
                sender.sendMessage(bound.i18n().format(sender, DialogTexts.COMMAND_ADMIN_STATS_ERROR,
                        Placeholder.parsed("error", String.valueOf(err.getMessage()))));
                return;
            }
            sender.sendMessage(bound.i18n().format(sender, DialogTexts.COMMAND_ADMIN_STATS_HEADER));
            renderJobCounts(sender, bound, result.jobCounts());
            renderRareStats(sender, bound, result.stats());
        }));
        return Command.SINGLE_SUCCESS;
    }

    private int executeStatsForJob(CommandContext<CommandSourceStack> ctx) {
        JobsServices bound = requireBound(ctx);
        if (bound == null) return Command.SINGLE_SUCCESS;
        CommandSender sender = ctx.getSource().getSender();
        String rawJob = StringArgumentType.getString(ctx, "job");
        JobId jobId;
        try {
            jobId = new JobId(rawJob);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(bound.i18n().format(sender, DialogTexts.COMMAND_ADMIN_STATS_UNKNOWN_JOB,
                    Placeholder.parsed("job", rawJob)));
            return Command.SINGLE_SUCCESS;
        }
        if (bound.jobRegistry().get(jobId).isEmpty()) {
            sender.sendMessage(bound.i18n().format(sender, DialogTexts.COMMAND_ADMIN_STATS_UNKNOWN_JOB,
                    Placeholder.parsed("job", rawJob)));
            return Command.SINGLE_SUCCESS;
        }
        AsyncExecutor executor = bound.asyncExecutor();
        Instant from = LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant to = Instant.now();
        TimeRange range = new TimeRange(from, to);
        CompletableFuture<ActionLogRepository.RareHitStats> future = executor.supplyAsync(
                () -> bound.actionLogRepository().rareHitStats(range, jobId.value()));
        future.whenComplete((stats, err) -> executor.runOnMain(() -> {
            if (err != null) {
                sender.sendMessage(bound.i18n().format(sender, DialogTexts.COMMAND_ADMIN_STATS_ERROR,
                        Placeholder.parsed("error", String.valueOf(err.getMessage()))));
                return;
            }
            sender.sendMessage(bound.i18n().format(sender, DialogTexts.COMMAND_ADMIN_STATS_JOB_HEADER,
                    Placeholder.parsed("job", jobId.value())));
            long count = bound.playerJobRepository().countByJob().getOrDefault(jobId.value(), 0L);
            sender.sendMessage(bound.i18n().format(sender, DialogTexts.COMMAND_ADMIN_STATS_JOB_ROW,
                    Placeholder.parsed("job", jobId.value()),
                    Placeholder.parsed("count", Long.toString(count))));
            renderRareStats(sender, bound, stats);
        }));
        return Command.SINGLE_SUCCESS;
    }

    // --- helpers ---

    private void renderJobCounts(CommandSender sender, JobsServices bound, Map<String, Long> counts) {
        // 登録済み job も 0 人として並べたいので、JobRegistry 側の順序で列挙する。
        for (JobDefinition def : bound.jobRegistry().all()) {
            String id = def.id().value();
            long n = counts.getOrDefault(id, 0L);
            sender.sendMessage(bound.i18n().format(sender, DialogTexts.COMMAND_ADMIN_STATS_JOB_ROW,
                    Placeholder.parsed("job", id),
                    Placeholder.parsed("count", Long.toString(n))));
        }
    }

    private void renderRareStats(CommandSender sender, JobsServices bound, ActionLogRepository.RareHitStats stats) {
        sender.sendMessage(bound.i18n().format(sender, DialogTexts.COMMAND_ADMIN_STATS_ACTIONS_TODAY,
                Placeholder.parsed("count", Long.toString(stats.actions()))));
        sender.sendMessage(bound.i18n().format(sender, DialogTexts.COMMAND_ADMIN_STATS_DAILY_TOTAL,
                Placeholder.parsed("total", bound.amountFormatter().format(stats.totalReward()))));
        double percent = stats.actions() == 0
                ? 0.0
                : (stats.rareHits() * 100.0 / stats.actions());
        sender.sendMessage(bound.i18n().format(sender, DialogTexts.COMMAND_ADMIN_STATS_RARE_HITS,
                Placeholder.parsed("percent", String.format(Locale.ROOT, "%.1f", percent)),
                Placeholder.parsed("rare", Long.toString(stats.rareHits())),
                Placeholder.parsed("total", Long.toString(stats.actions()))));
    }

    private OfflinePlayer resolveOfflinePlayer(String name) {
        // getOfflinePlayerIfCached は cache のみを見る安全 API。存在しなければ null。
        // 一度もサーバに接続したことのないプレイヤー名は解決できないが、それは意図通り。
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(name);
        return target == null || target.getUniqueId() == null ? null : target;
    }

    private int getOptionalInt(CommandContext<CommandSourceStack> ctx, String name, int defaultValue) {
        try {
            return IntegerArgumentType.getInteger(ctx, name);
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    private static String formatInstant(Instant instant) {
        LocalDateTime local = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        return local.format(TIMESTAMP);
    }

    private JobsServices requireBound(CommandContext<CommandSourceStack> ctx) {
        JobsServices bound = services.get();
        if (bound == null) {
            ctx.getSource().getSender().sendMessage(
                    MiniMessages.get().deserialize("<gray>Jobs plugin is loading.</gray>")
            );
            return null;
        }
        return bound;
    }

    private CompletableFuture<Suggestions> suggestPlayerNames(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            String name = op.getName();
            if (name != null && name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestJobIds(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        JobsServices bound = services.get();
        if (bound == null) return builder.buildFuture();
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (JobDefinition def : bound.jobRegistry().all()) {
            String id = def.id().value();
            if (id.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(id);
            }
        }
        return builder.buildFuture();
    }

    private record StatsResult(Map<String, Long> jobCounts, ActionLogRepository.RareHitStats stats) {}
}
