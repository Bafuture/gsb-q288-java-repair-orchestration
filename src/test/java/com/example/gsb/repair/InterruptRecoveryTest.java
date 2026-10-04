package com.example.gsb.repair;

import com.example.gsb.repair.store.FileProgressStore;
import com.example.gsb.repair.store.StatusRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 需求 6：流程中断后重启，从已完成步骤继续，不重复执行已成功步骤。 */
class InterruptRecoveryTest {

    @TempDir
    Path tempDir;

    @Test
    void crashDuringRepairResumesWithoutReexecutingSucceededSteps() {
        Spy spy = new Spy();
        spy.put("obj-a", "bad");
        spy.put("obj-b", "bad");
        Path progressFile = tempDir.resolve("progress.properties");
        AtomicBoolean crashed = new AtomicBoolean(false);

        // obj-b 的修复在“副作用已发生、状态尚未落盘”时模拟进程被杀。
        RepairRule rule = RepairRule.builder(TestRules.ISSUE_TYPE)
                .action(context -> {
                    spy.guardedRepair(context.objectId());
                    if ("obj-b".equals(context.objectId()) && crashed.compareAndSet(false, true)) {
                        throw new OrchestrationAbortException("simulated crash after repair");
                    }
                })
                .recheck(context -> {
                    spy.recheckInvocations.incrementAndGet();
                    return !spy.isBad(context.objectId());
                })
                .rollback(context -> spy.restoreBad(context.objectId()))
                .build();

        Orchestrator first = Orchestrator.builder(TestRules.detectionStep(spy))
                .addRule(rule)
                .progressStore(new FileProgressStore(progressFile))
                .build();
        assertThatThrownBy(() -> first.run(List.of("obj-a", "obj-b")))
                .isInstanceOf(OrchestrationAbortException.class);

        // 崩溃现场：obj-a 已走完闭环；obj-b 修复副作用已发生但状态未标记。
        assertThat(spy.values.get("obj-a")).isEqualTo("good");
        assertThat(spy.values.get("obj-b")).isEqualTo("good");
        assertThat(new FileProgressStore(progressFile).status(
                new Issue("obj-a", TestRules.ISSUE_TYPE, "expected=good").key()))
                .contains(StatusRecord.of(IssueStatus.VERIFIED));

        // 重启：新编排器 + 重新加载的进度文件，从断点继续。
        Orchestrator second = Orchestrator.builder(TestRules.detectionStep(spy))
                .addRule(rule)
                .progressStore(new FileProgressStore(progressFile))
                .build();
        OrchestrationReport report = second.run(List.of("obj-a", "obj-b"));

        // 检测不重复执行
        assertThat(spy.detectInvocations.get()).isEqualTo(2);
        // obj-b 的修复被重新执行一次，但幂等守卫保证副作用只发生一次
        assertThat(spy.repairInvocations.get()).isEqualTo(3);
        assertThat(spy.repairMutations.get()).isEqualTo(2);
        // obj-a 的复核不重复执行，obj-b 复核一次
        assertThat(spy.recheckInvocations.get()).isEqualTo(2);
        assertThat(report.allIssues()).hasSize(2)
                .allSatisfy(issue -> assertThat(issue.status()).isEqualTo(IssueStatus.VERIFIED));
        assertThat(report.stats().repairsSucceeded()).isEqualTo(1);
        assertThat(report.stats().verificationsPassed()).isEqualTo(2);
    }

    @Test
    void crashDuringRecheckResumesAtRecheckWithoutReRepair() {
        Spy spy = new Spy();
        spy.put("obj-a", "bad");
        Path progressFile = tempDir.resolve("progress.properties");
        AtomicBoolean crashed = new AtomicBoolean(false);

        RepairRule rule = RepairRule.builder(TestRules.ISSUE_TYPE)
                .action(context -> spy.guardedRepair(context.objectId()))
                .recheck(context -> {
                    spy.recheckInvocations.incrementAndGet();
                    if (crashed.compareAndSet(false, true)) {
                        throw new OrchestrationAbortException("simulated crash during recheck");
                    }
                    return !spy.isBad(context.objectId());
                })
                .rollback(context -> spy.restoreBad(context.objectId()))
                .build();

        Orchestrator first = Orchestrator.builder(TestRules.detectionStep(spy))
                .addRule(rule)
                .progressStore(new FileProgressStore(progressFile))
                .build();
        assertThatThrownBy(() -> first.run(List.of("obj-a")))
                .isInstanceOf(OrchestrationAbortException.class);

        // 崩溃现场：修复已成功落盘（REPAIRED），复核未完成。
        assertThat(new FileProgressStore(progressFile).status(
                new Issue("obj-a", TestRules.ISSUE_TYPE, "expected=good").key()))
                .contains(StatusRecord.of(IssueStatus.REPAIRED));

        Orchestrator second = Orchestrator.builder(TestRules.detectionStep(spy))
                .addRule(rule)
                .progressStore(new FileProgressStore(progressFile))
                .build();
        OrchestrationReport report = second.run(List.of("obj-a"));

        // 修复不重复执行，仅从复核继续
        assertThat(spy.repairInvocations.get()).isEqualTo(1);
        assertThat(spy.detectInvocations.get()).isEqualTo(1);
        assertThat(spy.recheckInvocations.get()).isEqualTo(2);
        assertThat(report.allIssues().get(0).status()).isEqualTo(IssueStatus.VERIFIED);
        assertThat(report.stats().repairsSucceeded()).isZero();
        assertThat(report.stats().verificationsPassed()).isEqualTo(1);
    }

    @Test
    void progressFileSurvivesStoreRecreationAndSpecialCharacters() {
        Path progressFile = tempDir.resolve("nested").resolve("progress.properties");
        Issue tricky = new Issue("obj:1/特殊 对象", "type|x", "detail,with,commas");

        FileProgressStore first = new FileProgressStore(progressFile);
        first.markObjectDetected(tricky.objectId(), List.of(tricky.key()));
        first.markObjectDetected("clean-object", List.of());
        first.putStatus(tricky.key(), StatusRecord.of(IssueStatus.SKIPPED_PRECONDITION, "原因：需人工确认"));

        FileProgressStore reloaded = new FileProgressStore(progressFile);
        assertThat(reloaded.detectedIssueKeys(tricky.objectId())).contains(List.of(tricky.key()));
        assertThat(reloaded.detectedIssueKeys("clean-object")).contains(List.of());
        assertThat(reloaded.detectedIssueKeys("unknown")).isEmpty();
        assertThat(reloaded.status(tricky.key()))
                .contains(StatusRecord.of(IssueStatus.SKIPPED_PRECONDITION, "原因：需人工确认"));
        assertThat(reloaded.allIssueKeys()).containsExactly(tricky.key());
    }
}
