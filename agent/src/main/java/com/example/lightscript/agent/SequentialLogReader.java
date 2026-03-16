package com.example.lightscript.agent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * 顺序日志读取器 - 最简单的方案
 * 使用ProcessBuilder.redirectErrorStream(true)将stderr重定向到stdout
 * 这样就只需要读取一个流，天然保证顺序！
 */
public class SequentialLogReader {
    
    public interface LogConsumer {
        void accept(String stream, String data);
    }
    
    /**
     * 方案1: 合并流读取（推荐）
     * 使用redirectErrorStream(true)将stderr合并到stdout
     */
    public static void readMergedLogs(Process process, String charset, LogConsumer consumer) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), charset))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    // 由于流已合并，我们无法区分stdout/stderr，统一标记为stdout
                    // 或者可以通过日志内容的特征来判断（如错误关键词）
                    consumer.accept("stdout", line);
                }
            }
        }
    }
    
    /**
     * 方案2: 轮询读取（如果需要区分stdout/stderr）
     * 单线程轮询两个流，保证读取顺序
     */
    public static void readSeparateLogs(Process process, String charset, LogConsumer consumer) throws IOException {
        try (BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream(), charset));
             BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), charset))) {
            
            boolean stdoutEOF = false;
            boolean stderrEOF = false;
            
            while (!stdoutEOF || !stderrEOF) {
                // 先检查stdout
                if (!stdoutEOF && stdoutReader.ready()) {
                    String line = stdoutReader.readLine();
                    if (line != null) {
                        if (!line.trim().isEmpty()) {
                            consumer.accept("stdout", line);
                        }
                    } else {
                        stdoutEOF = true;
                    }
                }
                
                // 再检查stderr
                if (!stderrEOF && stderrReader.ready()) {
                    String line = stderrReader.readLine();
                    if (line != null) {
                        if (!line.trim().isEmpty()) {
                            consumer.accept("stderr", line);
                        }
                    } else {
                        stderrEOF = true;
                    }
                }
                
                // 如果都没有数据可读，短暂休眠
                if ((!stdoutEOF && !stdoutReader.ready()) && (!stderrEOF && !stderrReader.ready())) {
                    try {
                        Thread.sleep(1); // 1ms
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }
}