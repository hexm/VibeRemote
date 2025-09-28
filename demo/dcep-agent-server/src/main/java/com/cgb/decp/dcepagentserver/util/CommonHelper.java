package com.cgb.decp.dcepagentserver.util;

import java.io.Closeable;
import java.io.IOException;

public abstract class CommonHelper {

	public static void closeResource(Closeable... objs) {
		for (Closeable obj : objs) {
			if (obj != null) {
				try {
					obj.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
}
