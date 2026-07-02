package me.f0reach.jobs.specialty;

import me.f0reach.jobs.domain.job.JobId;

import java.time.Duration;
import java.time.Instant;

/**
 * SpecialtyService の select / change の結果。
 *
 * <ul>
 *   <li>{@link Success} 変更成功。initial=true のときは初回選択（previous=null）。</li>
 *   <li>{@link CooldownRemaining} クールダウン中で変更不可。</li>
 *   <li>{@link UnknownJob} 指定 job id が JobRegistry に存在しない。</li>
 *   <li>{@link NoChange} 同じ job を再選択したなど、書き込み不要。</li>
 * </ul>
 */
public sealed interface SpecialtyChangeResult {

    record Success(JobId previous, JobId next, Instant changedAt, boolean initial)
            implements SpecialtyChangeResult {}

    record CooldownRemaining(Duration remaining, Instant nextAvailable)
            implements SpecialtyChangeResult {}

    record UnknownJob(JobId requested) implements SpecialtyChangeResult {}

    record NoChange() implements SpecialtyChangeResult {}
}
