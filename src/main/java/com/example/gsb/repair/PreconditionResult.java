package com.example.gsb.repair;

/** 前置条件评估结果。不满足时必须携带跳过原因。 */
public record PreconditionResult(boolean satisfied, String reason) {

    public static PreconditionResult ok() {
        return new PreconditionResult(true, "");
    }

    public static PreconditionResult rejected(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("rejected result requires a reason");
        }
        return new PreconditionResult(false, reason);
    }
}
