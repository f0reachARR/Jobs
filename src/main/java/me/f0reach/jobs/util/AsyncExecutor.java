package me.f0reach.jobs.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * プラグイン所有の非同期実行プールと Bukkit main thread への戻し口を提供する。
 * threading.md の「プラグイン所有の非同期実行プール」に対応。
 */
public final class AsyncExecutor {
    private final Plugin plugin;
    private final ExecutorService executor;

    public AsyncExecutor(Plugin plugin) {
        this.plugin = plugin;
        this.executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "Jobs-Async");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void runAsync(Runnable task) {
        executor.execute(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                plugin.getLogger().warning("Async task failed: " + t);
            }
        });
    }

    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    public void runOnMain(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
