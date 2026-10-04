package com.example.gsb.repair.store;

import java.util.List;
import java.util.Optional;

/**
 * 中断恢复所需的进度存储。
 *
 * <p>检测结果与每个问题的状态以“执行到哪一步就落盘到哪一步”的方式持久化：
 * 进程重启后，已完成对象不会重新检测，已终态问题不会重复修复。
 */
public interface ProgressStore {

    /** 标记某对象已完成检测（可能没有问题），使其在恢复时不再重复检测。 */
    void markObjectDetected(String objectId, List<String> issueKeys);

    /** 恢复时返回上次该对象检测出的问题 key；对象未检测过返回 empty。 */
    Optional<List<String>> detectedIssueKeys(String objectId);

    /** 持久化某个问题的最新状态。 */
    void putStatus(String issueKey, StatusRecord record);

    Optional<StatusRecord> status(String issueKey);

    /** 已记录的全部问题 key（含所有状态）。 */
    List<String> allIssueKeys();
}
