package com.example.gsb.repair;

/** 修复动作：对目标对象执行修复。实现应保证幂等（重复调用不产生额外副作用）。 */
@FunctionalInterface
public interface RepairAction {

    void repair(RepairContext context) throws Exception;
}
