package com.cgb.decp.dcepagentserver.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;

@Slf4j
public class IPUtil {

	/**
	 * 根据HttpServletRequest对象获取客户端真实的IP地址
	 * 
	 * @param request HttpServletRequest对象
	 * @return
	 */
	public static String getRemoteClientIP(HttpServletRequest request) {

		String ip = request.getHeader("X-Forwarded-For");
		// log.debugf("X-Real-IP: %s, X-Forwarded-For: %s, Proxy-Client-IP: 5s", ip,
		// request.getHeader("X-Forwarded-For"), request.getHeader("Proxy-Client-IP"));
		ip = getSingleIp(ip);
		// X-Real-IP
		if (ipIsEmpty(ip)) {
			ip = request.getHeader("X-Real-IP");
			// log.debugf("-------------------X-Real-IP: %s ",ip);
			ip = getSingleIp(ip);
		}
		// Proxy-Client-IP
		if (ipIsEmpty(ip)) {
			ip = request.getHeader("Proxy-Client-IP");
			// log.debugf("-------------------Proxy-Client-IP:",ip);
			ip = getSingleIp(ip);
		}
		// WL-Proxy-Client-IP
		if (ipIsEmpty(ip)) {
			ip = request.getHeader("WL-Proxy-Client-IP");
			// log.debugf("-------------------WL-Proxy-Client-IP:",ip);
			ip = getSingleIp(ip);
		}
		// getRemoteAddr
		if (ipIsEmpty(ip)) {
			ip = request.getRemoteAddr();
			// log.debugf("-------------------getRemoteAddr:",ip);
			ip = getSingleIp(ip);
		}
		return ip;

	}

	private static boolean ipIsEmpty(String ip) {
		return StringUtils.isEmpty(ip) || "unknown".equalsIgnoreCase(ip);
	}

	private static String getSingleIp(String ip) {
		if (!StringUtils.isEmpty(ip)) {
			ip = ip.replace(" ", "");
			String[] ips = ip.split(",");
			if (ips.length >= 1) {
				ip = ips[0];
			}
		}

		return ip;
	}

//	public static String getServerIp() {
//		try {
//			InetAddress address = InetAddress.getLocalHost();
//			return address.getHostAddress();
//		} catch (UnknownHostException e) {
//			log.error("", e);
//			return "localhost";
//		}
//	}

}