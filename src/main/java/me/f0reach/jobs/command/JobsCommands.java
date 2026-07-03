package me.f0reach.jobs.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.f0reach.jobs.JobsServices;
import me.f0reach.jobs.config.PluginConfig;
import me.f0reach.jobs.domain.job.JobDefinition;
import me.f0reach.jobs.domain.job.JobId;
import me.f0reach.jobs.domain.job.VarietyPenaltyConfig;
import me.f0reach.jobs.modifier.variety.VarietyPenaltyEvaluator;
import me.f0reach.jobs.ui.DialogTexts;
import me.f0reach.jobs.ui.JobConditionsDialog;
import me.f0reach.jobs.ui.SpecialtyListDialog;
import me.f0reach.jobs.util.MiniMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * /jobs コマンドのツリー構築と実行 handler。
 *
 * Bootstrap 時にツリーだけ構築し、実行 handler は起動後に JobsPlugin が
 * {@link #bindServices(JobsServices)} で差し込む形にする。
 *
 * 事前チェックの方針。
 * <ul>
 *   <li>/jobs select は現在の専業状態で分岐する。未選択なら {@link SpecialtyListDialog} SELECT、
 *       選択済み & cooldown 中なら {@code SpecialtyCooldownDialog}、選択済み & 変更可なら
 *       {@link SpecialtyListDialog} CHANGE を開く。</li>
 *   <li>/jobs info は選択・cooldown 状態に関係なく閲覧を許す。引数なしなら一覧、
 *       引数ありなら該当ジョブの詳細を READ_ONLY で開く。</li>
 *   <li>/jobs status は未選択 UUID に対して {@link DialogTexts#COMMAND_STATUS_NO_SPECIALTY} を返してダイアログを開かない。</li>
 * </ul>
 */
public final class JobsCommands {

    private final AtomicReference<JobsServices> services = new AtomicReference<>();

    public LiteralCommandNode<CommandSourceStack> buildTree() {
        return Commands.literal("jobs")
                .then(Commands.literal("select").executes(this::executeSelect))
                .then(Commands.literal("info")
                        .executes(this::executeInfo)
                        .then(Commands.argument("job", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(this::executeInfoWithJob)))
                .then(Commands.literal("status").executes(this::executeStatus))
                .then(Commands.literal("reload").executes(this::executeReload))
                .build();
    }

    public void bindServices(JobsServices services) {
        this.services.set(services);
    }

    public void unbindServices() {
        this.services.set(null);
    }

    private int executeSelect(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        JobsServices bound = requireBound(ctx);
        if (bound == null) return Command.SINGLE_SUCCESS;
        Player player = requirePlayer(ctx, bound);
        if (player == null) return Command.SINGLE_SUCCESS;

        JobId current = bound.specialtyService().currentJob(player.getUniqueId()).orElse(null);
        if (current == null) {
            bound.specialtyListDialog().open(player, SpecialtyListDialog.Mode.SELECT);
            return Command.SINGLE_SUCCESS;
        }

        Instant next = bound.specialtyService().nextAvailableAt(player.getUniqueId()).orElse(Instant.EPOCH);
        if (next.isAfter(Instant.now())) {
            bound.specialtyCooldownDialog().open(player, current, next);
        } else {
            bound.specialtyListDialog().open(player, SpecialtyListDialog.Mode.CHANGE);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int executeInfo(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        JobsServices bound = requireBound(ctx);
        if (bound == null) return Command.SINGLE_SUCCESS;
        Player player = requirePlayer(ctx, bound);
        if (player == null) return Command.SINGLE_SUCCESS;

        bound.specialtyListDialog().open(player, SpecialtyListDialog.Mode.INFO);
        return Command.SINGLE_SUCCESS;
    }

    private int executeInfoWithJob(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        JobsServices bound = requireBound(ctx);
        if (bound == null) return Command.SINGLE_SUCCESS;
        Player player = requirePlayer(ctx, bound);
        if (player == null) return Command.SINGLE_SUCCESS;

        String raw = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "job");
        JobId target;
        try {
            target = new JobId(raw);
        } catch (IllegalArgumentException e) {
            player.sendMessage(bound.i18n().format(player, DialogTexts.COMMAND_INFO_UNKNOWN_JOB,
                    Placeholder.parsed("job", raw)));
            return Command.SINGLE_SUCCESS;
        }
        if (bound.jobRegistry().get(target).isEmpty()) {
            player.sendMessage(bound.i18n().format(player, DialogTexts.COMMAND_INFO_UNKNOWN_JOB,
                    Placeholder.parsed("job", raw)));
            return Command.SINGLE_SUCCESS;
        }
        // 引数指定で開いた場合は「一覧に戻る」は出さず、閉じるボタンだけ。
        bound.jobConditionsDialog().open(player, target, JobConditionsDialog.Mode.READ_ONLY, null);
        return Command.SINGLE_SUCCESS;
    }

    private int executeStatus(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        JobsServices bound = requireBound(ctx);
        if (bound == null) return Command.SINGLE_SUCCESS;
        Player player = requirePlayer(ctx, bound);
        if (player == null) return Command.SINGLE_SUCCESS;

        JobId current = bound.specialtyService().currentJob(player.getUniqueId()).orElse(null);
        if (current == null) {
            player.sendMessage(bound.i18n().format(player, DialogTexts.COMMAND_STATUS_NO_SPECIALTY));
            return Command.SINGLE_SUCCESS;
        }
        player.sendMessage(bound.i18n().format(
                player, DialogTexts.COMMAND_STATUS_CURRENT,
                Placeholder.parsed("current_job", current.value())
        ));

        sendDailyStatus(player, bound);
        sendVarietyStatus(player, bound, current);
        sendNextChangeTime(player, bound);
        return Command.SINGLE_SUCCESS;
    }

    private int executeReload(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        JobsServices bound = requireBound(ctx);
        if (bound == null) return Command.SINGLE_SUCCESS;
        CommandSender sender = ctx.getSource().getSender();
        if (!sender.hasPermission("jobs.admin")) {
            sender.sendMessage(bound.i18n().format(sender, DialogTexts.COMMAND_RELOAD_NO_PERMISSION));
            return Command.SINGLE_SUCCESS;
        }
        try {
            bound.reload();
            int jobs = bound.jobRegistry().all().size();
            int rewards = bound.jobRegistry().all().stream().mapToInt(j -> j.rewards().size()).sum();
            sender.sendMessage(bound.i18n().format(sender, DialogTexts.COMMAND_RELOAD_OK,
                    Placeholder.parsed("jobs", Integer.toString(jobs)),
                    Placeholder.parsed("rewards", Integer.toString(rewards))
            ));
        } catch (RuntimeException e) {
            sender.sendMessage(bound.i18n().format(sender, DialogTexts.COMMAND_RELOAD_FAILED,
                    Placeholder.parsed("error", String.valueOf(e.getMessage()))
            ));
        }
        return Command.SINGLE_SUCCESS;
    }

    private void sendNextChangeTime(Player player, JobsServices bound) {
        bound.specialtyService().nextAvailableAt(player.getUniqueId()).ifPresent(next -> {
            if (!next.isAfter(Instant.now())) return; // クールダウンは既に切れている
            LocalDateTime local = LocalDateTime.ofInstant(next, ZoneId.systemDefault());
            String formatted = local.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            player.sendMessage(bound.i18n().format(
                    player, DialogTexts.COMMAND_STATUS_NEXT_CHANGE,
                    Placeholder.parsed("next_at", formatted)
            ));
        });
    }

    private void sendDailyStatus(Player player, JobsServices bound) {
        PluginConfig.DailyCapConfig cfg = bound.config().dailyCap();
        if (cfg.amount() <= 0) return;
        double total = switch (cfg.scope()) {
            case TOTAL -> bound.dailyTotalCache().todayTotal(player.getUniqueId());
            case PER_JOB -> bound.dailyTotalCache().todayForJob(
                    player.getUniqueId(),
                    bound.specialtyService().currentJob(player.getUniqueId())
                            .map(JobId::value).orElse(""));
        };
        player.sendMessage(bound.i18n().format(
                player, DialogTexts.COMMAND_STATUS_DAILY_TOTAL,
                Placeholder.parsed("total", bound.amountFormatter().format(total)),
                Placeholder.parsed("cap", bound.amountFormatter().format(cfg.amount()))
        ));

        int percent = Math.min(100, (int) Math.round(total * 100.0 / cfg.amount()));
        String bar = buildBar(percent, 20);
        player.sendMessage(bound.i18n().format(
                player, DialogTexts.COMMAND_STATUS_DAILY_BAR,
                Placeholder.parsed("bar", bar),
                Placeholder.parsed("percent", Integer.toString(percent))
        ));
        if (total >= cfg.amount()) {
            player.sendMessage(bound.i18n().format(player, DialogTexts.COMMAND_STATUS_DAILY_CAP_HIT));
        }
    }

    /** width 個のブロックで progress bar を組む。埋まった分は "■"、空は "□"。 */
    private static String buildBar(int percent, int width) {
        int filled = Math.max(0, Math.min(width, percent * width / 100));
        StringBuilder sb = new StringBuilder(width);
        for (int i = 0; i < filled; i++) sb.append('■');
        for (int i = filled; i < width; i++) sb.append('□');
        return sb.toString();
    }

    private void sendVarietyStatus(Player player, JobsServices bound, JobId currentJob) {
        JobDefinition def = bound.jobRegistry().get(currentJob).orElse(null);
        if (def == null) return;
        VarietyPenaltyConfig config = def.varietyPenalty();
        if (config == null || !config.enabled()) {
            player.sendMessage(bound.i18n().format(player, DialogTexts.COMMAND_STATUS_VARIETY_NONE));
            return;
        }

        VarietyPenaltyEvaluator.Snapshot snap = bound.varietyPenaltyEvaluator()
                .snapshot(player.getUniqueId(), currentJob);
        double ratio = snap == null ? 0.0 : snap.topRatio();
        int size = snap == null ? 0 : snap.size();
        int capacity = snap == null ? config.window() : snap.capacity();

        boolean penalized = ratio > 0.0 && lookupMultiplier(config, ratio) < 1.0;
        if (penalized) {
            if (!config.hideNumbers()) {
                player.sendMessage(bound.i18n().format(
                        player, DialogTexts.COMMAND_STATUS_VARIETY_ACTIVE,
                        Placeholder.parsed("size", Integer.toString(size)),
                        Placeholder.parsed("capacity", Integer.toString(capacity)),
                        Placeholder.parsed("ratio", String.format(Locale.ROOT, "%.0f", ratio * 100.0))
                ));
            }
            if (config.disclosedMessage() != null && !config.disclosedMessage().isBlank()) {
                player.sendMessage(bound.i18n().format(
                        player, DialogTexts.COMMAND_STATUS_VARIETY_DISCLOSED,
                        Placeholder.parsed("disclosed", config.disclosedMessage())
                ));
            }
        } else {
            player.sendMessage(bound.i18n().format(player, DialogTexts.COMMAND_STATUS_VARIETY_NONE));
        }
    }

    private static double lookupMultiplier(VarietyPenaltyConfig config, double ratio) {
        double mult = 1.0;
        double bestUpTo = Double.POSITIVE_INFINITY;
        for (VarietyPenaltyConfig.CurvePoint point : config.curve()) {
            if (ratio <= point.upTo() && point.upTo() < bestUpTo) {
                mult = point.multiplier();
                bestUpTo = point.upTo();
            }
        }
        return mult;
    }

    private JobsServices requireBound(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        JobsServices bound = services.get();
        if (bound == null) {
            // ここは i18n が使えない（LocaleRegistry も未初期化）ため英語固定で返す。
            ctx.getSource().getSender().sendMessage(
                    MiniMessages.get().deserialize("<gray>Jobs plugin is loading.</gray>")
            );
            return null;
        }
        return bound;
    }

    private Player requirePlayer(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
            JobsServices bound
    ) {
        var executor = ctx.getSource().getExecutor();
        if (executor instanceof Player player) return player;
        Component msg = bound.i18n().format(ctx.getSource().getSender(), DialogTexts.COMMAND_PLAYER_ONLY);
        ctx.getSource().getSender().sendMessage(msg);
        return null;
    }
}
