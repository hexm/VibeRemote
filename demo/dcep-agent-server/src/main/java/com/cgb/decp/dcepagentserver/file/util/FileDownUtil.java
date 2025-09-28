package com.cgb.decp.dcepagentserver.file.util;

import javax.servlet.http.HttpServletResponse;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

public class FileDownUtil {

	public static void download(String filename, HttpServletResponse res) throws IOException {
		// 发送给客户端的数据
		OutputStream outputStream = res.getOutputStream();
		byte[] buff = new byte[1024];
		// 读取filename
		try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(new File(filename)))) {
			while (true) {
				int i = bis.read(buff);
				if (i != -1) {
					outputStream.write(buff, 0, i);
					outputStream.flush();
					//i = bis.read(buff);
				} else {
					break;
				}
			}
		}
	}
}
