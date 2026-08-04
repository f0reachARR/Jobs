package me.f0reach.jobs.smelting;

import me.f0reach.jobs.config.PluginConfig;
import me.f0reach.jobs.detection.EventDispatcher;
import me.f0reach.jobs.detection.SourceFlags;
import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.matcher.MatchContext;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 精錬完了ぶんをまとめて 1 件の {@code item_smelted} として dispatch する。
 *
 * <p>ADR-0024 「amount は集約して 1 件にする」を参照。
 * 1 個ごとに dispatch すると 64 個スタックで 64 件になり、イベント件数だけでなく
 * {@code variety_penalty} の窓の意味論も従来（取り出し 1 回で 1 件）から変わる。
 *
 * <p>すべて main thread からのみ触る。credit は {@code FurnaceSmeltEvent} の中、
 * flush は {@code runTaskTimer} の中で走る。
 */
public final class SmeltRewardCollector {

    /** まとめ単位。かまどごとに分けて、従来の「取り出し 1 回で 1 件」に近い粒度を保つ。 */
    private record PendingKey(UUID owner, BlockRef block, NamespacedKey item) {}

    private final Plugin plugin;
    private final EventDispatcher dispatcher;
    private final Supplier<PluginConfig.SmeltingConfig> config;

    private final Map<PendingKey, Integer> pending = new LinkedHashMap<>();
    private BukkitTask flushTask;
    /** 現在スケジュールしている flush 周期。{@link #applyConfig()} の差分判定に使う。 */
    private long scheduledFlushTicks;

    /** 固定の設定で組む。テストなど reload を伴わない用途向け。 */
    public SmeltRewardCollector(
            Plugin plugin, EventDispatcher dispatcher, long flushTicks, int maxPending) {
        this(plugin, dispatcher,
                fixed(new PluginConfig.SmeltingConfig(flushTicks, maxPending)));
    }

    /** config を参照して組む。{@code /jobs reload} 後は {@link #applyConfig()} を呼ぶ。 */
    public SmeltRewardCollector(
            Plugin plugin, EventDispatcher dispatcher,
            Supplier<PluginConfig.SmeltingConfig> config) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.config = config;
    }

    private static Supplier<PluginConfig.SmeltingConfig> fixed(PluginConfig.SmeltingConfig cfg) {
        return () -> cfg;
    }

    /** listener 登録と同じタイミングで呼ぶ。 */
    public void start() {
        if (flushTask != null) return;
        schedule(config.get().flushTicks());
    }

    /**
     * reload 後に呼ぶ。max_pending は毎回読むので、ここでは flush 周期だけ見る。
     * 周期が変わったときはまとめ待ちを出してからタイマを組み直す（周期を跨いで持ち越さない）。
     */
    public void applyConfig() {
        if (flushTask == null) return;
        long ticks = config.get().flushTicks();
        if (ticks == scheduledFlushTicks) return;
        flush();
        flushTask.cancel();
        schedule(ticks);
    }

    private void schedule(long flushTicks) {
        this.scheduledFlushTicks = flushTicks;
        this.flushTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::flush, flushTicks, flushTicks);
    }

    /**
     * onDisable で呼ぶ。報酬ワーカーを drain する前に flush しておく必要がある
     * （docs/plan/threading.md 「停止時 (onDisable)」）。
     */
    public void stop() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        flush();
    }

    /**
     * 精錬 1 回ぶんを積む。
     *
     * @param owner  投入者。オフラインなら flush 時に捨てる。
     * @param block  かまど。
     * @param item   精錬結果のアイテム種別。
     * @param amount 精錬結果の個数。
     */
    public void credit(UUID owner, Block block, NamespacedKey item, int amount) {
        if (owner == null || block == null || item == null || amount <= 0) return;
        pending.merge(new PendingKey(owner, BlockRef.of(block), item), amount, Integer::sum);
        // 溜め込みすぎないよう、上限に達したら tick を待たずに出す。
        if (pending.size() >= config.get().maxPending()) flush();
    }

    /** 溜まっているぶんを dispatch する。オフラインのプレイヤーぶんは捨てる。 */
    public void flush() {
        if (pending.isEmpty()) return;
        List<Map.Entry<PendingKey, Integer>> batch = new ArrayList<>(pending.entrySet());
        pending.clear();
        for (Map.Entry<PendingKey, Integer> entry : batch) {
            PendingKey key = entry.getKey();
            Player player = plugin.getServer().getPlayer(key.owner());
            // オフラインぶんは払わない (ADR-0024)。専業・bypass 権限・ring buffer が
            // online な Player を前提にしている。
            if (player == null) continue;
            MatchContext ctx = MatchContext.builder()
                    .item(key.item())
                    .amount(entry.getValue())
                    .build();
            dispatcher.dispatch(player, ActionType.ITEM_SMELTED, ctx, SourceFlags.none());
        }
    }

    /** テストと診断から使う。まとめ待ちの件数。 */
    public int pendingCount() {
        return pending.size();
    }
}
