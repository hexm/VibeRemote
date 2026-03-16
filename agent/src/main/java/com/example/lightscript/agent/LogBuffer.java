package com.example.lightscript.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 日志缓冲区 - 收集日志并在达到条件时触发批量发送
 */
/**
 * 日志缓冲区 - 收集日志并在达到条件时触发批量发送
 * 增加缓冲区溢出保护机制
 */
public class LogBuffer {
    private final List<LogEntry> entries = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();

    private final int maxBatchSize;         // 最大批次大小 (默认1000)
    private final long maxWaitTimeMs;       // 最大等待时间 (默认5秒)
    private final int maxBufferSize;        // 最大缓冲区大小 (防止内存溢出)
    private volatile long lastFlushTime;    // 上次刷新时间
    private volatile int currentSeq = 0;    // 当前序列号
    private volatile int droppedLogCount = 0; // 丢弃的日志计数

    public LogBuffer() {
        this(1000, 5000, 10000); // 默认配置：批次1000，等待5秒，最大缓冲10000
    }

    public LogBuffer(int maxBatchSize, long maxWaitTimeMs) {
        this(maxBatchSize, maxWaitTimeMs, maxBatchSize * 10); // 最大缓冲区为批次大小的10倍
    }

    public LogBuffer(int maxBatchSize, long maxWaitTimeMs, int maxBufferSize) {
        this.maxBatchSize = maxBatchSize;
        this.maxWaitTimeMs = maxWaitTimeMs;
        this.maxBufferSize = maxBufferSize;
        this.lastFlushTime = System.currentTimeMillis();
    }

    /**
     * 添加日志条目 - 使用时间戳确保顺序
     */
    public void addLog(String stream, String data) {
        if (data == null || data.trim().isEmpty()) {
            return; // 过滤空行
        }

        lock.lock();
        try {
            // 检查缓冲区溢出
            if (entries.size() >= maxBufferSize) {
                droppedLogCount++;
                if (droppedLogCount % 100 == 1) { // 每100条丢弃日志打印一次警告
                    System.err.println("[LogBuffer] 缓冲区溢出，已丢弃 " + droppedLogCount + " 条日志");
                }
                return;
            }

            // 使用当前时间戳作为序列号的一部分，确保全局顺序
            long timestamp = System.currentTimeMillis();
            LogEntry entry = new LogEntry(++currentSeq, stream, data.trim(), timestamp);
            entries.add(entry);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 直接添加已构造的日志条目 - 用于全局排序
     */
    public void addLogEntry(LogEntry entry) {
        if (entry == null || entry.getData() == null || entry.getData().trim().isEmpty()) {
            return; // 过滤空条目
        }

        lock.lock();
        try {
            // 检查缓冲区溢出
            if (entries.size() >= maxBufferSize) {
                droppedLogCount++;
                if (droppedLogCount % 100 == 1) { // 每100条丢弃日志打印一次警告
                    System.err.println("[LogBuffer] 缓冲区溢出，已丢弃 " + droppedLogCount + " 条日志");
                }
                return;
            }

            entries.add(entry);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 检查是否应该刷新缓冲区
     */
    public boolean shouldFlush() {
        lock.lock();
        try {
            if (entries.isEmpty()) {
                return false;
            }

            // 检查批次大小
            if (entries.size() >= maxBatchSize) {
                return true;
            }

            // 检查等待时间
            long currentTime = System.currentTimeMillis();
            return (currentTime - lastFlushTime) >= maxWaitTimeMs;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 强制刷新检查 - 当缓冲区接近满时
     */
    public boolean shouldForceFlush() {
        lock.lock();
        try {
            // 当缓冲区使用率超过80%时强制刷新
            return entries.size() >= (maxBufferSize * 0.8);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 刷新缓冲区，返回当前所有日志条目
     * 注意：日志顺序已在StreamMerger中保证，这里直接返回
     */
    public List<LogEntry> flush() {
        lock.lock();
        try {
            if (entries.isEmpty()) {
                return new ArrayList<>();
            }

            List<LogEntry> result = new ArrayList<>(entries);
            entries.clear();
            lastFlushTime = System.currentTimeMillis();

            // 如果有丢弃的日志，添加统计信息
            if (droppedLogCount > 0) {
                LogEntry dropInfo = new LogEntry(++currentSeq, "system",
                    "[警告] 由于缓冲区溢出，共丢弃了 " + droppedLogCount + " 条日志",
                    System.currentTimeMillis());
                result.add(dropInfo);
                droppedLogCount = 0; // 重置计数
            }

            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 检查是否有待处理的日志
     */
    public boolean hasLogs() {
        lock.lock();
        try {
            return !entries.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取当前缓冲区大小
     */
    public int size() {
        lock.lock();
        try {
            return entries.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取缓冲区使用率
     */
    public double getUsageRatio() {
        lock.lock();
        try {
            return (double) entries.size() / maxBufferSize;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取丢弃的日志数量
     */
    public int getDroppedLogCount() {
        return droppedLogCount;
    }

    /**
     * 清空缓冲区
     */
    public void clear() {
        lock.lock();
        try {
            entries.clear();
            lastFlushTime = System.currentTimeMillis();
            droppedLogCount = 0;
        } finally {
            lock.unlock();
        }
    }
}
