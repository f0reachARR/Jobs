package me.f0reach.jobs.modifier.variety;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * (playerUuid, jobId) ごとに直近 {@code capacity} 件のアクションキーを保持する ring buffer。
 * スレッドセーフではない前提で、パイプラインの main thread からのみ操作する。
 *
 * <p>docs/plan/class-structure.md 「modifier.variety」の VarietyRingBuffer を参照。
 * 具体的な用途は spec/04-reward-pipeline.md 「variety_penalty」段階。
 */
public final class VarietyRingBuffer {

    private final int capacity;
    private final Deque<String> keys;
    private final Map<String, Integer> counts = new HashMap<>();

    public VarietyRingBuffer(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.capacity = capacity;
        this.keys = new ArrayDeque<>(capacity);
    }

    /** 初期化用。呼び出し前に record された履歴は破棄される。newestFirst の順で受け取る。 */
    public void initFromRecent(List<String> newestFirst) {
        clear();
        // ring buffer の意味的には「古い → 新しい」の順で入れる。
        // recentKeys は新しい順で返ってくるので、逆順に record する。
        List<String> reversed = new ArrayList<>(newestFirst);
        java.util.Collections.reverse(reversed);
        int start = Math.max(0, reversed.size() - capacity);
        for (int i = start; i < reversed.size(); i++) {
            record(reversed.get(i));
        }
    }

    /** 新しいアクションキーを 1 件追加。容量超過時は最古を捨てる。 */
    public void record(String key) {
        Objects.requireNonNull(key, "key");
        if (keys.size() == capacity) {
            String oldest = keys.pollFirst();
            decrement(oldest);
        }
        keys.addLast(key);
        counts.merge(key, 1, Integer::sum);
    }

    /** 現時点で buffer に入っている件数。 */
    public int size() {
        return keys.size();
    }

    public int capacity() {
        return capacity;
    }

    public boolean isEmpty() {
        return keys.isEmpty();
    }

    /** 最多キーの占有比率。buffer が空なら 0.0 を返す。 */
    public double topRatio() {
        if (keys.isEmpty()) return 0.0;
        int max = 0;
        for (int c : counts.values()) if (c > max) max = c;
        return (double) max / (double) keys.size();
    }

    /** 最多キー。buffer が空なら null。 */
    public String topKey() {
        if (keys.isEmpty()) return null;
        String top = null;
        int max = -1;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() > max) {
                max = e.getValue();
                top = e.getKey();
            }
        }
        return top;
    }

    public void clear() {
        keys.clear();
        counts.clear();
    }

    private void decrement(String key) {
        Integer c = counts.get(key);
        if (c == null) return;
        if (c <= 1) counts.remove(key);
        else counts.put(key, c - 1);
    }
}
