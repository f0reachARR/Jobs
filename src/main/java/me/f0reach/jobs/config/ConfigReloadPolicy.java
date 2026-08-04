package me.f0reach.jobs.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * {@code /jobs reload} で config.yml のどこを反映するかを 1 か所に集めた定義。
 *
 * <p>反映するのは「値を読み直すだけで済むキー」に限る。起動時に組んだ構造物
 * （接続プール、ワーカースレッド、キューの容量、キャッシュの持ち方）に直結するキーは
 * 反映せず、旧値を引き継いだうえで {@link #restartRequiredChanges} で列挙する。
 * 中途半端に差し替えると、動いている構造物と設定値が食い違うため。
 *
 * <p>反映しないキー:
 * <ul>
 *   <li>{@code persistence.*} — DataSource / pool / schema を起動時に組む</li>
 *   <li>{@code kvs.*} — store の実装と容量を起動時に決める</li>
 *   <li>{@code daily_cap.scope} — {@code DailyTotalCache} の持ち方が変わる</li>
 *   <li>{@code reward.async.enabled} / {@code queue_capacity} /
 *       {@code backlog_warn_ratio} / {@code economy_on_main} /
 *       {@code main_work_per_tick} — ワーカーとキューと main thread ドレイナの構成</li>
 * </ul>
 */
public final class ConfigReloadPolicy {

    private ConfigReloadPolicy() {}

    /**
     * reload 後に使う config を組む。反映しないキーは {@code before} の値を引き継ぐ。
     *
     * @param before 現在動いている設定
     * @param loaded config.yml を読み直した設定
     */
    public static PluginConfig effective(PluginConfig before, PluginConfig loaded) {
        PluginConfig.AsyncConfig pinnedAsync = new PluginConfig.AsyncConfig(
                before.reward().async().enabled(),
                before.reward().async().queueCapacity(),
                before.reward().async().backlogWarnRatios(),
                before.reward().async().economyOnMain(),
                before.reward().async().mainWorkPerTick(),
                loaded.reward().async().slowExtensionThresholdMs(),
                loaded.reward().async().drainTimeoutMs());
        return new PluginConfig(
                loaded.specialtyMode(),
                new PluginConfig.RewardConfig(
                        loaded.reward().decimals(), loaded.reward().roundingMode(), pinnedAsync),
                new PluginConfig.DailyCapConfig(
                        loaded.dailyCap().amount(),
                        loaded.dailyCap().resetAt(),
                        before.dailyCap().scope()),
                before.persistence(),
                before.kvs(),
                loaded.smelting(),
                loaded.antiAutomation());
    }

    /**
     * config.yml 側で変わったが reload では反映されないキー名。
     * 運用者が「変えたのに効かない」に気づけるよう、reload 時にログへ出す。
     */
    public static List<String> restartRequiredChanges(PluginConfig before, PluginConfig loaded) {
        List<String> changed = new ArrayList<>();
        addIfChanged(changed, "persistence", before.persistence(), loaded.persistence());
        addIfChanged(changed, "kvs", before.kvs(), loaded.kvs());
        addIfChanged(changed, "daily_cap.scope",
                before.dailyCap().scope(), loaded.dailyCap().scope());

        PluginConfig.AsyncConfig a = before.reward().async();
        PluginConfig.AsyncConfig b = loaded.reward().async();
        addIfChanged(changed, "reward.async.enabled", a.enabled(), b.enabled());
        addIfChanged(changed, "reward.async.queue_capacity",
                a.queueCapacity(), b.queueCapacity());
        addIfChanged(changed, "reward.async.backlog_warn_ratio",
                a.backlogWarnRatios(), b.backlogWarnRatios());
        addIfChanged(changed, "reward.async.economy_on_main",
                a.economyOnMain(), b.economyOnMain());
        addIfChanged(changed, "reward.async.main_work_per_tick",
                a.mainWorkPerTick(), b.mainWorkPerTick());
        return List.copyOf(changed);
    }

    private static void addIfChanged(List<String> out, String key, Object before, Object loaded) {
        if (!Objects.equals(before, loaded)) out.add(key);
    }
}
