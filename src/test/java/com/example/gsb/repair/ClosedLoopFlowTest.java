package com.example.gsb.repair;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.Test;

/** 闭环流程：检测→决策→修复→复核 全链路。 */
class ClosedLoopFlowTest {

    @Test
    void inconsistentItemsAreDetectedRepairedAndVerified() {
        Queue<String> events = new ConcurrentLinkedQueue<>();
        RepairFlow<Item> flow = RepairFlow.builder(Item.class, "config-sync", item -> item.id)
                .detect(item -> {
                    events.add(item.id + ":detect");
                    return Item.detectStep().detect(item);
                })
                .precondition(Item.notLocked())
                .repair((item, ctx) -> {
                    events.add(item.id + ":repair");
                    Item.idempotentRepair().repair(item, ctx);
                })
                .verify((item, ctx) -> {
                    events.add(item.id + ":verify");
                    return Item.verifyStep().verify(item, ctx);
                })
                .rollback((item, ctx) -> events.add(item.id + ":rollback"))
                .build();

        Item broken1 = Item.inconsistent("a");
        Item broken2 = Item.inconsistent("b");
        Item broken3 = Item.inconsistent("c");
        Item healthy = Item.consistent("d");

        RepairOrchestrator orchestrator = new RepairOrchestrator(new InMemoryCheckpointStore(), 4);
        OrchestrationResult result = orchestrator.run(flow, List.of(broken1, broken2, broken3, healthy));

        assertThat(result.aborted()).isFalse();
        assertThat(result.isSuccessForAll()).isTrue();
        assertThat(result.targetResults()).hasSize(4);
        assertThat(result.byState(TargetState.VERIFIED)).hasSize(4);

        RepairStats stats = result.stats();
        assertThat(stats.detectedIssues()).isEqualTo(3);
        assertThat(stats.repaired()).isEqualTo(3);
        assertThat(stats.verified()).isEqualTo(4);
        assertThat(stats.rolledBack()).isZero();
        assertThat(stats.manualRequired()).isZero();
        assertThat(stats.skipped()).isZero();
        assertThat(stats.failed()).isZero();

        assertThat(broken1.isConsistent()).isTrue();
        assertThat(broken2.isConsistent()).isTrue();
        assertThat(broken3.isConsistent()).isTrue();
        assertThat(broken1.mutations.get()).isEqualTo(1);

        assertThat(events).containsSubsequence("a:detect", "a:repair", "a:verify");
        // 一致的对象只检测，不修复也不复核
        assertThat(events).contains("d:detect").doesNotContain("d:repair", "d:verify");
    }

    @Test
    void consistentItemsNeedNoRepair() {
        RepairFlow<Item> flow = RepairFlow.builder(Item.class, "noop-flow", item -> item.id)
                .detect(Item.detectStep())
                .repair(Item.idempotentRepair())
                .verify(Item.verifyStep())
                .verifyFailurePolicy(VerifyFailurePolicy.MARK_FOR_MANUAL)
                .build();

        RepairOrchestrator orchestrator = new RepairOrchestrator(new InMemoryCheckpointStore(), 2);
        OrchestrationResult result = orchestrator.run(flow, List.of(Item.consistent("x"), Item.consistent("y")));

        assertThat(result.stats().detectedIssues()).isZero();
        assertThat(result.stats().repaired()).isZero();
        assertThat(result.stats().verified()).isEqualTo(2);
    }
}
