package com.dcep.agent.rpc.shell.api;

import com.dcep.agent.rpc.shell.dto.ShellResultDto;

/**
 * @Classname ShellExcuteService
 * @Description TODO
 * @Date 2020/12/26 15:11
 * @Created by hexm
 */
public interface ShellExcuteService {

    public ShellResultDto runShell(String runId, String pwd, String scriptContent);
}
