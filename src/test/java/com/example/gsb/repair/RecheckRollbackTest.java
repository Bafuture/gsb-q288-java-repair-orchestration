package com.example.gsb.repair;

import com.example.gsb.repair.store.InMemoryProgressStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 需求 4：复核不通过时按可配置策略回滚或转人工。 */
class RecheckRollbackTest {

    @Test
    void recheckFailureWithRollbackPolicyRollsBackRepair() {
        Spy spy = new Spy();
        spy.put("obj-1", "bad");

        // 复核始终不通过，模拟修复未能真正解决不一致。
        RepairRule rule = TestRules.guardedRule(spy, RecheckFailurePolicy.ROLLBACK, objectId -> false);
        Orchestrator orchestrator = Orchestrator.builder(TestRules.detectionStep(spy))
                .addRule(rule)
                .progressStore(new InMemoryProgressStore())
                .build();

        OrchestrationReport report = orchestrator.run(List.of("obj-1"));

        IssueResult result = report.allIssues().get(0);
        assertThat(result.status()).isEqualTo(IssueStatus.ROLLED_BACK);
        assertThat(result.note()).contains("recheck");
        // 修复被回滚，对象恢复原始状态
        assertThat(spy.values.get("obj-1")).isEqualTo("bad");
        assertThat(spy.rollbackInvocations.get()).isEqualTo(1);
        assertThat(report.stats().rollbacks()).isEqualTo(1);
        assertThat(report.stats().verificationsPassed()).isZero();
        assertThat(report.stats().repairsSucceeded()).isEqualTo(1);
    }

    @Test
    void recheckFailureWithManualPolicyKeepsChangeAndMarksManual() {
        Spy spy = new Spy();
        spy.put("obj-1", "bad");

        RepairRule rule = RepairRule.builder(TestRules.ISSUE_TYPE)
                .action(context -> spy.guardedRepair(context.objectId()))
                .recheck(context -> false)
                .failurePolicy(RecheckFailurePolicy.MANUAL)
                .build();
        Orchestrator orchestrator = Orchestrator.builder(TestRules.detectionStep(spy))
                .addRule(rule)
                .progressStore(new InMemoryProgressStore())
                .build();

        OrchestrationReport report = orchestrator.run(List.of("obj-1"));

        IssueResult result = report.allIssues().get(0);
        assertThat(result.status()).isEqualTo(IssueStatus.MANUAL_REQUIRED);
        // 人工策略下不回滚，修复结果保留待人工确认
        assertThat(spy.values.get("obj-1")).isEqualTo("good");
        assertThat(spy.rollbackInvocations.get()).isZero();
        assertThat(report.stats().manualInterventions()).isEqualTo(1);
        assertThat(report.stats().rollbacks()).isZero();
    }

    @Test
    void ruleLevelPolicyOverridesGlobalDefault() {
        Spy spy = new Spy();
        spy.put("obj-1", "bad");

        // 全局默认 ROLLBACK，但规则覆盖为 MANUAL。
        RepairRule rule = RepairRule.builder(TestRules.ISSUE_TYPE)
                .action(context -> spy.guardedRepair(context.objectId()))
                .recheck(context -> false)
                .failurePolicy(RecheckFailurePolicy.MANUAL)
                .build();
        Orchestrator orchestrator = Orchestrator.builder(TestRules.detectionStep(spy))
                .addRule(rule)
                .defaultFailurePolicy(RecheckFailurePolicy.ROLLBACK)
                .progressStore(new InMemoryProgressStore())
                .build();

        OrchestrationReport report = orchestrator.run(List.of("obj-1"));

        assertThat(report.allIssues().get(0).status()).isEqualTo(IssueStatus.MANUAL_REQUIRED);
        assertThat(spy.rollbackInvocations.get()).isZero();
    }

    @Test
    void rollbackPolicyWithoutRollbackActionFallsBackToManual() {
        Spy spy = new Spy();
        spy.put("obj-1", "bad");

        RepairRule rule = RepairRule.builder(TestRules.ISSUE_TYPE)
                .action(context -> spy.guardedRepair(context.objectId()))
                .recheck(context -> false)
                .build();
        Orchestrator orchestrator = Orchestrator.builder(TestRules.detectionStep(spy))
                .addRule(rule)
                .defaultFailurePolicy(RecheckFailurePolicy.ROLLBACK)
                .progressStore(new InMemoryProgressStore())
                .build();

        OrchestrationReport report = orchestrator.run(List.of("obj-1"));

        IssueResult result = report.allIssues().get(0);
        assertThat(result.status()).isEqualTo(IssueStatus.MANUAL_REQUIRED);
        assertThat(result.note()).contains("no rollback action");
        assertThat(report.stats().manualInterventions()).isEqualTo(1);
    }
}
