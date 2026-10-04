package com.example.gsb.repair;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** 幂等修复：同一问题重复修复不得产生副作用。 */
class IdempotentRepairTest {

    @Test
    void repairStepItselfIsIdempotent() throws Exception {
        Item item = Item.inconsistent("idem");
        RepairStep<Item> repair = Item.idempotentRepair();
        RepairContext ctx = new RepairContext("值不一致");

        repair.repair(item, ctx);
        repair.repair(item, ctx);
        repair.repair(item, ctx);

        assertThat(item.isConsistent()).isTrue();
        assertThat(item.mutations.get()).isEqualTo(1);
    }

    @Test
    void frameworkDoesNotRepeatSucceededStepsOnRerun() {
        InMemoryCheckpointStore store = new InMemoryCheckpointStore();
        AtomicInteger repairCalls = new AtomicInteger();
        AtomicInteger detectCalls = new AtomicInteger();
        RepairFlow<Item> flow = RepairFlow.builder(Item.class, "idem-flow", item -> item.id)
                .detect(item -> {
                    detectCalls.incrementAndGet();
                    return Item.detectStep().detect(item);
                })
                .repair((item, ctx) -> {
                    repairCalls.incrementAndGet();
                    Item.idempotentRepair().repair(item, ctx);
                })
                .verify(Item.verifyStep())
                .verifyFailurePolicy(VerifyFailurePolicy.MARK_FOR_MANUAL)
                .build();

        Item item = Item.inconsistent("once");
        RepairOrchestrator orchestrator = new RepairOrchestrator(store, 2);

        OrchestrationResult first = orchestrator.run(flow, List.of(item));
        assertThat(first.stats().repaired()).isEqualTo(1);
        assertThat(repairCalls.get()).isEqualTo(1);
        assertThat(item.mutations.get()).isEqualTo(1);

        OrchestrationResult second = orchestrator.run(flow, List.of(item));
        assertThat(second.stats().detectedIssues()).isZero();
        assertThat(second.stats().repaired()).isZero();
        assertThat(repairCalls.get()).isEqualTo(1);
        assertThat(detectCalls.get()).isEqualTo(1);
        assertThat(item.mutations.get()).isEqualTo(1);
    }
}
