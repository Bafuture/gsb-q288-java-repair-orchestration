package com.example.gsb.repair;

/**
 * 致命异常：步骤抛出该异常时整个编排运行立即中止，
 * 已完成的步骤检查点已持久化，重启后可从断点继续。
 *
 * <p>用于模拟进程崩溃 / 强制中断场景。
 */
public class FatalStepException extends RuntimeException {

    public FatalStepException(String message) {
        super(message);
    }
}
