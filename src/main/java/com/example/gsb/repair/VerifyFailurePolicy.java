package com.example.gsb.repair;

/**
 * 复核不通过时的处理策略（可在流程上配置默认值，也可按目标覆盖）。
 */
public enum VerifyFailurePolicy {

    /** 回滚修复，使目标恢复到修复前状态。 */
    ROLLBACK,

    /** 保留修复结果，标记为待人工处理。 */
    MARK_FOR_MANUAL
}
