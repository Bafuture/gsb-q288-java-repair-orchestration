package com.example.gsb.repair;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 文件检查点存储：持久化、覆盖写与跨实例恢复。 */
class FileCheckpointStoreTest {

    @TempDir
    Path dir;

    @Test
    void savesAndLoadsCheckpoint() {
        FileCheckpointStore store = new FileCheckpointStore(dir);
        store.save(Checkpoint.repaired("flow-1", "obj/1", "值不一致: actual=x:y"));

        assertThat(store.load("flow-1", "obj/1"))
                .hasValue(new Checkpoint("flow-1", "obj/1", TargetState.REPAIRED, "值不一致: actual=x:y"));
    }

    @Test
    void laterSaveOverwritesEarlierState() {
        FileCheckpointStore store = new FileCheckpointStore(dir);
        store.save(Checkpoint.detected("flow-1", "obj-1", "issue"));
        store.save(Checkpoint.terminal("flow-1", "obj-1", TargetState.VERIFIED));

        assertThat(store.load("flow-1", "obj-1"))
                .hasValue(new Checkpoint("flow-1", "obj-1", TargetState.VERIFIED, ""));
        assertThat(store.load("flow-1", "obj-1").orElseThrow().isTerminal()).isTrue();
    }

    @Test
    void survivesAcrossInstancesAndIsolatesFlowsAndTargets() {
        FileCheckpointStore first = new FileCheckpointStore(dir);
        first.save(Checkpoint.detected("flow-a", "obj-1", "问题甲"));
        first.save(Checkpoint.detected("flow-b", "obj-1", "问题乙"));

        FileCheckpointStore second = new FileCheckpointStore(dir);
        assertThat(second.load("flow-a", "obj-1").orElseThrow().issue()).isEqualTo("问题甲");
        assertThat(second.load("flow-b", "obj-1").orElseThrow().issue()).isEqualTo("问题乙");
        assertThat(second.load("flow-a", "obj-2")).isEmpty();
    }
}
