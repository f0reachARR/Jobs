package me.f0reach.jobs.registry;

import me.f0reach.jobs.domain.job.JobDefinition;
import me.f0reach.jobs.domain.job.JobId;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Job 定義の登録簿。
 * reload では {@link #swap(Map)} で丸ごと差し替える。
 * 進行中のパイプラインは旧 state を保持し続けるため、CAS で入れ替える。
 */
public final class JobRegistry {

    private final AtomicReference<Map<JobId, JobDefinition>> state =
            new AtomicReference<>(Collections.emptyMap());

    public void swap(Map<JobId, JobDefinition> next) {
        Map<JobId, JobDefinition> copy = Map.copyOf(next);
        state.set(copy);
    }

    public void loadAll(Iterable<JobDefinition> jobs) {
        Map<JobId, JobDefinition> map = new LinkedHashMap<>();
        for (JobDefinition job : jobs) {
            map.put(job.id(), job);
        }
        swap(map);
    }

    public Optional<JobDefinition> get(JobId id) {
        return Optional.ofNullable(state.get().get(id));
    }

    public Collection<JobDefinition> all() {
        return state.get().values();
    }

    public int size() {
        return state.get().size();
    }
}
