package com.example.gsb.repair.store;

import com.example.gsb.repair.IssueStatus;

/** 单个问题已持久化的执行进度。 */
public record StatusRecord(IssueStatus status, String note) {

    public StatusRecord {
        if (note == null) {
            note = "";
        }
    }

    public static StatusRecord of(IssueStatus status) {
        return new StatusRecord(status, "");
    }

    public static StatusRecord of(IssueStatus status, String note) {
        return new StatusRecord(status, note);
    }
}
