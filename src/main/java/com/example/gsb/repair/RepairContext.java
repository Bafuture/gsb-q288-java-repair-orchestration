package com.example.gsb.repair;

import java.util.Objects;

/** 修复执行上下文，携带被修复对象与触发修复的问题。 */
public record RepairContext(String objectId, Issue issue) {

    public RepairContext {
        Objects.requireNonNull(objectId, "objectId");
        Objects.requireNonNull(issue, "issue");
    }
}
