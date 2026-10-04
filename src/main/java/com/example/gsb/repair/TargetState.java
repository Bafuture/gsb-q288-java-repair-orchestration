package com.example.gsb.repair;

/**
 * 单个目标在闭环流程中的进度状态，按序推进并随检查点持久化。
 */
public enum TargetState {

    /** 检测完成且发现不一致，等待前置条件评估。 */
    DETECTED,

    /** 前置条件通过，修复执行成功，等待复核。 */
    REPAIRED,

    /** 复核通过，闭环完成。 */
    VERIFIED,

    /** 复核不通过，修复已回滚。 */
    ROLLED_BACK,

    /** 复核不通过，已标记待人工处理。 */
    MANUAL_REQUIRED,

    /** 前置条件不满足，修复被跳过。 */
    SKIPPED,

    /** 步骤执行抛出异常，流程失败。 */
    FAILED
}
