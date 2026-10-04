package com.example.gsb.repair.exec;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 条带化执行器：
 * <ul>
 *   <li>全局并发度由固定大小线程池限制；</li>
 *   <li>同一 stripe key（这里是对象 id）通过公平锁串行执行；</li>
 *   <li>不同 stripe key 的任务可并行，受并发度上限约束。</li>
 * </ul>
 */
public final class StripedExecutor implements AutoCloseable {

    private final int parallelism;
    private final ExecutorService pool;
    private final ConcurrentMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public StripedExecutor(int parallelism) {
        if (parallelism < 1) {
            throw new IllegalArgumentException("parallelism must be >= 1, got " + parallelism);
        }
        this.parallelism = parallelism;
        AtomicInteger index = new AtomicInteger();
        this.pool = Executors.newFixedThreadPool(parallelism, runnable -> {
            Thread thread = new Thread(runnable, "repair-worker-" + index.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    public int parallelism() {
        return parallelism;
    }

    public <T> Future<T> submit(String stripeKey, Callable<T> task) {
        ReentrantLock lock = locks.computeIfAbsent(stripeKey, key -> new ReentrantLock(true));
        return pool.submit(() -> {
            lock.lockInterruptibly();
            try {
                return task.call();
            } finally {
                lock.unlock();
            }
        });
    }

    /** 中止尚未开始与正在执行的任务（用于致命中断信号传播）。 */
    public void shutdownNow() {
        pool.shutdownNow();
    }

    @Override
    public void close() {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
