package com.example.gsb.repair;

import java.util.List;

/** 单个目标对象的闭环处理结果。 */
public record ObjectResult(String objectId, boolean detectionExecuted, List<IssueResult> issues) {

    public ObjectResult {
        issues = List.copyOf(issues);
    }
}
