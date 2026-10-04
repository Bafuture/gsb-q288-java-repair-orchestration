package com.example.gsb.repair;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 中断恢复：流程中途中断后重启，从已完成步骤继续，不重复成功步骤。 */
class CrashRecoveryTest {

    @TempDir
    Path checkpointDir;

    private static Map<String, AtomicInteger> newCounterMap() {
        return new ConcurrentHashMap<>();
    }

    private static int count(Map<String, AtomicInteger> map, String key) {
        return map.getOrDefault(key, new AtomicInteger()).get();
    }

    @Test
    void resumesFromCheckpointAfterCrashWithoutRepeatingSucceededSteps() {
        // 单线程执行保证处理顺序确定：a, b 完成后，c 在复核阶段"崩溃"
        Map<String, AtomicInteger> detectCalls = newCounterMap();
        Map<String, AtomicInteger> repairCalls = newCounterMap();
        Map<String, AtomicInteger> verifyCalls = newCounterMap();
        AtomicBoolean crashArmed = new AtomicBoolean(true);

        RepairFlow<Item> flow = RepairFlow.builder(Item.class, "recover-flow", item -> item.id)
                .detect(item -> {
                    detectCalls.computeIfAbsent(item.id, k -> new AtomicInteger()).incrementAndGet();
                    return Item.detectStep().detect(item);
                })
                .repair((item, ctx) -> {
                    repairCalls.computeIfAbsent(item.id, k -> new AtomicInteger()).incrementAndGet();
                    Item.idempotentRepair().repair(item, ctx);
                })
                .verify((item, ctx) -> {
                    verifyCalls.computeIfAbsent(item.id, k -> new AtomicInteger()).incrementAndGet();
                    if (item.id.equals("c") && crashArmed.getAndSet(false)) {
                        throw new FatalStepException("模拟进程崩溃");
                    }
                    return Item.verifyStep().verify(item, ctx);
                })
                .rollback((item, ctx) -> { })
                .build();

        List<Item> items = List.of(
                Item.inconsistent("a"), Item.inconsistent("b"), Item.inconsistent("c"),
                Item.inconsistent("d"), Item.inconsistent("e"));

        // 第一次运行：c 复核时崩溃，a/b 已闭环，c 修复成功但复核中断，d/e 未开始
        RepairOrchestrator first = new RepairOrchestrator(new FileCheckpointStore(checkpointDir), 1);
        OrchestrationResult crashed = first.run(flow, items);

        assertThat(crashed.aborted()).isTrue();
        assertThat(crashed.stats().verified()).isEqualTo(2);
        assertThat(crashed.stats().repaired()).isEqualTo(3);
        assertThat(count(repairCalls, "c")).isEqualTo(1);
        assertThat(count(verifyCalls, "c")).isEqualTo(1);

        // 重启（新的编排器实例 + 同一检查点目录）：从断点继续
        RepairOrchestrator second = new RepairOrchestrator(new FileCheckpointStore(checkpointDir), 1);
        OrchestrationResult recovered = second.run(flow, items);

        assertThat(recovered.aborted()).isFalse();
        assertThat(recovered.isSuccessForAll()).isTrue();
        // 本轮恢复运行只处理 c/d/e：c 直接复核，d/e 完整闭环
        assertThat(recovered.stats().verified()).isEqualTo(3);
        assertThat(items).allMatch(Item::isConsistent);

        // a/b 已是终态：所有步骤都不再执行
        assertThat(count(detectCalls, "a")).isEqualTo(1);
        assertThat(count(repairCalls, "a")).isEqualTo(1);
        assertThat(count(verifyCalls, "a")).isEqualTo(1);
        assertThat(count(detectCalls, "b")).isEqualTo(1);

        // c 崩溃前修复已成功：恢复后不重复检测与修复，只重跑复核
        assertThat(count(detectCalls, "c")).isEqualTo(1);
        assertThat(count(repairCalls, "c")).isEqualTo(1);
        assertThat(count(verifyCalls, "c")).isEqualTo(2);
        assertThat(items.get(2).mutations.get()).isEqualTo(1); // 无副作用重复

        // d/e 在恢复后首次完整执行
        assertThat(count(detectCalls, "d")).isEqualTo(1);
        assertThat(count(repairCalls, "e")).isEqualTo(1);

        // 恢复运行只统计本轮增量；跨次汇总可对快照累加
        assertThat(recovered.stats().detectedIssues()).isEqualTo(2);
        assertThat(recovered.stats().repaired()).isEqualTo(2);
        RepairStats overall = crashed.stats().plus(recovered.stats());
        assertThat(overall.verified()).isEqualTo(5);
        assertThat(overall.repaired()).isEqualTo(5);
        assertThat(overall.detectedIssues()).isEqualTo(5);
    }

    @Test
    void terminalTargetsAreSkippedEntirelyOnRestart() {
        CheckpointStore store = new FileCheckpointStore(checkpointDir);
        AtomicInteger detectCalls = new AtomicInteger();
        RepairFlow<Item> flow = RepairFlow.builder(Item.class, "done-flow", item -> item.id)
                .detect(item -> {
                    detectCalls.incrementAndGet();
                    return Item.detectStep().detect(item);
                })
                .repair(Item.idempotentRepair())
                .verify(Item.verifyStep())
                .verifyFailurePolicy(VerifyFailurePolicy.MARK_FOR_MANUAL)
                .build();

        Item item = Item.inconsistent("done");
        new RepairOrchestrator(store, 1).run(flow, List.of(item));
        assertThat(detectCalls.get()).isEqualTo(1);

        // 模拟重启：新编排器 + 同一存储，已闭环目标直接跳过
        OrchestrationResult rerun = new RepairOrchestrator(store, 1).run(flow, List.of(item));
        assertThat(detectCalls.get()).isEqualTo(1);
        assertThat(rerun.stats().verified()).isZero();
        assertThat(rerun.targetResults()).isEmpty();
    }
}
