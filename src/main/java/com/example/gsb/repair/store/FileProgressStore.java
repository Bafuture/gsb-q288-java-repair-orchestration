package com.example.gsb.repair.store;

import com.example.gsb.repair.IssueStatus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * 基于 Properties 文件的进度存储，支撑跨进程（重启）中断恢复。
 *
 * <p>每次写入都整体落盘并原子替换，保证已成功步骤的状态在进程被杀掉后仍可见。
 * key/value 经 Base64 编码，对对象 id、问题描述中的特殊字符透明。
 * 所有方法同步，避免并发写入相互覆盖。
 */
public final class FileProgressStore implements ProgressStore {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final String DETECTED_PREFIX = "detected.";
    private static final String STATUS_PREFIX = "status.";
    private static final String NOTE_PREFIX = "note.";
    private static final String EMPTY_MARKER = "__empty__";

    private final Path file;
    private final Map<String, List<String>> detected = new LinkedHashMap<>();
    private final Map<String, StatusRecord> statuses = new LinkedHashMap<>();

    public FileProgressStore(Path file) {
        this.file = file;
        load();
    }

    private synchronized void load() {
        if (!Files.exists(file)) {
            return;
        }
        Properties props = new Properties();
        try (var in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            props.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to load progress from " + file, e);
        }
        for (String rawKey : props.stringPropertyNames()) {
            if (rawKey.startsWith(DETECTED_PREFIX)) {
                String objectId = decode(rawKey.substring(DETECTED_PREFIX.length()));
                String encodedList = props.getProperty(rawKey);
                detected.put(objectId, decodeIssueKeys(encodedList));
            } else if (rawKey.startsWith(STATUS_PREFIX)) {
                String issueKey = decode(rawKey.substring(STATUS_PREFIX.length()));
                IssueStatus status = IssueStatus.valueOf(props.getProperty(rawKey));
                String noteEncoded = props.getProperty(NOTE_PREFIX + rawKey.substring(STATUS_PREFIX.length()), "");
                statuses.put(issueKey, new StatusRecord(status, noteEncoded.isEmpty() ? "" : decode(noteEncoded)));
            }
        }
    }

    @Override
    public synchronized void markObjectDetected(String objectId, List<String> issueKeys) {
        detected.put(objectId, List.copyOf(issueKeys));
        persist();
    }

    @Override
    public synchronized Optional<List<String>> detectedIssueKeys(String objectId) {
        List<String> keys = detected.get(objectId);
        return keys == null ? Optional.empty() : Optional.of(List.copyOf(keys));
    }

    @Override
    public synchronized void putStatus(String issueKey, StatusRecord record) {
        statuses.put(issueKey, record);
        persist();
    }

    @Override
    public synchronized Optional<StatusRecord> status(String issueKey) {
        return Optional.ofNullable(statuses.get(issueKey));
    }

    @Override
    public synchronized List<String> allIssueKeys() {
        return List.copyOf(statuses.keySet());
    }

    private synchronized void persist() {
        Properties props = new Properties();
        detected.forEach((objectId, keys) -> {
            StringBuilder joined = new StringBuilder();
            for (String key : keys) {
                if (!joined.isEmpty()) {
                    joined.append(',');
                }
                joined.append(encode(key));
            }
            if (joined.isEmpty()) {
                joined.append(EMPTY_MARKER);
            }
            props.setProperty(DETECTED_PREFIX + encode(objectId), joined.toString());
        });
        statuses.forEach((issueKey, record) -> {
            String encodedKey = encode(issueKey);
            props.setProperty(STATUS_PREFIX + encodedKey, record.status().name());
            if (!record.note().isEmpty()) {
                props.setProperty(NOTE_PREFIX + encodedKey, encode(record.note()));
            }
        });
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (var out = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                props.store(out, "repair-orchestration progress");
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailure) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to persist progress to " + file, e);
        }
    }

    private static List<String> decodeIssueKeys(String encodedList) {
        if (EMPTY_MARKER.equals(encodedList)) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        for (String encoded : encodedList.split(",", -1)) {
            keys.add(decode(encoded));
        }
        return List.copyOf(keys);
    }

    private static String encode(String value) {
        return ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(DECODER.decode(value), StandardCharsets.UTF_8);
    }
}
