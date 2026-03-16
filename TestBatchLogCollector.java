import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 批量日志收集器的简单测试
 */
public class TestBatchLogCollector {
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== 批量日志收集器测试开始 ===");
        
        // 测试1: LogBuffer基本功能
        testLogBuffer();
        
        // 测试2: 批量触发机制
        testBatchTrigger();
        
        System.out.println("=== 所有测试通过 ===");
    }
    
    private static void testLogBuffer() throws Exception {
        System.out.println("测试1: LogBuffer基本功能");
        
        // 创建小容量的缓冲区便于测试
        LogBuffer buffer = new LogBuffer(5, 1000); // 5条或1秒
        
        // 添加日志
        buffer.addLog("stdout", "第1行日志");
        buffer.addLog("stdout", "第2行日志");
        buffer.addLog("stderr", "第3行错误日志");
        
        System.out.println("缓冲区大小: " + buffer.size());
        assert buffer.size() == 3 : "缓冲区大小应该为3";
        
        // 测试空行过滤
        buffer.addLog("stdout", "");
        buffer.addLog("stdout", "   ");
        System.out.println("添加空行后缓冲区大小: " + buffer.size());
        assert buffer.size() == 3 : "空行应该被过滤";
        
        // 测试刷新
        List<LogEntry> logs = buffer.flush();
        System.out.println("刷新后获得日志条数: " + logs.size());
        assert logs.size() == 3 : "应该获得3条日志";
        assert buffer.size() == 0 : "刷新后缓冲区应该为空";
        
        // 验证日志内容
        assert "第1行日志".equals(logs.get(0).getData()) : "第一条日志内容不正确";
        assert "stdout".equals(logs.get(0).getStream()) : "第一条日志流类型不正确";
        assert "stderr".equals(logs.get(2).getStream()) : "第三条日志流类型不正确";
        
        System.out.println("✓ LogBuffer基本功能测试通过");
    }
    
    private static void testBatchTrigger() throws Exception {
        System.out.println("测试2: 批量触发机制");
        
        LogBuffer buffer = new LogBuffer(3, 2000); // 3条或2秒
        
        // 测试大小触发
        buffer.addLog("stdout", "日志1");
        buffer.addLog("stdout", "日志2");
        assert !buffer.shouldFlush() : "未达到批次大小时不应触发";
        
        buffer.addLog("stdout", "日志3");
        assert buffer.shouldFlush() : "达到批次大小时应该触发";
        
        buffer.flush();
        
        // 测试时间触发
        buffer.addLog("stdout", "延时日志1");
        assert !buffer.shouldFlush() : "刚添加时不应触发";
        
        // 等待超过超时时间
        Thread.sleep(2100);
        assert buffer.shouldFlush() : "超时后应该触发";
        
        System.out.println("✓ 批量触发机制测试通过");
    }
}

// 简化的LogEntry和LogBuffer类用于测试
class LogEntry {
    private int seq;
    private String stream;
    private String data;
    private long timestamp;

    public LogEntry(int seq, String stream, String data) {
        this.seq = seq;
        this.stream = stream;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    public int getSeq() { return seq; }
    public String getStream() { return stream; }
    public String getData() { return data; }
    public long getTimestamp() { return timestamp; }
}

class LogBuffer {
    private final java.util.List<LogEntry> entries = new java.util.ArrayList<>();
    private final int maxBatchSize;
    private final long maxWaitTimeMs;
    private volatile long lastFlushTime;
    private volatile int currentSeq = 0;

    public LogBuffer(int maxBatchSize, long maxWaitTimeMs) {
        this.maxBatchSize = maxBatchSize;
        this.maxWaitTimeMs = maxWaitTimeMs;
        this.lastFlushTime = System.currentTimeMillis();
    }

    public synchronized void addLog(String stream, String data) {
        if (data == null || data.trim().isEmpty()) {
            return;
        }
        LogEntry entry = new LogEntry(++currentSeq, stream, data.trim());
        entries.add(entry);
    }

    public synchronized boolean shouldFlush() {
        if (entries.isEmpty()) {
            return false;
        }
        if (entries.size() >= maxBatchSize) {
            return true;
        }
        long currentTime = System.currentTimeMillis();
        return (currentTime - lastFlushTime) >= maxWaitTimeMs;
    }

    public synchronized java.util.List<LogEntry> flush() {
        if (entries.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        java.util.List<LogEntry> result = new java.util.ArrayList<>(entries);
        entries.clear();
        lastFlushTime = System.currentTimeMillis();
        return result;
    }

    public synchronized int size() {
        return entries.size();
    }
}