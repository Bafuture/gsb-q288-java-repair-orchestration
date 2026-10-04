package com.example.gsb.repair;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 测试目标对象：expected 为基准值，actual 为当前值，两者不一致即需要修复。
 * mutations 记录实际发生的状态变更次数，用于验证幂等性（无副作用重复变更）。
 */
final class Item {

    final String id;
    final String expected;
    final AtomicReference<String> actual;
    final AtomicInteger mutations = new AtomicInteger();
    volatile boolean locked;

    Item(String id, String expected, String actual) {
        this.id = id;
        this.expected = expected;
        this.actual = new AtomicReference<>(actual);
    }

    static Item inconsistent(String id) {
        return new Item(id, "expected-" + id, "broken-" + id);
    }

    static Item consistent(String id) {
        return new Item(id, "expected-" + id, "expected-" + id);
    }

    boolean isConsistent() {
        return expected.equals(actual.get());
    }

    static DetectStep<Item> detectStep() {
        return item -> item.isConsistent()
                ? DetectResult.ok()
                : DetectResult.problem("值不一致: actual=" + item.actual.get());
    }

    /** 幂等修复步骤：仅在确实不一致时才变更，重复执行无额外副作用。 */
    static RepairStep<Item> idempotentRepair() {
        return (item, ctx) -> {
            if (!item.isConsistent()) {
                item.actual.set(item.expected);
                item.mutations.incrementAndGet();
            }
        };
    }

    static VerifyStep<Item> verifyStep() {
        return (item, ctx) -> item.isConsistent();
    }

    /** 标准前置条件：锁定的对象禁止自动修复。 */
    static Precondition<Item> notLocked() {
        return (item, ctx) -> item.locked
                ? PreconditionResult.rejected("对象已锁定，禁止自动修复")
                : PreconditionResult.ok();
    }
}
