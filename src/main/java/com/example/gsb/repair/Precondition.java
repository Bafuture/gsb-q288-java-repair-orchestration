package com.example.gsb.repair;

/**
 * 修复前置条件：检测结果不满足条件时修复被跳过，并记录跳过原因。
 *
 * @param <T> 目标对象类型
 */
@FunctionalInterface
public interface Precondition<T> {

    /**
     * @return {@link PreconditionResult#satisfied()} 表示允许修复，
     *         否则返回 {@link PreconditionResult#rejected(String)} 并给出跳过原因
     */
    PreconditionResult evaluate(T target, RepairContext context) throws Exception;
}
