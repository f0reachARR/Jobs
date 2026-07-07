package me.f0reach.jobs.api.specialty;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * プレイヤーの現在専業の取得・変更を扱う公開 API。
 * spec/06-public-api.md 「PlayerJobService」を参照。
 *
 * <p>読み取りはキャッシュ経由の同期取得 ({@link #getCurrentJobId}) と、
 * オフライン含む DB 直取得 ({@link #fetchCurrentJobId}) を提供する。
 *
 * <p>変更は 2 経路：
 * <ul>
 *   <li>{@link #changeAsPlayer} プレイヤー起点。cooldown と {@code jobs.bypass.cooldown} を尊重し、
 *       {@link me.f0reach.jobs.api.event.JobSpecialtyChangedEvent} を発火する。
 *       Bukkit main thread 前提。</li>
 *   <li>{@link #setBySystem} 外部プラグイン起点（クエスト報酬・イベント配布など）。
 *       cooldown 判定なし、オフラインプレイヤーも可。監査ログには {@code actor='system'} で残る。</li>
 * </ul>
 *
 * <p>job id は公開 API の面を薄くするため String で扱う。
 * ジョブ id の妥当性 validate は Jobs プラグイン内で行う。
 */
public interface PlayerJobService {

    /**
     * キャッシュから現在の職業 id を返す。オンライン中のプレイヤーだけがキャッシュに乗るため、
     * オフラインでは常に {@link Optional#empty()} になる。任意 thread から呼べる。
     * オフライン含めて取得したい場合は {@link #fetchCurrentJobId} を使う。
     */
    Optional<String> getCurrentJobId(@NotNull UUID player);

    /**
     * player_job テーブルから現在の職業 id を非同期に取得する。
     * オフラインプレイヤーでも解決できる。DB I/O が入るため main thread からも呼べるが、
     * 結果は completion thread で処理されるので Bukkit API を触るなら {@link org.bukkit.Bukkit#getScheduler()} 経由で戻す。
     */
    CompletableFuture<Optional<String>> fetchCurrentJobId(@NotNull UUID player);

    /**
     * 次回変更可能時刻をキャッシュから返す。オンラインプレイヤーで cooldown 起点が既知の場合のみ。
     * オフラインまたは cooldown 起点が未取得なら empty。
     */
    Optional<Instant> nextChangeAvailableAt(@NotNull UUID player);

    /**
     * プレイヤー起点の変更。
     * <ul>
     *   <li>ジョブ未選択なら初回選択として扱う（{@link JobChangeResult.Success#initial()} が true）。</li>
     *   <li>cooldown 内なら {@link JobChangeResult.CooldownRemaining}。
     *       {@code jobs.bypass.cooldown} permission 保有者は cooldown を無視する。</li>
     *   <li>現在と同じ job なら {@link JobChangeResult.NoChange}。</li>
     * </ul>
     *
     * <p>Bukkit main thread から呼ぶこと。オフラインプレイヤーには使えない
     * ({@link #setBySystem} を使う)。
     */
    @NotNull JobChangeResult changeAsPlayer(@NotNull Player player, @NotNull String jobId);

    /**
     * 外部プラグイン起点の強制変更。
     * <ul>
     *   <li>cooldown 判定を行わない。</li>
     *   <li>オフラインプレイヤーでも DB 更新される。オンラインなら
     *       {@link me.f0reach.jobs.api.event.JobSpecialtyChangedEvent} も main thread で発火する。</li>
     *   <li>{@code player_job_history} に {@code actor='system'} で 1 行 append される。
     *       {@code actorTag} は監査ログの識別用途で、呼び出し元プラグイン名など人間可読な短い ID を推奨する。</li>
     * </ul>
     *
     * <p>DB I/O を含むため非同期で処理される。any thread から呼べる。
     *
     * @param player  対象プレイヤーの UUID
     * @param jobId   付与する job id
     * @param actorTag 呼び出し元識別子（監査ログ用、null 不可）
     */
    @NotNull CompletableFuture<JobChangeResult> setBySystem(
            @NotNull UUID player, @NotNull String jobId, @NotNull String actorTag);
}
