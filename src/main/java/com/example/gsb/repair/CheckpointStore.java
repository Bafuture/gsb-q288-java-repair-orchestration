package com.example.gsb.repair;

import java.util.Optional;

/**
 * 检查点存储：为中断恢复提供持久化能力。
 * 实现必须保证 save 后数据对后续 load 可见（线程安全）。
 */
public interface CheckpointStore {

    void save(Checkpoint checkpoint);

    Optional<Checkpoint> load(String flowName, String targetId);
}
