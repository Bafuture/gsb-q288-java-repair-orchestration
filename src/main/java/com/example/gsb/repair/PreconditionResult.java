package com.example.gsb.repair;

import java.util.Objects;

/**
 * 前置条件评估结果。
 */
public record PreconditionResult(boolean satisfied, String reason) {

    public PreconditionResult {
        Objects.requireNonNull(reason, "reason");
    }

    public static PreconditionResult ok() {
        return new PreconditionResult(true, "");
    }

    public static PreconditionResult rejected(String reason) {
        return new PreconditionResult(false, reason);
    }
}
