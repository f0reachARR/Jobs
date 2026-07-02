package me.f0reach.jobs.pipeline.stage;

import me.f0reach.jobs.domain.job.RareBonus;
import me.f0reach.jobs.pipeline.PipelineContext;
import me.f0reach.jobs.pipeline.Stage;
import me.f0reach.jobs.util.MiniMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;

import java.util.random.RandomGenerator;

/**
 * 段階 5。rare.chance でロールし、ヒット時は基礎報酬を rare.reward で置き換え、announce をブロードキャスト。
 *
 * spec/04-reward-pipeline.md 「rare ロール」を参照。
 * rare.reward は amount 倍しない (「基礎報酬を上書きする」意味論に沿う)。
 */
public final class RareRollStage implements Stage {

    private final RandomGenerator random;

    public RareRollStage(RandomGenerator random) {
        this.random = random;
    }

    @Override
    public Result execute(PipelineContext ctx) {
        if (ctx.zeroLocked()) return Result.CONTINUE;
        RareBonus rare = ctx.matchedEntry().rareBonus();
        if (rare == null) return Result.CONTINUE;

        if (random.nextDouble() >= rare.chance()) return Result.CONTINUE;

        double rareReward = rare.rewardAmount().roll(random);
        ctx.setBaseReward(rareReward);
        ctx.setFinalReward(rareReward);
        ctx.setRareHit(true);

        if (rare.announceMessage() != null && !rare.announceMessage().isBlank()) {
            Component msg = MiniMessages.get().deserialize(
                    rare.announceMessage(),
                    Placeholder.parsed("player", ctx.player().getName())
            );
            Bukkit.broadcast(msg);
        }
        return Result.CONTINUE;
    }
}
