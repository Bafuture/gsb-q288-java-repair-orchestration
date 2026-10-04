package com.example.gsb.repair;

import java.util.Objects;

/**
 * 断点检查点：记录单个目标已推进到的状态与检测产出，
 * 中断恢复时据此跳过已成功完成的步骤。
 */
public record Checkpoint(String flowName, String targetId, TargetState state, String issue) {

    public Checkpoint {
        Objects.requireNonNull(flowName, "flowName");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(issue, "issue");
    }

    public static Checkpoint detected(String flowName, String targetId, String issue) {
        return new Checkpoint(flowName, targetId, TargetState.DETECTED, issue);
    }

    public static Checkpoint repaired(String flowName, String targetId, String issue) {
        return new Checkpoint(flowName, targetId, TargetState.REPAIRED, issue);
    }

    public static Checkpoint terminal(String flowName, String targetId, TargetState state) {
        return new Checkpoint(flowName, targetId, state, "");
    }

    /** 是否为终态（无需再处理，恢复时直接跳过）。 */
    public boolean isTerminal() {
        return state != TargetState.DETECTED && state != TargetState.REPAIRED;
    }
}
