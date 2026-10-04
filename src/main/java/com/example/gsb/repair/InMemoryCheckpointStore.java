package com.example.gsb.repair;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 内存检查点存储：适用于单进程内重跑恢复，进程退出即丢失。
 */
public final class InMemoryCheckpointStore implements CheckpointStore {

    private final ConcurrentMap<String, Checkpoint> checkpoints = new ConcurrentHashMap<>();

    @Override
    public void save(Checkpoint checkpoint) {
        checkpoints.put(key(checkpoint.flowName(), checkpoint.targetId()), checkpoint);
    }

    @Override
    public Optional<Checkpoint> load(String flowName, String targetId) {
        return Optional.ofNullable(checkpoints.get(key(flowName, targetId)));
    }

    private static String key(String flowName, String targetId) {
        return flowName + " " + targetId;
    }
}
