package com.example.gsb.repair;

import com.example.gsb.repair.store.InMemoryProgressStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** 需求 5：不同对象可并行、同一对象串行，并发度可控制。 */
class ConcurrencyControlTest {

    @Test
    void differentObjectsRepairedInParallelBoundedByParallelism() {
        Spy spy = new Spy();
        int objectCount = 8;
        IntStream.range(0, objectCount).forEach(i -> spy.put("obj-" + i, "bad"));

        DetectionStep detection = new DetectionStep() {
            @Override
            public String name() {
                return "parallel-check";
            }

            @Override
            public List<Issue> detect(String objectId) {
                spy.detectInvocations.incrementAndGet();
                return List.of(new Issue(objectId, TestRules.ISSUE_TYPE));
            }
        };
        RepairRule rule = RepairRule.builder(TestRules.ISSUE_TYPE)
                .action(context -> {
                    spy.trackRepair(context.objectId());
                    spy.guardedRepair(context.objectId());
                })
                .recheck(context -> true)
                .build();

        Orchestrator orchestrator = Orchestrator.builder(detection)
                .addRule(rule)
                .progressStore(new InMemoryProgressStore())
                .parallelism(3)
                .build();

        List<String> objectIds = IntStream.range(0, objectCount).mapToObj(i -> "obj-" + i).toList();
        OrchestrationReport report = orchestrator.run(objectIds);

        // 观测到并行（>1），且绝不超过配置的并发度
        assertThat(spy.maxInFlightGlobal.get()).isGreaterThan(1);
        assertThat(spy.maxInFlightGlobal.get()).isLessThanOrEqualTo(3);
        // 全部修复完成并复核通过
        assertThat(spy.repairInvocations.get()).isEqualTo(objectCount);
        assertThat(report.allIssues()).hasSize(objectCount)
                .allSatisfy(issue -> assertThat(issue.status()).isEqualTo(IssueStatus.VERIFIED));
    }

    @Test
    void issuesOfSameObjectAreAlwaysSerial() {
        Spy spy = new Spy();
        spy.put("obj-1", "bad");

        DetectionStep detection = new DetectionStep() {
            @Override
            public String name() {
                return "same-object-check";
            }

            @Override
            public List<Issue> detect(String objectId) {
                return List.of(
                        new Issue(objectId, "issue-a"),
                        new Issue(objectId, "issue-b"));
            }
        };
        RepairAction serialAction = context -> {
            spy.trackRepair(context.objectId());
            spy.guardedRepair(context.objectId());
        };
        RepairRule ruleA = RepairRule.builder("issue-a").action(serialAction).recheck(context -> true).build();
        RepairRule ruleB = RepairRule.builder("issue-b").action(serialAction).recheck(context -> true).build();

        Orchestrator orchestrator = Orchestrator.builder(detection)
                .addRule(ruleA)
                .addRule(ruleB)
                .progressStore(new InMemoryProgressStore())
                .parallelism(4)
                .build();

        // 同一对象重复提交也必须串行且只处理一次
        OrchestrationReport report = orchestrator.run(List.of("obj-1", "obj-1"));

        assertThat(spy.maxInFlightPerObject.get("obj-1").get()).isEqualTo(1);
        assertThat(spy.maxInFlightGlobal.get()).isEqualTo(1);
        assertThat(spy.repairInvocations.get()).isEqualTo(2);
        assertThat(report.objects()).hasSize(1);
    }

    @Test
    void defaultParallelismIsOne() {
        Spy spy = new Spy();
        IntStream.range(0, 4).forEach(i -> spy.put("obj-" + i, "bad"));

        Orchestrator orchestrator = Orchestrator.builder(TestRules.detectionStep(spy))
                .addRule(RepairRule.builder(TestRules.ISSUE_TYPE)
                        .action(context -> {
                            spy.trackRepair(context.objectId());
                            spy.guardedRepair(context.objectId());
                        })
                        .recheck(context -> true)
                        .build())
                .progressStore(new InMemoryProgressStore())
                .build();

        orchestrator.run(IntStream.range(0, 4).mapToObj(i -> "obj-" + i).toList());

        assertThat(spy.maxInFlightGlobal.get()).isEqualTo(1);
    }
}
