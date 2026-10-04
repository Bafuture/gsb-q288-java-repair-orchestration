package com.example.gsb.repair;

/**
 * 编排统计快照（不可变）。
 */
public record RepairStats(
        int detectedIssues,
        int repaired,
        int verified,
        int rolledBack,
        int manualRequired,
        int skipped,
        int failed) {

    public static RepairStats empty() {
        return new RepairStats(0, 0, 0, 0, 0, 0, 0);
    }

    /** 返回与另一快照逐项累加后的新快照。 */
    public RepairStats plus(RepairStats other) {
        return new RepairStats(
                detectedIssues + other.detectedIssues,
                repaired + other.repaired,
                verified + other.verified,
                rolledBack + other.rolledBack,
                manualRequired + other.manualRequired,
                skipped + other.skipped,
                failed + other.failed);
    }
}
