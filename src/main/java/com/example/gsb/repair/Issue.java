package com.example.gsb.repair;

import java.util.Objects;

/**
 * 一致性检测发现的问题。{@code objectId} 标识被检测/修复的目标对象，
 * {@code type} 标识问题类别（用于匹配修复规则），{@code detail} 为可选的补充说明。
 */
public record Issue(String objectId, String type, String detail) {

    public Issue {
        Objects.requireNonNull(objectId, "objectId");
        Objects.requireNonNull(type, "type");
        detail = detail == null ? "" : detail;
    }

    public Issue(String objectId, String type) {
        this(objectId, type, "");
    }

    /** 稳定的问题标识，用于进度持久化与幂等判断。 */
    public String key() {
        return objectId + "|" + type + "|" + detail;
    }
}
