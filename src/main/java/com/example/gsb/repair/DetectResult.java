package com.example.gsb.repair;

import java.util.Objects;

/**
 * 检测结果：是否一致，以及不一致时的问题描述（会持久化到检查点，供中断恢复使用）。
 */
public record DetectResult(boolean consistent, String issue) {

    public DetectResult {
        Objects.requireNonNull(issue, "issue");
    }

    public static DetectResult ok() {
        return new DetectResult(true, "");
    }

    public static DetectResult problem(String issue) {
        return new DetectResult(false, issue);
    }
}
