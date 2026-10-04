package com.example.gsb.repair;

import com.example.gsb.repair.store.InMemoryProgressStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 需求 2：修复前置条件不满足时跳过修复并给出原因。 */
class PreconditionTest {

    private static DetectionStep twoIssueDetection(Spy spy) {
        return new DetectionStep() {
            @Override
            public String name() {
                return "precondition-check";
            }

            @Override
            public List<Issue> detect(String objectId) {
                spy.detectInvocations.incrementAndGet();
                if ("needs-approval".equals(objectId)) {
                    return List.of(new Issue(objectId, "config-drift", "requires-approval"));
                }
                return List.of(new Issue(objectId, "config-drift", "auto-fixable"));
            }
        };
    }

    @Test
    void unsatisfiedPreconditionSkipsRepairWithReason() {
        Spy spy = new Spy();
        RepairRule rule = RepairRule.builder("config-drift")
                .action(context -> spy.guardedRepair(context.objectId()))
                .recheck(context -> true)
                .precondition(issue -> issue.detail().contains("requires-approval")
                        ? PreconditionResult.rejected("该问题需人工审批后方可修复")
                        : PreconditionResult.ok())
                .build();

        Orchestrator orchestrator = Orchestrator.builder(twoIssueDetection(spy))
                .addRule(rule)
                .progressStore(new InMemoryProgressStore())
                .build();

        OrchestrationReport report = orchestrator.run(List.of("needs-approval", "auto-object"));

        IssueResult skipped = report.objects().get(0).issues().get(0);
        assertThat(skipped.status()).isEqualTo(IssueStatus.SKIPPED_PRECONDITION);
        assertThat(skipped.note()).isEqualTo("该问题需人工审批后方可修复");

        IssueResult repaired = report.objects().get(1).issues().get(0);
        assertThat(repaired.status()).isEqualTo(IssueStatus.VERIFIED);

        // 被跳过的问题没有执行修复动作
        assertThat(spy.repairInvocations.get()).isEqualTo(1);
        assertThat(report.stats().skippedByPrecondition()).isEqualTo(1);
        assertThat(report.stats().repairsSucceeded()).isEqualTo(1);
    }

    @Test
    void preconditionEvaluationFailureMarksManualIntervention() {
        Spy spy = new Spy();
        RepairRule rule = RepairRule.builder("config-drift")
                .action(context -> spy.guardedRepair(context.objectId()))
                .recheck(context -> true)
                .precondition(issue -> {
                    throw new IllegalStateException("approval service unavailable");
                })
                .build();

        Orchestrator orchestrator = Orchestrator.builder(twoIssueDetection(spy))
                .addRule(rule)
                .progressStore(new InMemoryProgressStore())
                .build();

        OrchestrationReport report = orchestrator.run(List.of("auto-object"));

        IssueResult result = report.allIssues().get(0);
        assertThat(result.status()).isEqualTo(IssueStatus.MANUAL_REQUIRED);
        assertThat(result.note()).contains("precondition evaluation failed");
        assertThat(spy.repairInvocations.get()).isZero();
        assertThat(report.stats().manualInterventions()).isEqualTo(1);
    }
}
