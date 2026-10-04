package com.example.gsb.repair;

/**
 * 回滚步骤：复核不通过且策略为回滚时，撤销修复产生的变更。
 *
 * @param <T> 目标对象类型
 */
@FunctionalInterface
public interface RollbackStep<T> {

    void rollback(T target, RepairContext context) throws Exception;
}
