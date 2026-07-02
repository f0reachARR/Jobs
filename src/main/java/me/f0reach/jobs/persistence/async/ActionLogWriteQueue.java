package me.f0reach.jobs.persistence.async;

import me.f0reach.jobs.persistence.dto.ActionLogRow;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * action_log の非同期書き込みキュー。main thread からは enqueue のみ、
 * BatchFlushWorker が drain して batch INSERT する。
 *
 * <p>キュー満杯時は enqueue が失敗を返し、呼び出し側 (ActionLogStage) は
 * ログ落としを WARNING で記録する（threading.md 参照）。
 */
public final class ActionLogWriteQueue {

    private final BlockingQueue<ActionLogRow> queue;

    public ActionLogWriteQueue(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    /**
     * キュー満杯なら false を返す（main thread は待たない）。
     */
    public boolean offer(ActionLogRow row) {
        return queue.offer(row);
    }

    /**
     * 最大 {@code max} 件、または {@code timeout} 経過まで待ち、蓄まった分を返す。
     * timeout 中に 1 件も来なければ空リストを返す。
     */
    public List<ActionLogRow> drain(int max, long timeout, TimeUnit unit) throws InterruptedException {
        List<ActionLogRow> out = new ArrayList<>();
        // 最初の 1 件は blocking で待つ。
        ActionLogRow first = queue.poll(timeout, unit);
        if (first == null) return out;
        out.add(first);
        queue.drainTo(out, max - 1);
        return out;
    }

    /** shutdown 時に残りを取り出すためのヘルパ。 */
    public List<ActionLogRow> drainAll() {
        List<ActionLogRow> out = new ArrayList<>();
        queue.drainTo(out);
        return out;
    }

    public int size() { return queue.size(); }
    public int remainingCapacity() { return queue.remainingCapacity(); }
}
