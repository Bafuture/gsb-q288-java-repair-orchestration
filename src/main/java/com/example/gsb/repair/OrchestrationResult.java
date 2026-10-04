package com.example.gsb.repair;

import java.util.List;
import java.util.Objects;

/**
 * 一次编排运行的结果：各目标终态 + 统计快照。
 * 中断中止时只包含中断前完成的目标，已完成进度仍保存在检查点中。
 */
public record OrchestrationResult(List<TargetResult> targetResults, RepairStats stats, boolean aborted) {

    public OrchestrationResult {
        targetResults = List.copyOf(targetResults);
        Objects.requireNonNull(stats, "stats");
    }

    public boolean isSuccessForAll() {
        return !aborted && targetResults.stream().allMatch(TargetResult::closedLoop);
    }

    public List<TargetResult> byState(TargetState state) {
        return targetResults.stream().filter(r -> r.state() == state).toList();
    }
}
