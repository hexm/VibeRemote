package com.dcep.agent.rpc.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

/**
 * @Classname ShellUtil
 * @Description TODO
 * @Date 2021/1/2 11:14
 * @Created by hexm
 */
public class AgentUtil {

    private static int RUN_ID_LENGTH = 4;

    public static void main(String[] args) {
        for (int i = 0; i < 1000; i++) {
            System.out.println(getCount(new Random().nextInt(9999), 10));
        }
    }

    public static String newRunId() {
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
        return df.format(new Date()) + getCount(new Random().nextInt(9999), RUN_ID_LENGTH);
    }

    //位数不足自动左补全
    private static String getCount(Integer i, int length) {
        String s = i.toString();
        int l = s.length();
        if (l < length) {
            for (int j = 0; j < length - l; j++) {
                s = "0" + s;
            }
        } else {
            s = s.substring(0, length);
        }
        return s;
    }
}
