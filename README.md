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

## 组件说明

实现位于 `src/main/java/com/example/gsb/repair/`，核心概念：

| 类型 | 职责 |
|------|------|
| `RepairFlow<T>` | 一个"检测→决策→修复→复核"流程定义（Builder 组装各步骤） |
| `DetectStep` / `RepairStep` / `VerifyStep` / `RollbackStep` | 四个阶段步骤接口 |
| `Precondition` | 修复前置条件，不满足时跳过并记录原因 |
| `VerifyFailurePolicy` | 复核不通过策略：`ROLLBACK`（默认）或 `MARK_FOR_MANUAL` |
| `RepairOrchestrator` | 编排器：并发执行、按目标串行、断点续跑、统计汇总 |
| `CheckpointStore` | 检查点存储（`InMemoryCheckpointStore` / `FileCheckpointStore`） |
| `RepairStats` | 统计：检测问题数、修复数、复核通过数、回滚数、人工介入数等 |

### 使用示例

```java
RepairFlow<Item> flow = RepairFlow.builder(Item.class, "config-sync", item -> item.id)
        .detect(item -> item.isConsistent()
                ? DetectResult.ok()
                : DetectResult.problem("值不一致"))
        .precondition((item, ctx) -> item.locked
                ? PreconditionResult.rejected("对象已锁定，禁止自动修复")
                : PreconditionResult.ok())
        .repair((item, ctx) -> item.applyExpected())   // 实现需幂等
        .verify((item, ctx) -> item.isConsistent())
        .rollback((item, ctx) -> item.restoreBackup())
        .verifyFailurePolicy(VerifyFailurePolicy.ROLLBACK) // 或 MARK_FOR_MANUAL
        .build();

RepairOrchestrator orchestrator =
        new RepairOrchestrator(new FileCheckpointStore(Path.of("checkpoints")), 8);
OrchestrationResult result = orchestrator.run(flow, items);
RepairStats stats = result.stats(); // detectedIssues/repaired/verified/rolledBack/manualRequired...
```

行为约定：

- 每个成功步骤完成后写入检查点；中断（步骤抛出 `FatalStepException`）后重跑 `run`
  即可从断点继续，已成功步骤不会重复执行，终态目标整体跳过。
- 不同目标并行修复（受 `maxConcurrency` 限制），同一目标经 `KeyedLock` 串行。
- 复核不通过时按策略回滚或标记待人工；前置条件不满足时跳过修复并给出原因。

## 任务提示词

以下为本题完整的 User Prompt 原文，两次执行必须使用完全相同的文本。

我们发现问题后修复流程是手工的，容易漏步骤或修错对象，希望形成检测到复核的闭环。请从零实现一个一致性校验与修复编排组件。仓库目前只有一个空的 Maven 工程（pom.xml 只声明 JUnit 5 与 AssertJ）。要求：1) 支持定义检测步骤与修复步骤，形成"检测→决策→修复→复核"的闭环流程；2) 支持修复前置条件：检测结果不满足条件时不得执行修复，需给出跳过原因；3) 支持幂等修复：同一问题重复修复不得产生副作用，需有测试证明；4) 支持修复后复核：复核不通过时必须回滚修复或标记为待人工处理，规则可配置；5) 支持并发修复：不同对象可并行修复，同一对象必须串行，需控制并发度；6) 支持中断恢复：流程中途中断后重启要能从已完成的步骤继续，不重复执行已成功步骤；7) 提供统计：检测问题数、修复成功数、复核通过数、回滚数与人工介入数；8) 测试覆盖闭环流程、前置条件、幂等修复、复核与回滚、并发控制与中断恢复；`mvn -q verify` 一条命令跑通。

## 提交要求

1. 在本仓库中完成提示词要求的全部内容。
2. `./mvnw -q verify` 必须通过。
3. 完成后在所属分支（A 或 B）上提交，产物快照的父提交必须是初始环境快照。
