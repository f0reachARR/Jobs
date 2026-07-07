package me.f0reach.jobs.api.specialty;

import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;

/**
 * {@link PlayerJobService} の変更 API の結果。
 *
 * <p>公開 API のため job id は {@link String} で扱う。
 * 内部の {@link me.f0reach.jobs.specialty.SpecialtyChangeResult} を薄くマッピングする。
 *
 * <ul>
 *   <li>{@link Success} 変更成功。{@code initial=true} は初回選択で {@code previousJobId=null}。</li>
 *   <li>{@link CooldownRemaining} プレイヤー起点変更で cooldown 内。</li>
 *   <li>{@link UnknownJob} 指定 job id が JobRegistry に存在しない。</li>
 *   <li>{@link NoChange} 現在専業と同じ、または初回選択済みで select が空振り。</li>
 * </ul>
 */
public sealed interface JobChangeResult {

    record Success(
            @Nullable String previousJobId,
            String newJobId,
            Instant changedAt,
            boolean initial
    ) implements JobChangeResult {}

    record CooldownRemaining(Duration remaining, Instant nextAvailable)
            implements JobChangeResult {}

    record UnknownJob(String requestedJobId) implements JobChangeResult {}

    record NoChange() implements JobChangeResult {}
}
