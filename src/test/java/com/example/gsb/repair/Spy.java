package com.example.gsb.repair;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** 测试用“被治理系统”与调用计数：模拟外部对象状态及各步骤的执行次数。 */
final class Spy {

    /** 对象 id -> 当前值；检测/修复/回滚都围绕它。 */
    final Map<String, String> values = new ConcurrentHashMap<>();

    final AtomicInteger detectInvocations = new AtomicInteger();
    final AtomicInteger repairInvocations = new AtomicInteger();
    final AtomicInteger repairMutations = new AtomicInteger();
    final AtomicInteger recheckInvocations = new AtomicInteger();
    final AtomicInteger rollbackInvocations = new AtomicInteger();

    /** 当前正在执行修复动作的对象集合，用于并发断言。 */
    final Map<String, AtomicInteger> inFlightPerObject = new ConcurrentHashMap<>();
    final Map<String, AtomicInteger> maxInFlightPerObject = new ConcurrentHashMap<>();
    final AtomicInteger inFlightGlobal = new AtomicInteger();
    final AtomicInteger maxInFlightGlobal = new AtomicInteger();
    final AtomicReference<String> repairThreadPattern = new AtomicReference<>();

    void put(String objectId, String value) {
        values.put(objectId, value);
    }

    boolean isBad(String objectId) {
        return !"good".equals(values.get(objectId));
    }

    /** 幂等修复：只有状态确实不对时才产生副作用。 */
    void guardedRepair(String objectId) {
        repairInvocations.incrementAndGet();
        if (isBad(objectId)) {
            values.put(objectId, "good");
            repairMutations.incrementAndGet();
        }
    }

    void restoreBad(String objectId) {
        values.put(objectId, "bad");
        rollbackInvocations.incrementAndGet();
    }

    void trackRepair(String objectId) throws InterruptedException {
        AtomicInteger perObjectInFlight =
                inFlightPerObject.computeIfAbsent(objectId, key -> new AtomicInteger());
        int current = perObjectInFlight.incrementAndGet();
        maxInFlightPerObject
                .computeIfAbsent(objectId, key -> new AtomicInteger())
                .accumulateAndGet(current, Math::max);
        int global = inFlightGlobal.incrementAndGet();
        maxInFlightGlobal.accumulateAndGet(global, Math::max);
        Thread.sleep(30);
        inFlightGlobal.decrementAndGet();
        perObjectInFlight.decrementAndGet();
    }
}
