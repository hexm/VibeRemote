package com.cgb.decp.dcepagentserver.aop;

import com.cgb.decp.dcepagentserver.aop.dto.ResObject;
import com.cgb.decp.dcepagentserver.exception.BusinessException;
import com.cgb.decp.dcepagentserver.util.DateHelper;
import com.cgb.decp.dcepagentserver.util.IPUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.bitwalker.useragentutils.UserAgent;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.ValidationException;

@Aspect
@Component
public class ControllerAspect {

	private static final Logger log = LoggerFactory.getLogger(ControllerAspect.class.getName());

	private ObjectMapper mapper = new ObjectMapper();

	@Pointcut("(@annotation(org.springframework.web.bind.annotation.RequestMapping) || @annotation(org.springframework.web.bind.annotation.PostMapping) || @annotation(org.springframework.web.bind.annotation.GetMapping)) && !within(com.cgb.decp.dcepagentserver.file.controller.FileDownloadController) ")
	public void requestMappingAspect() {
	}

	@SuppressWarnings("rawtypes")
	@Around("requestMappingAspect()")
	public Object doControllerArount(ProceedingJoinPoint joinPoint) throws Exception {

		Signature signature = joinPoint.getSignature();
		String methodName = signature.getName();
		MDC.put("method", methodName);
		Object[] args = joinPoint.getArgs();
		HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
		MDC.put("requestMethod", request.getMethod());
		MDC.put("operatorIp", IPUtil.getRemoteClientIP(request));
		MDC.put("operatorLocation", "未知地点");//浏览器端操作人地点
		MDC.put("browser", request.getHeader("User-Agent"));
		UserAgent userAgent = UserAgent.parseUserAgentString(request.getHeader("User-Agent"));

		if (userAgent != null) {
			MDC.put("operateSystem", userAgent.getOperatingSystem().toString());
		}
		log.debug("AOP 开始执行方法    <<======" + methodName + "======>>");
		log.debug("AOP 请求的URI路径    <<======" + request.getRequestURI() + "======>>");
		MDC.put("operatorUrl", request.getRequestURI());
		String contentType = request.getHeader("Content-Type");
		log.debug("contentType:" + contentType);
		if (contentType != null && contentType.contains("application/json")) {
			MDC.put("requestParam", mapper.writeValueAsString(args));
		}
		// 日志拦截 start
		Object obj = null;
		if("errorHtml".equals(methodName)) {
			try {
				return joinPoint.proceed();
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
		try {
			log.debug("AOP 请求参数    <<=====request:" + mapper.writeValueAsString(args));
			MDC.put("requestParam", mapper.writeValueAsString(args));
			HttpServletResponse response = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getResponse();
			response.addHeader("transactiontype", "transactiontype");// 交易类型
			response.addHeader("userName", "username");
			response.addHeader("timestamp", DateHelper.toDateTimeString());
			response.addHeader("sign", "sign");
			response.addHeader("serialNumber", "sign");// 服务端流水号

			obj = joinPoint.proceed();

			if(obj instanceof ResponseEntity) {
				ResponseEntity oo = (ResponseEntity) obj;
				Object body = oo.getBody();
				ResObject<Object> resObject = new ResObject<Object>();
				resObject.setData(body);
				resObject.setCode("0");
				resObject.setMsg("交易成功");
				obj = ResponseEntity.ok(resObject);
			}
		} catch (Throwable e) {
			log.error("--> {} AOP 拦截到异常" + e.getMessage(), methodName);
			ResObject<Object> resObject = new ResObject<Object>();
			if (e instanceof BusinessException) {
				log.error("--> AOP BusinessException");
				BusinessException ex = (BusinessException) e;
				// obj = JSON.toJSON(new ResObj(ex.getResCode(), "fail", ex.getMessage()));
				resObject.setCode("busi");
				resObject.setMsg(ex.getMessage() != null ? ex.getMessage() : "busi异常");
				obj = ResponseEntity.ok(resObject);
			} else if (e instanceof ValidationException) {
				log.error("--> AOP ValidationException");
				ValidationException ex = (ValidationException) e;
				resObject.setCode("validate");
				resObject.setMsg(ex.getMessage() != null ? ex.getMessage() : "validate异常");
				obj = ResponseEntity.ok(resObject);
			} else {
				log.error("--> AOP OtherException");
				resObject.setCode("fail");
				resObject.setMsg(e.getMessage() != null ? e.getMessage() : "失败");
				obj = ResponseEntity.ok(resObject);
			}
		} finally {
			MDC.clear();
		}

		if (obj != null) {
			final String logInfo = mapper.writeValueAsString(obj);
			log.debug("AOP 响应参数    <<=====response:" + logInfo);
			MDC.put("responseData", logInfo);
		} else {
			log.debug("AOP 响应参数    <<=====response: null");
			MDC.put("responseData", null);
		}

		log.debug("AOP 执行方法结束    <<======/" + methodName + "======>>");

		return obj;
	}

}
