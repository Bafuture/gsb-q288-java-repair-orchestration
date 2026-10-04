package com.example.gsb.repair;

/** 单个问题本次运行结束时的状态与说明。 */
public record IssueResult(Issue issue, IssueStatus status, String note) {

    public IssueResult {
        if (note == null) {
            note = "";
        }
    }
}
