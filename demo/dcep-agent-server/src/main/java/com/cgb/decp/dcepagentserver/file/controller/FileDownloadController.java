package com.cgb.decp.dcepagentserver.file.controller;

import com.cgb.decp.dcepagentserver.exception.BusinessException;
import com.cgb.decp.dcepagentserver.file.util.FileDownUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.activation.MimetypesFileTypeMap;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

@Controller
public class FileDownloadController {

	@GetMapping("file/down")
	@ResponseBody
	public ResponseEntity<String> downloadFile(String filePath, String fileName) {
		if (StringUtils.isEmpty(filePath) || StringUtils.isEmpty(fileName)) {
			throw new BusinessException("filePath, fileName can not null.");
		}
		File file = new File(filePath);
		if (!file.exists() || !file.isFile()) {
			return ResponseEntity.ok("file not exist.");
		}
		ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
		HttpServletResponse response = requestAttributes.getResponse();
		// 设置信息给客户端不解析
		String type = new MimetypesFileTypeMap().getContentType(filePath);
		// 设置contenttype，即告诉客户端所发送的数据属于什么类型
		response.setHeader("Content-type", type);
		response.setContentType(type);
		// 设置编码
		String hehe;
		try {
			hehe = new String(fileName.getBytes("utf-8"), "iso-8859-1");
			// 设置扩展头，当Content-Type 的类型为要下载的类型时 , 这个信息头会告诉浏览器这个文件的名字和类型。
			response.setHeader("Content-Disposition", "attachment;filename=" + hehe);
			FileDownUtil.download(filePath, response);
		} catch (IOException e) {
			return ResponseEntity.ok(e.getMessage());
		}
		return ResponseEntity.ok("");
	}
	
	
	@GetMapping("file/test")
	@ResponseBody
	public ResponseEntity<String> downloadFiletest(String filePath, String fileName) {
		return ResponseEntity.ok("okk");
	}

}
