package com.cgb.decp.dcepagentserver.rpc.shell.impl;

import lombok.extern.slf4j.Slf4j;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Callable;

/**
 * @Classname ShellExcuteCallable
 * @Description TODO
 * @Date 2020/12/31 21:00
 * @Created by hexm
 */
@Slf4j
public class ShellExcuteCallable implements Callable<Integer> {

    private String runId;
    private String scriptContent;
    private String logFile;

    public ShellExcuteCallable(String runId, String scriptContent, String logFile) {
        this.runId = runId;
        this.scriptContent = scriptContent;
        this.logFile = logFile;
    }

    /**
     * Computes a result, or throws an exception if unable to do so.
     *
     * @return computed result
     * @throws Exception if unable to compute a result
     */
    @Override
    public Integer call() throws Exception {
        FileOutputStream fileOutputStream = null;
        Thread inputThread = null;
        Thread errThread = null;
        Process process = null;
        try {
            // file
            fileOutputStream = new FileOutputStream(logFile, true);
            // process-exec
            process = Runtime.getRuntime().exec(scriptContent);
            // log-thread
            final FileOutputStream finalFileOutputStream = fileOutputStream;
            Process finalProcess = process;
            Runnable inputLog = new Runnable() {
                @Override
                public void run() {
                    try {
                        copy(finalProcess.getInputStream(), finalFileOutputStream, new byte[1024]);
                    } catch (IOException e) {
                        log.error("", e);
                    }
                }
            };
            Runnable errLog = new Runnable() {
                @Override
                public void run() {
                    try {
                        copy(finalProcess.getErrorStream(), finalFileOutputStream, new byte[1024]);
                    } catch (IOException e) {
                        log.error("", e);
                    }
                }
            };
            inputThread = new Thread(inputLog);
            errThread = new Thread(errLog);
            inputThread.start();
            errThread.start();

            // process-wait
            int exitValue = process.waitFor();      // exit code: 0=success, 1=error
            log.info("{} run end. exitValue: {}", runId,exitValue);
            inputThread.join();
            errThread.join();

            return exitValue;
        } catch (Exception ex) {
            log.error("", ex);
            return 500;
        } finally {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    log.error("", e);
                }
            }
            if (inputThread != null && inputThread.isAlive()) {
                inputThread.interrupt();
            }
            if (errThread != null && errThread.isAlive()) {
                errThread.interrupt();
            }
            //后台清理proc对象 避免阻塞
            try {
                //释放进程
                if (process != null) {
                    process.destroy();
                }
            } catch (Exception e) {
                log.info("close process IOException" + e.getMessage());
            }
        }
    }

    /**
     * 数据流Copy（Input自动关闭，Output不处理）
     *
     * @param inputStream
     * @param outputStream
     * @param buffer
     * @return
     * @throws IOException
     */
    private static long copy(InputStream inputStream, OutputStream outputStream, byte[] buffer) throws IOException {
        try {
            long total = 0;
            for (; ; ) {
                int res = inputStream.read(buffer);
                if (res == -1) {
                    break;
                }
                if (res > 0) {
                    total += res;
                    if (outputStream != null) {
                        outputStream.write(buffer, 0, res);
                    }
                }
            }
            outputStream.flush();
            //out = null;
            inputStream.close();
            inputStream = null;
            return total;
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }
}
