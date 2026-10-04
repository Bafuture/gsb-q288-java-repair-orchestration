package com.example.gsb.repair;

/**
 * 修复步骤：对检测出不一致的目标执行修复。
 *
 * <p>实现必须是幂等的：对同一问题重复执行不得产生额外副作用，
 * 因为中断恢复或复核失败重试时同一修复可能被再次触发。
 *
 * @param <T> 目标对象类型
 */
@FunctionalInterface
public interface RepairStep<T> {

    void repair(T target, RepairContext context) throws Exception;
}
