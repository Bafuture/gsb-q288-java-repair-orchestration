package com.example.gsb.repair;

import java.util.List;

/** 一次编排运行的汇总结果：逐对象明细 + 统计。 */
public record OrchestrationReport(List<ObjectResult> objects, RepairStats stats) {

    public OrchestrationReport {
        objects = List.copyOf(objects);
    }

    public List<IssueResult> allIssues() {
        return objects.stream().flatMap(object -> object.issues().stream()).toList();
    }
}
