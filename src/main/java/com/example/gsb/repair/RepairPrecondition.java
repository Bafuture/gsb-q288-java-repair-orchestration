package com.example.gsb.repair;

/** 修复前置条件：修复执行前评估，不满足时跳过修复并给出原因。 */
@FunctionalInterface
public interface RepairPrecondition {

    PreconditionResult evaluate(Issue issue);
}
