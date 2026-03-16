package com.example.lightscript.agent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 流合并器 - 将stdout和stderr按生成顺序合并为单一流
 * 确保日志的严格顺序性
 */
public class StreamMerger {
    
    /**
     * 日志条目，包含生成顺序信息
     */
    public static class OrderedLogEntry {
        private final long order;           // 生成顺序号
        private final String stream;        // 流类型
        private final String data;          // 日志内容
        private final long timestamp;       // 时间戳
        
        public OrderedLogEntry(long order, String stream, String data, long timestamp) {
            this.order = order;
            this.stream = stream;
            this.data = data;
            this.timestamp = timestamp;
        }
        
        public long getOrder() { return order; }
        public String getStream() { return stream; }
        public String getData() { return data; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * 日志消费者接口
     */
    public interface LogConsumer {
        void accept(String stream, String data);
    }
    
    private final BlockingQueue<OrderedLogEntry> logQueue = new LinkedBlockingQueue<>();
    private final AtomicLong orderCounter = new AtomicLong(0);
    private volatile boolean shutdown = false;
    private Thread mergerThread;
    
    /**
     * 启动流合并器
     */
    public void start(LogConsumer consumer) {
        mergerThread = new Thread(() -> {
            try {
                while (!shutdown || !logQueue.isEmpty()) {
                    OrderedLogEntry entry = logQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (entry != null) {
                        consumer.accept(entry.getStream(), entry.getData());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "StreamMerger");
        
        mergerThread.setDaemon(true);
        mergerThread.start();
    }
    
    /**
     * 创建流读取器
     */
    public Thread createStreamReader(InputStream inputStream, String streamType, String charset) {
        return new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, charset))) {
                String line;
                while ((line = reader.readLine()) != null && !shutdown) {
                    if (line.trim().isEmpty()) {
                        continue; // 过滤空行
                    }
                    
                    // 生成严格递增的顺序号
                    long order = orderCounter.incrementAndGet();
                    long timestamp = System.currentTimeMillis();
                    
                    // 添加到队列，保证顺序
                    OrderedLogEntry entry = new OrderedLogEntry(order, streamType, line, timestamp);
                    try {
                        logQueue.put(entry);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (IOException e) {
                System.err.println("Error reading " + streamType + ": " + e.getMessage());
            }
        }, "StreamReader-" + streamType);
    }
    
    /**
     * 关闭流合并器
     */
    public void shutdown() {
        shutdown = true;
        if (mergerThread != null) {
            try {
                mergerThread.join(5000); // 等待5秒
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}