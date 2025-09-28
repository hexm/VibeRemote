package com.cgb.decp.dcepagentserver.shell.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * @Classname ShellResultDto
 * @Description TODO
 * @Date 2020/12/26 15:12
 * @Created by hexm
 */
@Data
public class ShellResultVo implements Serializable {

    private String runId;

    private int code;

    private String msg;

   private String logUrl;




}
