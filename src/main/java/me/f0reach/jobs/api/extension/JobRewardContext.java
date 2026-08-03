package me.f0reach.jobs.api.extension;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * {@link JobRewardModifier} の入力。
 * spec/06-public-api.md 「JobRewardModifier」を参照。
 *
 * <p><b>スレッド契約</b>：このオブジェクトを受け取るコールバックは Jobs 専用の
 * ワーカースレッドから呼ばれる。main thread ではない。
 * Bukkit API を叩く必要がある場合は、実装側で main thread へ戻すこと
 * （{@code Bukkit.getScheduler().runTask(...)} など）。
 */
public interface JobRewardContext {

    /**
     * 対象プレイヤー。
     *
     * <p>ワーカースレッドから触れてよいのは、この参照の同一性と
     * {@link Player#getUniqueId()} や {@link Player#isOnline()} のような
     * 状態を持たない読み出しに限る。位置、インベントリ、装備といった
     * 可変状態の読み書きは main thread へ戻してから行うこと。
     *
     * <p>報酬処理がキューに滞在するあいだにログアウトしている場合があるので、
     * オンライン前提の処理では {@link Player#isOnline()} を確認する。
     * UUID と名前だけで足りるなら {@link #getPlayerUuid()} と
     * {@link #getPlayerName()} を使う。
     */
    Player getPlayer();

    /** 対象プレイヤーの UUID。ワーカースレッドから安全に読める。 */
    UUID getPlayerUuid();

    /** アクション検知時点でのプレイヤー名。ワーカースレッドから安全に読める。 */
    String getPlayerName();

    String getJobId();

    String getActionKey();

    /** ここまでの段階で確定している報酬 (未丸め)。 */
    double getCurrentReward();

    boolean isRareHit();
}
