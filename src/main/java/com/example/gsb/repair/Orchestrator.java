package com.example.gsb.repair;

import com.example.gsb.repair.exec.StripedExecutor;
import com.example.gsb.repair.store.ProgressStore;
import com.example.gsb.repair.store.StatusRecord;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * 一致性校验与修复编排器，闭环流程为：
 * <pre>
 *   检测(detect) → 决策(规则匹配 + 前置条件) → 修复(repair) → 复核(recheck)
 *                                                         ├─ 通过：VERIFIED
 *                                                         └─ 不通过：按策略回滚(ROLLED_BACK) 或 转人工(MANUAL_REQUIRED)
 * </pre>
 *
 * <p>能力：
 * <ul>
 *   <li>前置条件不满足时跳过修复并记录原因；</li>
 *   <li>修复动作本身要求幂等，编排层同时保证已成功步骤不重复执行（见 {@link ProgressStore}）；</li>
 *   <li>不同对象并行、同一对象串行，并发度可配；</li>
 *   <li>每个步骤完成即落盘，中断重启后从断点继续；</li>
 *   <li>每次运行产出检测/修复/复核/回滚/人工介入统计。</li>
 * </ul>
 */
public final class Orchestrator {

    private final DetectionStep detectionStep;
    private final Map<String, RepairRule> rules;
    private final ProgressStore store;
    private final RecheckFailurePolicy defaultFailurePolicy;
    private final int parallelism;

    private Orchestrator(Builder builder) {
        this.detectionStep = builder.detectionStep;
        this.rules = Map.copyOf(builder.rules);
        this.store = builder.store;
        this.defaultFailurePolicy = builder.defaultFailurePolicy;
        this.parallelism = builder.parallelism;
    }

    public static Builder builder(DetectionStep detectionStep) {
        return new Builder(detectionStep);
    }

    /**
     * 对一批目标对象执行闭环流程。对象间并行、同一对象串行；
     * 传入重复的对象 id 也只会被处理一次（串行保证）。
     */
    public OrchestrationReport run(List<String> objectIds) {
        RepairStats stats = new RepairStats();
        List<String> distinctIds = objectIds.stream().distinct().toList();
        List<ObjectResult> results = new ArrayList<>(distinctIds.size());

        StripedExecutor executor = new StripedExecutor(parallelism);
        try {
            List<Future<ObjectResult>> futures = new ArrayList<>();
            for (String objectId : distinctIds) {
                futures.add(executor.submit(objectId, () -> processObject(objectId, stats)));
            }
            for (Future<ObjectResult> future : futures) {
                try {
                    results.add(future.get());
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof OrchestrationAbortException abort) {
                        // 致命中断：取消排队任务，保留已落盘进度，向上传播。
                        executor.shutdownNow();
                        throw abort;
                    }
                    throw unwrap(e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("orchestration interrupted", e);
                }
            }
        } finally {
            executor.close();
        }
        return new OrchestrationReport(results, stats);
    }

    private static RuntimeException unwrap(ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        return new IllegalStateException(cause);
    }

    private ObjectResult processObject(String objectId, RepairStats stats) {
        List<String> issueKeys;
        boolean detectionExecuted;
        Optional<List<String>> saved = store.detectedIssueKeys(objectId);
        if (saved.isPresent()) {
            // 中断恢复：检测已成功完成，不重复执行检测步骤。
            issueKeys = saved.get();
            detectionExecuted = false;
        } else {
            List<Issue> detected = detectionStep.detect(objectId);
            issueKeys = detected.stream().map(Issue::key).distinct().toList();
            // 先逐条落盘问题，再标记对象检测完成，保证崩溃后可续跑。
            for (Issue issue : detected) {
                if (store.status(issue.key()).isEmpty()) {
                    store.putStatus(issue.key(), StatusRecord.of(IssueStatus.DETECTED));
                }
            }
            store.markObjectDetected(objectId, issueKeys);
            detectionExecuted = true;
        }

        List<IssueResult> issueResults = new ArrayList<>();
        for (String key : issueKeys) {
            issueResults.add(processIssue(objectId, key, stats));
        }
        return new ObjectResult(objectId, detectionExecuted, issueResults);
    }

    private IssueResult processIssue(String objectId, String issueKey, RepairStats stats) {
        StatusRecord existing = store.status(issueKey).orElse(null);
        IssueStatus existingStatus = existing == null ? null : existing.status();
        if (existingStatus != null) {
            stats.incrementDetected();
        }
        if (existingStatus != null && existingStatus.terminal()) {
            // 终态问题不重复执行任何步骤。
            stats.countTerminalStatus(existingStatus);
            return new IssueResult(toIssue(issueKey), existingStatus, existing.note());
        }

        Issue issue = toIssue(issueKey);
        RepairRule rule = rules.get(issue.type());
        if (rule == null) {
            String note = "no repair rule for issue type: " + issue.type();
            return finish(issue, IssueStatus.MANUAL_REQUIRED, note, stats);
        }

        if (existingStatus != IssueStatus.REPAIRED) {
            // 决策：前置条件评估（REPAIRED 状态说明修复已成功，恢复时直接进入复核）。
            RepairPrecondition precondition = rule.precondition();
            if (precondition != null) {
                PreconditionResult preconditionResult;
                try {
                    preconditionResult = precondition.evaluate(issue);
                } catch (OrchestrationAbortException abort) {
                    throw abort;
                } catch (Exception e) {
                    String note = "precondition evaluation failed: " + e;
                    return finish(issue, IssueStatus.MANUAL_REQUIRED, note, stats);
                }
                if (!preconditionResult.satisfied()) {
                    return finish(issue, IssueStatus.SKIPPED_PRECONDITION, preconditionResult.reason(), stats);
                }
            }

            // 修复。
            RepairContext context = new RepairContext(objectId, issue);
            try {
                rule.action().repair(context);
            } catch (OrchestrationAbortException abort) {
                // 致命中断：不落任何“已修复”标记，重新运行时会重新执行修复（修复动作须幂等）。
                throw abort;
            } catch (Exception e) {
                return finish(issue, IssueStatus.MANUAL_REQUIRED, "repair failed: " + e, stats);
            }
            store.putStatus(issueKey, StatusRecord.of(IssueStatus.REPAIRED));
            stats.incrementRepaired();
        }

        // 复核。
        RepairContext context = new RepairContext(objectId, issue);
        boolean passed;
        try {
            passed = rule.recheck().recheck(context);
        } catch (OrchestrationAbortException abort) {
            throw abort;
        } catch (Exception e) {
            return handleRecheckFailure(rule, issue, issueKey, "recheck failed: " + e, stats);
        }
        if (passed) {
            return finish(issue, IssueStatus.VERIFIED, "", stats);
        }
        return handleRecheckFailure(rule, issue, issueKey, "recheck reported inconsistency", stats);
    }

    private IssueResult handleRecheckFailure(RepairRule rule, Issue issue, String issueKey,
                                             String note, RepairStats stats) {
        RecheckFailurePolicy policy = rule.failurePolicy() != null ? rule.failurePolicy() : defaultFailurePolicy;
        if (policy == RecheckFailurePolicy.ROLLBACK) {
            RollbackAction rollback = rule.rollback();
            if (rollback == null) {
                return finish(issue, IssueStatus.MANUAL_REQUIRED,
                        note + "; rollback policy configured but no rollback action", stats);
            }
            try {
                rollback.rollback(new RepairContext(issue.objectId(), issue));
            } catch (OrchestrationAbortException abort) {
                throw abort;
            } catch (Exception e) {
                return finish(issue, IssueStatus.MANUAL_REQUIRED, note + "; rollback failed: " + e, stats);
            }
            return finish(issue, IssueStatus.ROLLED_BACK, note, stats);
        }
        return finish(issue, IssueStatus.MANUAL_REQUIRED, note, stats);
    }

    private IssueResult finish(Issue issue, IssueStatus status, String note, RepairStats stats) {
        store.putStatus(issue.key(), StatusRecord.of(status, note));
        stats.countTerminalStatus(status);
        return new IssueResult(issue, status, note);
    }

    private static Issue toIssue(String issueKey) {
        int first = issueKey.indexOf('|');
        int second = issueKey.indexOf('|', first + 1);
        return new Issue(issueKey.substring(0, first), issueKey.substring(first + 1, second),
                issueKey.substring(second + 1));
    }

    public static final class Builder {

        private final DetectionStep detectionStep;
        private final Map<String, RepairRule> rules = new LinkedHashMap<>();
        private ProgressStore store;
        private RecheckFailurePolicy defaultFailurePolicy = RecheckFailurePolicy.ROLLBACK;
        private int parallelism = 1;

        private Builder(DetectionStep detectionStep) {
            this.detectionStep = detectionStep;
        }

        public Builder addRule(RepairRule rule) {
            rules.put(rule.issueType(), rule);
            return this;
        }

        public Builder progressStore(ProgressStore store) {
            this.store = store;
            return this;
        }

        public Builder defaultFailurePolicy(RecheckFailurePolicy policy) {
            this.defaultFailurePolicy = policy;
            return this;
        }

        public Builder parallelism(int parallelism) {
            this.parallelism = parallelism;
            return this;
        }

        public Orchestrator build() {
            if (detectionStep == null) {
                throw new IllegalStateException("detection step is required");
            }
            if (store == null) {
                throw new IllegalStateException("progress store is required");
            }
            return new Orchestrator(this);
        }
    }
}
