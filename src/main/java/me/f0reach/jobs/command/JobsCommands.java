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
import me.f0reach.jobs.util.MiniMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

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
 *   <li>/jobs select は選択済み UUID に対して {@link DialogTexts#COMMAND_SELECT_ALREADY} を返してダイアログを開かない。</li>
 *   <li>/jobs change は未選択 UUID に対して {@link DialogTexts#COMMAND_CHANGE_NO_SELECTION} を返してダイアログを開かない。
 *       cooldown 判定と現在職業のプレビューは {@code SpecialtyChangeDialog} 側の分岐に委ねる。</li>
 *   <li>/jobs status は未選択 UUID に対して {@link DialogTexts#COMMAND_STATUS_NO_SPECIALTY} を返してダイアログを開かない。</li>
 * </ul>
 */
public final class JobsCommands {

    private final AtomicReference<JobsServices> services = new AtomicReference<>();

    public LiteralCommandNode<CommandSourceStack> buildTree() {
        return Commands.literal("jobs")
                .then(Commands.literal("select").executes(this::executeSelect))
                .then(Commands.literal("change").executes(this::executeChange))
                .then(Commands.literal("status").executes(this::executeStatus))
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

        if (bound.specialtyService().currentJob(player.getUniqueId()).isPresent()) {
            player.sendMessage(bound.i18n().format(player, DialogTexts.COMMAND_SELECT_ALREADY));
            return Command.SINGLE_SUCCESS;
        }
        bound.specialtySelectDialog().open(player);
        return Command.SINGLE_SUCCESS;
    }

    private int executeChange(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        JobsServices bound = requireBound(ctx);
        if (bound == null) return Command.SINGLE_SUCCESS;
        Player player = requirePlayer(ctx, bound);
        if (player == null) return Command.SINGLE_SUCCESS;

        if (bound.specialtyService().currentJob(player.getUniqueId()).isEmpty()) {
            player.sendMessage(bound.i18n().format(player, DialogTexts.COMMAND_CHANGE_NO_SELECTION));
            return Command.SINGLE_SUCCESS;
        }
        bound.specialtyChangeDialog().open(player);
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
        return Command.SINGLE_SUCCESS;
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
                Placeholder.parsed("total", formatAmount(total)),
                Placeholder.parsed("cap", formatAmount(cfg.amount()))
        ));
        if (total >= cfg.amount()) {
            player.sendMessage(bound.i18n().format(player, DialogTexts.COMMAND_STATUS_DAILY_CAP_HIT));
        }
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

    private static String formatAmount(double amount) {
        if (amount == Math.floor(amount) && !Double.isInfinite(amount)) {
            return Long.toString((long) amount);
        }
        return String.format(Locale.ROOT, "%.2f", amount);
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
