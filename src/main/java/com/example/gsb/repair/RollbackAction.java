package com.example.gsb.repair;

/** 回滚动作：复核不通过时撤销修复动作产生的变更。 */
@FunctionalInterface
public interface RollbackAction {

    void rollback(RepairContext context) throws Exception;
}
