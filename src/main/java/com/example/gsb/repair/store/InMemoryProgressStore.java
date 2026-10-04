package com.example.gsb.repair.store;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 进程内进度存储，适用于测试或单次运行。线程安全。 */
public final class InMemoryProgressStore implements ProgressStore {

    private final Map<String, List<String>> detected = new ConcurrentHashMap<>();
    private final Map<String, StatusRecord> statuses = new ConcurrentHashMap<>();

    @Override
    public void markObjectDetected(String objectId, List<String> issueKeys) {
        detected.put(objectId, List.copyOf(issueKeys));
    }

    @Override
    public Optional<List<String>> detectedIssueKeys(String objectId) {
        List<String> keys = detected.get(objectId);
        return keys == null ? Optional.empty() : Optional.of(keys);
    }

    @Override
    public void putStatus(String issueKey, StatusRecord record) {
        statuses.put(issueKey, record);
    }

    @Override
    public Optional<StatusRecord> status(String issueKey) {
        return Optional.ofNullable(statuses.get(issueKey));
    }

    @Override
    public List<String> allIssueKeys() {
        return List.copyOf(statuses.keySet());
    }
}
