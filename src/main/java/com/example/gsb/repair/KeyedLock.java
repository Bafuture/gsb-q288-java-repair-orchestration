package com.example.gsb.repair;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 按 key 分组的锁：同一 key 的临界区串行执行，不同 key 互不阻塞。
 * 用于保证"不同对象并行、同一对象串行"。
 */
public final class KeyedLock<K> {

    private final ConcurrentMap<K, ReentrantLock> locks = new ConcurrentHashMap<>();

    public void lock(K key) {
        locks.computeIfAbsent(key, k -> new ReentrantLock()).lock();
    }

    public void unlock(K key) {
        ReentrantLock lock = locks.get(key);
        if (lock == null) {
            throw new IllegalStateException("未持有 key 对应的锁: " + key);
        }
        lock.unlock();
    }
}
