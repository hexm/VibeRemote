package com.example.lightscript.agent;

/**
 * 日志条目 - 批量传输的基本单元
 */
public class LogEntry {
    private int seq;                    // 序列号
    private String stream;              // 流类型 (stdout/stderr/system)
    private String data;                // 日志内容
    private long timestamp;             // 时间戳

    public LogEntry() {}

    public LogEntry(int seq, String stream, String data) {
        this.seq = seq;
        this.stream = stream;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    public LogEntry(int seq, String stream, String data, long timestamp) {
        this.seq = seq;
        this.stream = stream;
        this.data = data;
        this.timestamp = timestamp;
    }

    // Getters and Setters
    public int getSeq() { return seq; }
    public void setSeq(int seq) { this.seq = seq; }

    public String getStream() { return stream; }
    public void setStream(String stream) { this.stream = stream; }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return String.format("LogEntry{seq=%d, stream='%s', data='%s', timestamp=%d}", 
                           seq, stream, data, timestamp);
    }
}