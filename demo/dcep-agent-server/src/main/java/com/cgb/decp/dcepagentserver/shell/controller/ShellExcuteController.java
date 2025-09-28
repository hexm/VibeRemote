package com.cgb.decp.dcepagentserver.shell.controller;

import com.cgb.decp.dcepagentserver.shell.vo.ShellResultVo;
import com.dcep.agent.rpc.shell.api.ShellExcuteService;
import com.dcep.agent.rpc.shell.dto.ShellResultDto;
import com.dcep.agent.rpc.util.AgentUtil;
import com.xxl.rpc.core.util.IpUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.util.FileUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestOperations;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

/**
 * @Classname ShellExcuteController
 * @Description TODO
 * @Date 2020/12/27 23:10
 * @Created by hexm
 */
@Controller
@Slf4j
public class ShellExcuteController {

    @Autowired
    private ShellExcuteService shellExcuteService;

    @Value("${server.port}")
    private String serverPort;

    @Value("${script.log.home}")
    private String scriptLogHome;

    /**
     * 执行脚本
     * @param shell
     * @return
     */
    @ResponseBody
    @PostMapping("/shell")
    public ResponseEntity<ShellResultVo> shellExecute(@RequestBody String shell) {
        ShellResultDto dto = shellExcuteService.runShell(AgentUtil.newRunId(), "/", shell);
        ShellResultVo vo = new ShellResultVo();
        vo.setCode(dto.getCode());
        vo.setMsg(dto.getMsg());
        vo.setRunId(dto.getRunId());
        vo.setLogUrl("http://" + IpUtil.getIp() + ":" + serverPort + "/log/" + vo.getRunId() + ".log");
        return ResponseEntity.ok(vo);
    }

    /**
     * 返回日志内容
     * @param runId
     * @return
     */
    @ResponseBody
    @PostMapping("/shellLog/{runId}")
    public ResponseEntity<String> shellLog(@PathVariable("runId") String runId) {
        String content = null;
        try {
            content = FileUtil.readAsString(new File(scriptLogHome + File.separator + runId +".log"));
        } catch (IOException e) {
            content = e.getMessage();
        }
        return ResponseEntity.ok(content);
    }


}

