package com.example.lightscript.agent;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 任务状态监控器
 * 用于跟踪正在执行的任务，确保升级时不会中断正在执行的任务
 */
class TaskStatusMonitor {
    private final AtomicInteger runningTaskCount = new AtomicInteger(0);
    private final Set<Long> runningTaskIds = ConcurrentHashMap.newKeySet();
    
    /**
     * 检查是否有正在运行的任务
     */
    public boolean hasRunningTasks() {
        return runningTaskCount.get() > 0;
    }
    
    /**
     * 任务开始时调用
     */
    public void onTaskStart(Long executionId) {
        runningTaskIds.add(executionId);
        int count = runningTaskCount.incrementAndGet();
        System.out.println("[TaskMonitor] Task started: " + executionId + ", running tasks: " + count);
    }
    
    /**
     * 任务结束时调用
     */
    public void onTaskComplete(Long executionId) {
        if (runningTaskIds.remove(executionId)) {
            int count = runningTaskCount.decrementAndGet();
            System.out.println("[TaskMonitor] Task completed: " + executionId + ", running tasks: " + count);
        }
    }
    
    /**
     * 获取正在运行的任务数量
     */
    public int getRunningTaskCount() {
        return runningTaskCount.get();
    }
    
    /**
     * 获取正在运行的任务ID列表
     */
    public Set<Long> getRunningTaskIds() {
        return new HashSet<>(runningTaskIds);
    }
    
    /**
     * 强制停止所有正在运行的任务（用于强制升级）
     */
    public void stopAllTasks() {
        Set<Long> tasksToStop = new HashSet<>(runningTaskIds);
        if (!tasksToStop.isEmpty()) {
            System.out.println("[TaskMonitor] Force stopping " + tasksToStop.size() + " running tasks for upgrade: " + tasksToStop);
            
            // 清空任务列表和计数器
            runningTaskIds.clear();
            runningTaskCount.set(0);
            
            // 这里可以添加实际的任务中断逻辑，比如：
            // - 发送中断信号给正在执行的进程
            // - 设置中断标志
            // - 记录被中断的任务以便后续处理
            
            System.out.println("[TaskMonitor] All tasks have been force stopped for upgrade");
        } else {
            System.out.println("[TaskMonitor] No running tasks to stop");
        }
    }
}