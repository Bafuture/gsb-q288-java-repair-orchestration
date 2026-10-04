package com.example.gsb.repair;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 一致性校验与修复编排器。
 *
 * <p>对每个目标执行"检测→决策（前置条件）→修复→复核"闭环：
 * <ul>
 *   <li>检测一致：跳过，无副作用；</li>
 *   <li>前置条件不满足：跳过修复并记录原因；</li>
 *   <li>复核不通过：按策略回滚或标记待人工；</li>
 *   <li>不同目标并行（受 maxConcurrency 限制），同一目标经 {@link KeyedLock} 串行；</li>
 *   <li>每个成功步骤写入检查点，中断重启后从断点继续，不重复已成功步骤。</li>
 * </ul>
 */
public final class RepairOrchestrator {

    private final CheckpointStore checkpointStore;
    private final int maxConcurrency;
    private final KeyedLock<String> targetLocks = new KeyedLock<>();
    private final StatsCollector totalStats = new StatsCollector();

    public RepairOrchestrator(CheckpointStore checkpointStore, int maxConcurrency) {
        if (maxConcurrency < 1) {
            throw new IllegalArgumentException("maxConcurrency 必须 >= 1");
        }
        this.checkpointStore = checkpointStore;
        this.maxConcurrency = maxConcurrency;
    }

    /** 累计统计（跨多次 run），可用于中断恢复后汇总整体进度。 */
    public RepairStats totalStats() {
        return totalStats.snapshot();
    }

    /**
     * 对一批目标执行闭环流程。
     *
     * <p>步骤抛出 {@link FatalStepException} 时整个运行立即中止（模拟进程崩溃），
     * 已完成目标的检查点已持久化，再次调用 run 即可断点续跑。
     */
    public <T> OrchestrationResult run(RepairFlow<T> flow, List<T> targets) {
        StatsCollector runStats = new StatsCollector();
        ConcurrentLinkedQueue<TargetResult> results = new ConcurrentLinkedQueue<>();
        AtomicBoolean aborted = new AtomicBoolean(false);

        ExecutorService executor = Executors.newFixedThreadPool(maxConcurrency);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (T target : targets) {
                futures.add(executor.submit(() -> processTarget(flow, target, runStats, results, aborted)));
            }
            for (Future<?> future : futures) {
                await(future);
            }
        } finally {
            executor.shutdownNow();
        }

        RepairStats stats = runStats.snapshot();
        totalAccumulate(stats);
        return new OrchestrationResult(List.copyOf(results), stats, aborted.get());
    }

    private void await(Future<?> future) {
        try {
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("编排线程被中断", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("编排任务异常", e.getCause());
        }
    }

    private void totalAccumulate(RepairStats stats) {
        // StatsCollector 无 add 接口，这里逐项回放增量。
        for (int i = 0; i < stats.detectedIssues(); i++) {
            totalStats.issueDetected();
        }
        for (int i = 0; i < stats.repaired(); i++) {
            totalStats.repaired();
        }
        for (int i = 0; i < stats.verified(); i++) {
            totalStats.verified();
        }
        for (int i = 0; i < stats.rolledBack(); i++) {
            totalStats.rolledBack();
        }
        for (int i = 0; i < stats.manualRequired(); i++) {
            totalStats.manualRequired();
        }
        for (int i = 0; i < stats.skipped(); i++) {
            totalStats.skipped();
        }
        for (int i = 0; i < stats.failed(); i++) {
            totalStats.failed();
        }
    }

    private <T> void processTarget(RepairFlow<T> flow, T target, StatsCollector stats,
                                   ConcurrentLinkedQueue<TargetResult> results, AtomicBoolean aborted) {
        if (aborted.get()) {
            return;
        }
        String targetId = flow.targetIdOf(target);
        targetLocks.lock(targetId);
        try {
            if (aborted.get()) {
                return;
            }
            processTargetLocked(flow, target, targetId, stats, results);
        } catch (FatalStepException fatal) {
            aborted.set(true);
        } finally {
            targetLocks.unlock(targetId);
        }
    }

    private <T> void processTargetLocked(RepairFlow<T> flow, T target, String targetId,
                                         StatsCollector stats, ConcurrentLinkedQueue<TargetResult> results) {
        try {
            Optional<Checkpoint> saved = checkpointStore.load(flow.name(), targetId);
            if (saved.isPresent() && saved.get().isTerminal()) {
                return; // 已闭环（或已有终态结论），恢复时直接跳过
            }

            TargetState state = saved.map(Checkpoint::state).orElse(null);
            String issue = saved.map(Checkpoint::issue).orElse("");

            // 1) 检测
            if (state == null) {
                DetectResult detected = flow.detect().detect(target);
                if (detected.consistent()) {
                    checkpointStore.save(Checkpoint.terminal(flow.name(), targetId, TargetState.VERIFIED));
                    stats.verified();
                    results.add(TargetResult.of(targetId, TargetState.VERIFIED, "目标一致，无需修复"));
                    return;
                }
                issue = detected.issue();
                stats.issueDetected();
                checkpointStore.save(Checkpoint.detected(flow.name(), targetId, issue));
                state = TargetState.DETECTED;
            }

            RepairContext context = new RepairContext(issue);

            // 2) 决策：前置条件 + 3) 修复
            if (state == TargetState.DETECTED) {
                PreconditionResult precondition = flow.precondition().evaluate(target, context);
                if (!precondition.satisfied()) {
                    checkpointStore.save(Checkpoint.terminal(flow.name(), targetId, TargetState.SKIPPED));
                    stats.skipped();
                    results.add(TargetResult.of(targetId, TargetState.SKIPPED, precondition.reason()));
                    return;
                }
                flow.repair().repair(target, context);
                stats.repaired();
                checkpointStore.save(Checkpoint.repaired(flow.name(), targetId, issue));
                state = TargetState.REPAIRED;
            }

            // 4) 复核
            boolean verified = flow.verify().verify(target, context);
            if (verified) {
                checkpointStore.save(Checkpoint.terminal(flow.name(), targetId, TargetState.VERIFIED));
                stats.verified();
                results.add(TargetResult.of(targetId, TargetState.VERIFIED, "复核通过"));
                return;
            }

            // 复核不通过：按配置策略处理
            if (flow.verifyFailurePolicy() == VerifyFailurePolicy.ROLLBACK) {
                flow.rollback().rollback(target, context);
                checkpointStore.save(Checkpoint.terminal(flow.name(), targetId, TargetState.ROLLED_BACK));
                stats.rolledBack();
                results.add(TargetResult.of(targetId, TargetState.ROLLED_BACK, "复核不通过，已回滚"));
            } else {
                checkpointStore.save(Checkpoint.terminal(flow.name(), targetId, TargetState.MANUAL_REQUIRED));
                stats.manualRequired();
                results.add(TargetResult.of(targetId, TargetState.MANUAL_REQUIRED, "复核不通过，待人工处理"));
            }
        } catch (FatalStepException fatal) {
            throw fatal;
        } catch (Exception e) {
            try {
                checkpointStore.save(Checkpoint.terminal(flow.name(), targetId, TargetState.FAILED));
            } catch (RuntimeException ignored) {
                // 检查点写入失败不掩盖原始异常
            }
            stats.failed();
            results.add(TargetResult.of(targetId, TargetState.FAILED, String.valueOf(e.getMessage())));
        }
    }
}
