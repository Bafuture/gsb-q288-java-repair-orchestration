# 一致性校验与修复编排闭环

Pair-wise GSB 标注任务仓库（第 17 批 / 288）。

| 项目 | 内容 |
|------|------|
| 任务类型 | Feature 迭代 |
| 任务难度 | 困难 |
| 语言/框架 | Java, Maven, JUnit 5 |
| 环境可复现等级 | 无外部依赖 |
| 构建方式 | Maven（含 mvnw wrapper，无需本机安装 Maven） |

> 本仓库是**初始环境快照**：只有工程骨架，不含任何实现代码。
> 分支说明：`main` 为初始环境；`A`、`B` 为两次独立执行各自的工作分支，均从 `main` 的同一个提交拉出。

## 运行方式

```bash
./mvnw -q verify
```

## 任务提示词

以下为本题完整的 User Prompt 原文，两次执行必须使用完全相同的文本。

我们发现问题后修复流程是手工的，容易漏步骤或修错对象，希望形成检测到复核的闭环。请从零实现一个一致性校验与修复编排组件。仓库目前只有一个空的 Maven 工程（pom.xml 只声明 JUnit 5 与 AssertJ）。要求：1) 支持定义检测步骤与修复步骤，形成"检测→决策→修复→复核"的闭环流程；2) 支持修复前置条件：检测结果不满足条件时不得执行修复，需给出跳过原因；3) 支持幂等修复：同一问题重复修复不得产生副作用，需有测试证明；4) 支持修复后复核：复核不通过时必须回滚修复或标记为待人工处理，规则可配置；5) 支持并发修复：不同对象可并行修复，同一对象必须串行，需控制并发度；6) 支持中断恢复：流程中途中断后重启要能从已完成的步骤继续，不重复执行已成功步骤；7) 提供统计：检测问题数、修复成功数、复核通过数、回滚数与人工介入数；8) 测试覆盖闭环流程、前置条件、幂等修复、复核与回滚、并发控制与中断恢复；`mvn -q verify` 一条命令跑通。

## 提交要求

1. 在本仓库中完成提示词要求的全部内容。
2. `./mvnw -q verify` 必须通过。
3. 完成后在所属分支（A 或 B）上提交，产物快照的父提交必须是初始环境快照。

## 实现说明（分支 A）

`com.example.gsb.repair` 包提供一致性校验与修复编排组件，零外部依赖，闭环流程：

```
检测 DetectionStep → 决策（RepairRule 匹配 + RepairPrecondition 前置条件）
                  → 修复 RepairAction → 复核 RecheckAction
                                          ├─通过：VERIFIED
                                          └─不通过：ROLLBACK（回滚）/ MANUAL（转人工，可配置）
```

| 需求 | 实现位置 |
|------|----------|
| 1 闭环流程 | `Orchestrator`：检测 → 决策 → 修复 → 复核，状态机见 `IssueStatus` |
| 2 前置条件 | `RepairPrecondition`/`PreconditionResult`：不满足则 `SKIPPED_PRECONDITION` 并记录跳过原因 |
| 3 幂等修复 | 修复动作须自带幂等守卫（见 `Spy.guardedRepair`）；编排层保证已成功步骤不重复执行，`IdempotentRepairTest` 证明 |
| 4 复核与回滚 | `RecheckAction` + `RecheckFailurePolicy`（规则级可覆盖全局默认）；回滚缺失时兜底转人工 |
| 5 并发控制 | `StripedExecutor`：固定线程池限并发度，对象 id 条带锁保证同对象串行、不同对象并行 |
| 6 中断恢复 | `ProgressStore`：每步完成即落盘（`FileProgressStore` 原子写 properties），重启后终态跳过、`REPAIRED` 从复核继续 |
| 7 统计 | `RepairStats`：检测问题数 / 修复成功数 / 复核通过数 / 回滚数 / 人工介入数（另含前置条件跳过数） |

最小用法：

```java
Orchestrator orchestrator = Orchestrator.builder(detectionStep)
        .addRule(RepairRule.builder("drift")
                .precondition(issue -> PreconditionResult.ok())
                .action(ctx -> repair(ctx))
                .recheck(ctx -> verify(ctx))
                .rollback(ctx -> undo(ctx))
                .failurePolicy(RecheckFailurePolicy.ROLLBACK)
                .build())
        .progressStore(new FileProgressStore(Path.of("progress.properties")))
        .parallelism(4)
        .build();
OrchestrationReport report = orchestrator.run(List.of("obj-1", "obj-2"));
report.stats(); // 检测/修复/复核/回滚/人工计数
```

测试 17 个，覆盖：闭环（`ClosedLoopTest`）、前置条件（`PreconditionTest`）、
幂等修复（`IdempotentRepairTest`）、复核回滚（`RecheckRollbackTest`）、
并发控制（`ConcurrencyControlTest`）、中断恢复（`InterruptRecoveryTest`）。
