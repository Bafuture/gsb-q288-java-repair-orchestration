package com.example.gsb.repair;

import com.example.gsb.repair.store.InMemoryProgressStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 需求 3：幂等修复——同一问题重复修复不得产生副作用。 */
class IdempotentRepairTest {

    @Test
    void repeatedRunDoesNotReexecuteSucceededSteps() {
        Spy spy = new Spy();
        spy.put("obj-1", "bad");
        InMemoryProgressStore store = new InMemoryProgressStore();

        Orchestrator first = Orchestrator.builder(TestRules.detectionStep(spy))
                .addRule(TestRules.passingWhenGood(spy, RecheckFailurePolicy.ROLLBACK))
                .progressStore(store)
                .build();
        first.run(List.of("obj-1"));
        assertThat(spy.repairMutations.get()).isEqualTo(1);

        // 用同一进度存储再跑一遍（等价于重启后继续）：已成功步骤不得重复执行。
        Orchestrator second = Orchestrator.builder(TestRules.detectionStep(spy))
                .addRule(TestRules.passingWhenGood(spy, RecheckFailurePolicy.ROLLBACK))
                .progressStore(store)
                .build();
        OrchestrationReport report = second.run(List.of("obj-1"));

        assertThat(spy.detectInvocations.get()).isEqualTo(1);
        assertThat(spy.repairInvocations.get()).isEqualTo(1);
        assertThat(spy.repairMutations.get()).isEqualTo(1);
        assertThat(spy.recheckInvocations.get()).isEqualTo(1);
        assertThat(report.allIssues().get(0).status()).isEqualTo(IssueStatus.VERIFIED);
        assertThat(report.stats().repairsSucceeded()).isZero();
        assertThat(report.stats().verificationsPassed()).isEqualTo(1);
    }

    @Test
    void idempotentGuardPreventsSideEffectsOnDuplicateInvocation() {
        Spy spy = new Spy();
        spy.put("obj-1", "bad");

        // 直接重复调用修复动作两次：幂等守卫保证副作用只发生一次。
        RepairAction action = context -> spy.guardedRepair(context.objectId());
        Issue issue = new Issue("obj-1", TestRules.ISSUE_TYPE);
        try {
            action.repair(new RepairContext("obj-1", issue));
            action.repair(new RepairContext("obj-1", issue));
        } catch (Exception e) {
            throw new AssertionError(e);
        }

        assertThat(spy.repairInvocations.get()).isEqualTo(2);
        assertThat(spy.repairMutations.get()).isEqualTo(1);
        assertThat(spy.values.get("obj-1")).isEqualTo("good");
    }
}
