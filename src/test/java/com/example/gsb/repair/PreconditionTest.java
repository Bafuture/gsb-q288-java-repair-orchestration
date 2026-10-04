package com.example.gsb.repair;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** 修复前置条件：不满足时跳过修复并给出原因。 */
class PreconditionTest {

    @Test
    void repairIsSkippedWithReasonWhenPreconditionNotSatisfied() {
        AtomicInteger repairCalls = new AtomicInteger();
        RepairFlow<Item> flow = RepairFlow.builder(Item.class, "guarded-flow", item -> item.id)
                .detect(Item.detectStep())
                .precondition(Item.notLocked())
                .repair((item, ctx) -> {
                    repairCalls.incrementAndGet();
                    Item.idempotentRepair().repair(item, ctx);
                })
                .verify(Item.verifyStep())
                .verifyFailurePolicy(VerifyFailurePolicy.MARK_FOR_MANUAL)
                .build();

        Item locked = Item.inconsistent("locked-item");
        locked.locked = true;
        Item normal = Item.inconsistent("normal-item");

        RepairOrchestrator orchestrator = new RepairOrchestrator(new InMemoryCheckpointStore(), 2);
        OrchestrationResult result = orchestrator.run(flow, List.of(locked, normal));

        List<TargetResult> skipped = result.byState(TargetState.SKIPPED);
        assertThat(skipped).hasSize(1);
        assertThat(skipped.get(0).targetId()).isEqualTo("locked-item");
        assertThat(skipped.get(0).skipReason()).hasValue("对象已锁定，禁止自动修复");
        assertThat(locked.isConsistent()).isFalse();
        assertThat(locked.mutations.get()).isZero();

        assertThat(result.byState(TargetState.VERIFIED)).hasSize(1);
        assertThat(normal.isConsistent()).isTrue();

        assertThat(repairCalls.get()).isEqualTo(1);
        assertThat(result.stats().detectedIssues()).isEqualTo(2);
        assertThat(result.stats().skipped()).isEqualTo(1);
        assertThat(result.stats().repaired()).isEqualTo(1);
    }

    @Test
    void preconditionReceivesDetectContext() {
        StringBuilder seenIssue = new StringBuilder();
        RepairFlow<Item> flow = RepairFlow.builder(Item.class, "ctx-flow", item -> item.id)
                .detect(Item.detectStep())
                .precondition((item, ctx) -> {
                    seenIssue.append(ctx.issue());
                    return PreconditionResult.ok();
                })
                .repair(Item.idempotentRepair())
                .verify(Item.verifyStep())
                .verifyFailurePolicy(VerifyFailurePolicy.MARK_FOR_MANUAL)
                .build();

        new RepairOrchestrator(new InMemoryCheckpointStore(), 1)
                .run(flow, List.of(Item.inconsistent("ctx-item")));

        assertThat(seenIssue.toString()).contains("值不一致");
    }
}
