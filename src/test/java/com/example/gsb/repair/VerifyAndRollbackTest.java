package com.example.gsb.repair;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** 修复后复核：复核不通过时回滚或标记人工，策略可配置。 */
class VerifyAndRollbackTest {

    /** 修复前备份原值，供回滚恢复。 */
    private static final class BackupRepair implements RepairStep<Item>, RollbackStep<Item> {
        private final Map<String, String> backup = new HashMap<>();

        @Override
        public synchronized void repair(Item item, RepairContext ctx) throws Exception {
            backup.put(item.id, item.actual.get());
            Item.idempotentRepair().repair(item, ctx);
        }

        @Override
        public synchronized void rollback(Item item, RepairContext ctx) throws Exception {
            item.actual.set(backup.getOrDefault(item.id, item.actual.get()));
        }
    }

    @Test
    void failedVerifyTriggersRollbackByDefault() {
        BackupRepair step = new BackupRepair();
        RepairFlow<Item> flow = RepairFlow.builder(Item.class, "rollback-flow", item -> item.id)
                .detect(Item.detectStep())
                .repair(step)
                .verify((item, ctx) -> false) // 复核恒不通过
                .rollback(step)
                .build();

        Item item = Item.inconsistent("bad");
        OrchestrationResult result =
                new RepairOrchestrator(new InMemoryCheckpointStore(), 2).run(flow, List.of(item));

        assertThat(result.byState(TargetState.ROLLED_BACK)).hasSize(1);
        assertThat(item.actual.get()).isEqualTo("broken-bad"); // 已恢复到修复前
        assertThat(result.stats().detectedIssues()).isEqualTo(1);
        assertThat(result.stats().repaired()).isEqualTo(1);
        assertThat(result.stats().verified()).isZero();
        assertThat(result.stats().rolledBack()).isEqualTo(1);
    }

    @Test
    void failedVerifyCanMarkForManualHandlingInstead() {
        BackupRepair step = new BackupRepair();
        AtomicInteger rollbackCalls = new AtomicInteger();
        RepairFlow<Item> flow = RepairFlow.builder(Item.class, "manual-flow", item -> item.id)
                .detect(Item.detectStep())
                .repair(step)
                .verify((item, ctx) -> false)
                .rollback((item, ctx) -> rollbackCalls.incrementAndGet())
                .verifyFailurePolicy(VerifyFailurePolicy.MARK_FOR_MANUAL)
                .build();

        Item item = Item.inconsistent("bad");
        OrchestrationResult result =
                new RepairOrchestrator(new InMemoryCheckpointStore(), 2).run(flow, List.of(item));

        assertThat(result.byState(TargetState.MANUAL_REQUIRED)).hasSize(1);
        assertThat(item.actual.get()).isEqualTo("expected-bad"); // 保留修复结果
        assertThat(rollbackCalls.get()).isZero();
        assertThat(result.stats().manualRequired()).isEqualTo(1);
        assertThat(result.stats().rolledBack()).isZero();
    }

    @Test
    void rollbackPolicyRequiresRollbackStep() {
        assertThatThrownBy(() -> RepairFlow.builder(Item.class, "invalid", item -> item.id)
                .detect(Item.detectStep())
                .repair(Item.idempotentRepair())
                .verify(Item.verifyStep())
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ROLLBACK");
    }
}
