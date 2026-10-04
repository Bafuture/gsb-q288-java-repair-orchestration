package com.example.gsb.repair;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** 并发控制：不同对象并行、同一对象串行、并发度受限。 */
class ConcurrencyControlTest {

    @Test
    void differentTargetsRunInParallelWithinMaxConcurrency() {
        int maxConcurrency = 4;
        int targetCount = 40;
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxObserved = new AtomicInteger();

        RepairFlow<Item> flow = RepairFlow.builder(Item.class, "parallel-flow", item -> item.id)
                .detect(Item.detectStep())
                .repair((item, ctx) -> {
                    int now = inFlight.incrementAndGet();
                    maxObserved.accumulateAndGet(now, Math::max);
                    try {
                        Thread.sleep(20);
                    } finally {
                        inFlight.decrementAndGet();
                    }
                    Item.idempotentRepair().repair(item, ctx);
                })
                .verify(Item.verifyStep())
                .verifyFailurePolicy(VerifyFailurePolicy.MARK_FOR_MANUAL)
                .build();

        List<Item> items = new ArrayList<>();
        for (int i = 0; i < targetCount; i++) {
            items.add(Item.inconsistent("item-" + i));
        }

        OrchestrationResult result = new RepairOrchestrator(new InMemoryCheckpointStore(), maxConcurrency)
                .run(flow, items);

        assertThat(result.stats().repaired()).isEqualTo(targetCount);
        assertThat(result.stats().verified()).isEqualTo(targetCount);
        assertThat(items).allMatch(Item::isConsistent);
        // 并发度被限制在 maxConcurrency 以内，且确实发生了并行
        assertThat(maxObserved.get()).isLessThanOrEqualTo(maxConcurrency);
        assertThat(maxObserved.get()).isGreaterThan(1);
    }

    @Test
    void sameKeyIsSerializedAcrossThreads() throws Exception {
        KeyedLock<String> lock = new KeyedLock<>();
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger maxOverlap = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(2);

        Runnable critical = () -> {
            lock.lock("same-key");
            try {
                int now = inside.incrementAndGet();
                maxOverlap.accumulateAndGet(now, Math::max);
                Thread.sleep(50);
                inside.decrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock("same-key");
                done.countDown();
            }
        };

        new Thread(critical).start();
        new Thread(critical).start();
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(maxOverlap.get()).isEqualTo(1); // 同一 key 从不重叠
    }

    @Test
    void differentKeysDoNotBlockEachOther() throws Exception {
        KeyedLock<String> lock = new KeyedLock<>();
        CountDownLatch aInside = new CountDownLatch(1);
        CountDownLatch bEntered = new CountDownLatch(1);

        Thread threadA = new Thread(() -> {
            lock.lock("key-a");
            try {
                aInside.countDown();
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock("key-a");
            }
        });
        Thread threadB = new Thread(() -> {
            lock.lock("key-b");
            try {
                bEntered.countDown();
            } finally {
                lock.unlock("key-b");
            }
        });

        threadA.start();
        assertThat(aInside.await(5, TimeUnit.SECONDS)).isTrue();
        threadB.start();
        // A 仍持有 key-a 的锁时，B 能立即进入 key-b 的临界区
        assertThat(bEntered.await(2, TimeUnit.SECONDS)).isTrue();
        threadA.join(5000);
        threadB.join(5000);
    }
}
