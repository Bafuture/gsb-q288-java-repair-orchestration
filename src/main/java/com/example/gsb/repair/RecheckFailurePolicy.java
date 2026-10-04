package com.example.gsb.repair;

/** 复核不通过时的处置策略。 */
public enum RecheckFailurePolicy {

    /** 回滚修复动作，问题标记为已回滚。 */
    ROLLBACK,

    /** 不回滚，问题标记为待人工处理。 */
    MANUAL
}
