package com.example.gsb.repair;

/**
 * 由步骤实现抛出的“致命中断”信号，模拟流程中途被强杀（JVM 退出）：
 * 当前执行立即终止，但已持久化的步骤进度保留，下次运行从断点继续。
 */
public class OrchestrationAbortException extends RuntimeException {

    public OrchestrationAbortException(String message) {
        super(message);
    }
}
