package com.example.gsb.repair;

/**
 * 检测步骤：检查目标对象是否一致。
 *
 * @param <T> 目标对象类型
 */
@FunctionalInterface
public interface DetectStep<T> {

    /**
     * @return 检测结果，{@link DetectResult#consistent()} 表示无需修复
     */
    DetectResult detect(T target) throws Exception;
}
