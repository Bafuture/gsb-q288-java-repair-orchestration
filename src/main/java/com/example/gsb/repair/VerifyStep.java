package com.example.gsb.repair;

/**
 * 复核步骤：修复完成后重新校验目标是否已恢复一致。
 *
 * @param <T> 目标对象类型
 */
@FunctionalInterface
public interface VerifyStep<T> {

    /**
     * @return true 表示复核通过
     */
    boolean verify(T target, RepairContext context) throws Exception;
}
