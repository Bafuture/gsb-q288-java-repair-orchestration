package com.example.gsb.repair;

/** 单个问题在闭环流程中的状态机取值。 */
public enum IssueStatus {

    /** 检测发现，尚未进入修复。 */
    DETECTED,

    /** 前置条件不满足，修复被跳过（终态）。 */
    SKIPPED_PRECONDITION,

    /** 修复已执行，复核尚未完成（中断恢复时从此状态继续复核）。 */
    REPAIRED,

    /** 复核通过（终态）。 */
    VERIFIED,

    /** 复核不通过，已回滚（终态）。 */
    ROLLED_BACK,

    /** 复核不通过或流程异常，待人工处理（终态）。 */
    MANUAL_REQUIRED;

    /** 是否为闭环终态：终态步骤在中断恢复时不再重复执行。 */
    public boolean terminal() {
        return this == SKIPPED_PRECONDITION
                || this == VERIFIED
                || this == ROLLED_BACK
                || this == MANUAL_REQUIRED;
    }
}
