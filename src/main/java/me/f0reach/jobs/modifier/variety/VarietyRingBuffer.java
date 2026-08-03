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
 *
 * <p>docs/plan/class-structure.md 「modifier.variety」の VarietyRingBuffer を参照。
 * 具体的な用途は spec/04-reward-pipeline.md 「variety_penalty」段階。
 *
 * <p>書き込みは {@code RewardDispatcher} が保証する単一スレッドから、
 * 読み出しは {@code /jobs status} と Dialog UI から main thread で起きる。
 * 内部の {@link HashMap} は「単一書き手 + 別スレッドからの読み」でも安全でない
 * （resize の途中を読むと null や無限ループになる）ため、全メソッドを synchronized にする。
 * 臨界区間は異なるキー数だけのループで、UI からの読み出しも稀なので競合はほぼ起きない。
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
    public synchronized void initFromRecent(List<String> newestFirst) {
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
    public synchronized void record(String key) {
        Objects.requireNonNull(key, "key");
        if (keys.size() == capacity) {
            String oldest = keys.pollFirst();
            decrement(oldest);
        }
        keys.addLast(key);
        counts.merge(key, 1, Integer::sum);
    }

    /** 現時点で buffer に入っている件数。 */
    public synchronized int size() {
        return keys.size();
    }

    public int capacity() {
        return capacity;
    }

    public synchronized boolean isEmpty() {
        return keys.isEmpty();
    }

    /** 最多キーの占有比率。buffer が空なら 0.0 を返す。 */
    public synchronized double topRatio() {
        if (keys.isEmpty()) return 0.0;
        int max = 0;
        for (int c : counts.values()) if (c > max) max = c;
        return (double) max / (double) keys.size();
    }

    /** 最多キー。buffer が空なら null。 */
    public synchronized String topKey() {
        if (keys.isEmpty()) return null;
        return topKeyLocked();
    }

    /**
     * 件数と最多キーをまとめて 1 回のロックで返す。
     * 個別 getter を並べて呼ぶと、その合間に record が挟まって値がずれる。
     */
    public synchronized Snapshot snapshot() {
        if (keys.isEmpty()) {
            return new Snapshot(0, capacity, 0.0, null);
        }
        int max = 0;
        for (int c : counts.values()) if (c > max) max = c;
        return new Snapshot(
                keys.size(), capacity, (double) max / (double) keys.size(), topKeyLocked());
    }

    public synchronized void clear() {
        keys.clear();
        counts.clear();
    }

    private String topKeyLocked() {
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

    private void decrement(String key) {
        Integer c = counts.get(key);
        if (c == null) return;
        if (c <= 1) counts.remove(key);
        else counts.put(key, c - 1);
    }

    /** 整合した観測値の組。 */
    public record Snapshot(int size, int capacity, double topRatio, String topKey) {}
}
