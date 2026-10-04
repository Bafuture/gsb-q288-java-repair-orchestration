package com.example.gsb.repair;

/** 复核动作：修复完成后再次校验，返回 true 表示复核通过。 */
@FunctionalInterface
public interface RecheckAction {

    boolean recheck(RepairContext context) throws Exception;
}
