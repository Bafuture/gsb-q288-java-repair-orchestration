package com.example.gsb.repair;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * 文件检查点存储：每个目标一个文件，原子写入，
 * 进程崩溃重启后仍可恢复进度。
 */
public final class FileCheckpointStore implements CheckpointStore {

    private final Path directory;

    public FileCheckpointStore(Path directory) {
        this.directory = directory;
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException("无法创建检查点目录: " + directory, e);
        }
    }

    @Override
    public synchronized void save(Checkpoint checkpoint) {
        String line = checkpoint.state().name() + ":" + encode(checkpoint.issue());
        Path file = fileFor(checkpoint.flowName(), checkpoint.targetId());
        try {
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, line, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("写入检查点失败: " + file, e);
        }
    }

    @Override
    public synchronized Optional<Checkpoint> load(String flowName, String targetId) {
        Path file = fileFor(flowName, targetId);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                return Optional.empty();
            }
            String line = lines.get(0);
            int sep = line.indexOf(':');
            TargetState state = TargetState.valueOf(line.substring(0, sep));
            String issue = decode(line.substring(sep + 1));
            return Optional.of(new Checkpoint(flowName, targetId, state, issue));
        } catch (IOException e) {
            throw new UncheckedIOException("读取检查点失败: " + file, e);
        }
    }

    private Path fileFor(String flowName, String targetId) {
        return directory.resolve(encode(flowName) + "__" + encode(targetId) + ".chk");
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        if (value.isEmpty()) {
            return "";
        }
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
