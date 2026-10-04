package com.example.gsb.repair;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程安全的统计收集器，编排过程中实时累计，可随时快照。
 */
public final class StatsCollector {

    private final AtomicInteger detectedIssues = new AtomicInteger();
    private final AtomicInteger repaired = new AtomicInteger();
    private final AtomicInteger verified = new AtomicInteger();
    private final AtomicInteger rolledBack = new AtomicInteger();
    private final AtomicInteger manualRequired = new AtomicInteger();
    private final AtomicInteger skipped = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();

    void issueDetected() {
        detectedIssues.incrementAndGet();
    }

    void repaired() {
        repaired.incrementAndGet();
    }

    void verified() {
        verified.incrementAndGet();
    }

    void rolledBack() {
        rolledBack.incrementAndGet();
    }

    void manualRequired() {
        manualRequired.incrementAndGet();
    }

    void skipped() {
        skipped.incrementAndGet();
    }

    void failed() {
        failed.incrementAndGet();
    }

    public RepairStats snapshot() {
        return new RepairStats(
                detectedIssues.get(),
                repaired.get(),
                verified.get(),
                rolledBack.get(),
                manualRequired.get(),
                skipped.get(),
                failed.get());
    }
}
