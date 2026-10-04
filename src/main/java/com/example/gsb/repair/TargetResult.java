package com.example.gsb.repair;

import java.util.Objects;
import java.util.Optional;

/**
 * 单个目标本次运行的终态结果。
 */
public record TargetResult(String targetId, TargetState state, String detail) {

    public TargetResult {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(detail, "detail");
    }

    public static TargetResult of(String targetId, TargetState state, String detail) {
        return new TargetResult(targetId, state, detail);
    }

    public Optional<String> skipReason() {
        return state == TargetState.SKIPPED ? Optional.of(detail) : Optional.empty();
    }

    public boolean closedLoop() {
        return state == TargetState.VERIFIED;
    }
}
