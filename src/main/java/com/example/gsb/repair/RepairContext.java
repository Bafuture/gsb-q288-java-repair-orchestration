package com.example.gsb.repair;

import java.util.Objects;

/**
 * 修复上下文：携带检测阶段产出的问题描述，供修复与复核步骤使用。
 */
public record RepairContext(String issue) {

    public RepairContext {
        Objects.requireNonNull(issue, "issue");
    }
}
