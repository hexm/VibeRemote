package com.dcep.agent.rpc.shell.dto;

import java.io.Serializable;

/**
 * @Classname ShellResultDto
 * @Description TODO
 * @Date 2020/12/26 15:12
 * @Created by hexm
 */
public class ShellResultDto implements Serializable {

    private String runId;

    private int code;

    private String msg;

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public ShellResultDto(String runId, int code, String msg) {
        this.runId = runId;
        this.code = code;
        this.msg = msg;
    }

    @Override
    public String toString() {
        return "ShellResultDto{" +
                "runId=" + runId +
                ", code=" + code +
                ", msg='" + msg + '\'' +
                '}';
    }
}
