package com.cgb.decp.dcepagentserver.rpc.shell.impl;

import com.cgb.decp.dcepagentserver.rpc.service.impl.DemoServiceImpl;
import com.cgb.decp.dcepagentserver.util.DcepAgentUtil;
import com.dcep.agent.rpc.shell.api.ShellExcuteService;
import com.dcep.agent.rpc.shell.dto.ShellResultDto;
import com.xxl.rpc.core.remoting.provider.annotation.XxlRpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @Classname ShellExcuteServiceImpl
 * @Description TODO
 * @Date 2020/12/26 15:17
 * @Created by hexm
 */
@XxlRpcService
@Service
public class ShellExcuteServiceImpl implements ShellExcuteService {

    private static Logger logger = LoggerFactory.getLogger(ShellExcuteServiceImpl.class);

    private static ExecutorService fixedThreadPool = Executors.newCachedThreadPool();

    @Value("${script.file.home}")
    private String scriptFileHome;

    @Value("${script.log.home}")
    private String scriptLogHome;

    @Override
    public ShellResultDto runShell(String runId, String pwd, String scriptContent) {
        String scriptFileName = scriptFileHome + File.separator + runId + ".sh";
        String command = "sh " + scriptFileName;
        if (DcepAgentUtil.isWin()) {
            scriptFileName = scriptFileHome + File.separator + runId + ".bat";
            command = scriptFileName;
        }
        String logFile = scriptLogHome + File.separator + runId + ".log";

        try {
            makeDirs(scriptFileName, logFile);
            markScriptFile(scriptFileName, scriptContent);
            // 异步执行脚本
            Future<Integer> futrue = fixedThreadPool.submit(new ShellExcuteCallable(runId, command, logFile));

            int exitValue = futrue.get(5, TimeUnit.SECONDS);

            return new ShellResultDto(runId, exitValue, "");
        } catch (TimeoutException e) {
            logger.error("runShell error", e);
            return new ShellResultDto(runId, Integer.MAX_VALUE, e.getMessage());
        } catch (Exception ex) {
            logger.error("runShell error", ex);
            return new ShellResultDto(runId, 500, ex.getMessage());
        }
    }

    /**
     * 创建父目录
     *
     * @param scriptFileName
     * @param logFile
     */
    private void makeDirs(String scriptFileName, String logFile) {
        new File(new File(scriptFileName).getParent()).mkdirs();
        new File(new File(logFile).getParent()).mkdirs();
    }

    public static void markScriptFile(String scriptFileName, String content) throws IOException {
        // make file,   filePath/gluesource/666-123456789.py
        FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream(scriptFileName);
            fileOutputStream.write(content.getBytes("UTF-8"));
            fileOutputStream.close();
        } catch (Exception e) {
            throw e;
        } finally {
            if (fileOutputStream != null) {
                fileOutputStream.close();
            }
        }
    }


}
