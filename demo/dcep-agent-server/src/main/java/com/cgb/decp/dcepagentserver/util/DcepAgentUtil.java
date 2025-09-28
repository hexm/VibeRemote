package com.cgb.decp.dcepagentserver.util;

/**
 * @Classname DcepAgentUtil
 * @Description TODO
 * @Date 2020/12/28 9:42
 * @Created by hexm
 */
public class DcepAgentUtil {

    private static Boolean isWin;

    /**
     * check if the agent runing in win system
     *
     * @return
     */
    public static boolean isWin() {
        if (isWin == null) {
            isWin = System.getProperty("os.name").toUpperCase().contains("WIN");
        }
        return isWin;
    }

}
