package com.example.gsb.repair;

import com.example.gsb.repair.store.InMemoryProgressStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 需求 1：检测 → 决策 → 修复 → 复核闭环流程。 */
class ClosedLoopTest {

    @Test
    void closesLoopFromDetectionToVerification() {
        Spy spy = new Spy();
        spy.put("obj-1", "bad");
        spy.put("obj-2", "good");

        Orchestrator orchestrator = Orchestrator.builder(TestRules.detectionStep(spy))
                .addRule(TestRules.passingWhenGood(spy, RecheckFailurePolicy.ROLLBACK))
                .progressStore(new InMemoryProgressStore())
                .parallelism(2)
                .build();

        OrchestrationReport report = orchestrator.run(List.of("obj-1", "obj-2"));

        assertThat(report.objects()).hasSize(2);
        ObjectResult obj1 = report.objects().get(0);
        assertThat(obj1.objectId()).isEqualTo("obj-1");
        assertThat(obj1.detectionExecuted()).isTrue();
        assertThat(obj1.issues()).hasSize(1);
        assertThat(obj1.issues().get(0).status()).isEqualTo(IssueStatus.VERIFIED);

        ObjectResult obj2 = report.objects().get(1);
        assertThat(obj2.issues()).isEmpty();

        // 修复确实生效
        assertThat(spy.values.get("obj-1")).isEqualTo("good");
        // 需求 7：统计
        assertThat(report.stats().issuesDetected()).isEqualTo(1);
        assertThat(report.stats().repairsSucceeded()).isEqualTo(1);
        assertThat(report.stats().verificationsPassed()).isEqualTo(1);
        assertThat(report.stats().rollbacks()).isZero();
        assertThat(report.stats().manualInterventions()).isZero();
        // 复核在修复之后执行（闭环顺序）
        assertThat(spy.repairInvocations.get()).isEqualTo(1);
        assertThat(spy.recheckInvocations.get()).isEqualTo(1);
    }

    @Test
    void duplicateObjectIdsAreProcessedOnce() {
        Spy spy = new Spy();
        spy.put("obj-1", "bad");

        Orchestrator orchestrator = Orchestrator.builder(TestRules.detectionStep(spy))
                .addRule(TestRules.passingWhenGood(spy, RecheckFailurePolicy.ROLLBACK))
                .progressStore(new InMemoryProgressStore())
                .build();

        OrchestrationReport report = orchestrator.run(List.of("obj-1", "obj-1", "obj-1"));

        assertThat(report.objects()).hasSize(1);
        assertThat(spy.detectInvocations.get()).isEqualTo(1);
        assertThat(spy.repairMutations.get()).isEqualTo(1);
    }

    @Test
    void issueWithoutRuleIsMarkedForManualIntervention() {
        Spy spy = new Spy();
        spy.put("obj-1", "bad");

        Orchestrator orchestrator = Orchestrator.builder(TestRules.detectionStep(spy))
                .progressStore(new InMemoryProgressStore())
                .build();

        OrchestrationReport report = orchestrator.run(List.of("obj-1"));

        IssueResult result = report.allIssues().get(0);
        assertThat(result.status()).isEqualTo(IssueStatus.MANUAL_REQUIRED);
        assertThat(result.note()).contains("no repair rule");
        assertThat(report.stats().manualInterventions()).isEqualTo(1);
        assertThat(spy.repairInvocations.get()).isZero();
    }
}
