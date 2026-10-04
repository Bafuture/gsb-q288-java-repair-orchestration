package com.example.gsb.repair;

import java.util.List;
import java.util.function.Predicate;

/** 测试用检测步骤与常用修复规则构造器。 */
final class TestRules {

    static final String ISSUE_TYPE = "value-mismatch";

    private TestRules() {
    }

    static DetectionStep detectionStep(Spy spy) {
        return new DetectionStep() {
            @Override
            public String name() {
                return "value-consistency-check";
            }

            @Override
            public List<Issue> detect(String objectId) {
                spy.detectInvocations.incrementAndGet();
                if (spy.isBad(objectId)) {
                    return List.of(new Issue(objectId, ISSUE_TYPE, "expected=good"));
                }
                return List.of();
            }
        };
    }

    static RepairRule guardedRule(Spy spy, RecheckFailurePolicy policy, Predicate<String> recheckPasses) {
        return RepairRule.builder(ISSUE_TYPE)
                .action(context -> spy.guardedRepair(context.objectId()))
                .recheck(context -> {
                    spy.recheckInvocations.incrementAndGet();
                    return recheckPasses.test(context.objectId());
                })
                .rollback(context -> spy.restoreBad(context.objectId()))
                .failurePolicy(policy)
                .build();
    }

    /** 修复为 good 后复核即通过的常用规则。 */
    static RepairRule passingWhenGood(Spy spy, RecheckFailurePolicy policy) {
        return guardedRule(spy, policy, objectId -> !spy.isBad(objectId));
    }
}
