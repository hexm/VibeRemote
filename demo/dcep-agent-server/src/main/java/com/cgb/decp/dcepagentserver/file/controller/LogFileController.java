package com.cgb.decp.dcepagentserver.file.controller;

import com.xxl.rpc.core.util.IpUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.util.FileUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.File;

@Controller
@Slf4j
public class LogFileController {

    @Value("${script.log.home}")
    private String scriptLogHome;

    @GetMapping("/log/{logFileName}")
//	@ResponseBody
    public String scriptLogView(@PathVariable("logFileName") String logFileName, ModelMap map) {
        return logview(scriptLogHome + File.separator + logFileName, map);
        //return ResponseEntity.ok(logFileName);
    }

    @GetMapping("/logview")
    public String logview(String filePath, ModelMap map) {
        String fullFileName = filePath;
        map.addAttribute("logFileName", fullFileName);
        map.addAttribute("serverIp", IpUtil.getIp());
        try {
            // TODO:有内存溢出的风险，暂时不管
            String content = FileUtil.readAsString(new File(fullFileName));
            map.addAttribute("content", content);
        } catch (Exception ex) {
            map.addAttribute("content", ex.getMessage());
        }
        return "logView";
    }

    @GetMapping("/runscript")
    public String runscript(String filePath, ModelMap map) {
        String fullFileName = filePath;
        map.addAttribute("logFileName", fullFileName);
        map.addAttribute("serverIp", IpUtil.getIp());
        try {
            // TODO:有内存溢出的风险，暂时不管
            String content = FileUtil.readAsString(new File(fullFileName));
            map.addAttribute("content", content);
        } catch (Exception ex) {
            map.addAttribute("content", ex.getMessage());
        }
        return "runscript";
    }

}
