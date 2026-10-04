package com.example.gsb.repair;

import java.util.concurrent.atomic.LongAdder;

/** 编排统计计数器，线程安全。取值语义见各 getter。 */
public final class RepairStats {

    private final LongAdder issuesDetected = new LongAdder();
    private final LongAdder repairsSucceeded = new LongAdder();
    private final LongAdder verificationsPassed = new LongAdder();
    private final LongAdder rollbacks = new LongAdder();
    private final LongAdder manualInterventions = new LongAdder();
    private final LongAdder skippedByPrecondition = new LongAdder();

    public void incrementDetected() {
        issuesDetected.increment();
    }

    public void incrementRepaired() {
        repairsSucceeded.increment();
    }

    public void incrementVerified() {
        verificationsPassed.increment();
    }

    public void incrementRolledBack() {
        rollbacks.increment();
    }

    public void incrementManual() {
        manualInterventions.increment();
    }

    public void incrementSkippedPrecondition() {
        skippedByPrecondition.increment();
    }

    /** 检测发现的问题数。 */
    public long issuesDetected() {
        return issuesDetected.sum();
    }

    /** 修复动作成功执行的问题数（本运行内，恢复续跑不重复计数）。 */
    public long repairsSucceeded() {
        return repairsSucceeded.sum();
    }

    /** 复核通过的问题数。 */
    public long verificationsPassed() {
        return verificationsPassed.sum();
    }

    /** 复核失败后执行回滚的问题数。 */
    public long rollbacks() {
        return rollbacks.sum();
    }

    /** 标记为待人工处理的问题数。 */
    public long manualInterventions() {
        return manualInterventions.sum();
    }

    /** 因前置条件不满足而跳过修复的问题数。 */
    public long skippedByPrecondition() {
        return skippedByPrecondition.sum();
    }

    /** 按已持久化的终态补计（中断恢复时用于还原本次运行看到的全貌）。 */
    public void countTerminalStatus(IssueStatus status) {
        switch (status) {
            case VERIFIED -> incrementVerified();
            case ROLLED_BACK -> incrementRolledBack();
            case MANUAL_REQUIRED -> incrementManual();
            case SKIPPED_PRECONDITION -> incrementSkippedPrecondition();
            default -> {
            }
        }
    }
}
