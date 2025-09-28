package com.cgb.decp.dcepagentserver.file.controller;

import com.cgb.decp.dcepagentserver.exception.BusinessException;
import com.cgb.decp.dcepagentserver.file.model.FileProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Controller
@Slf4j
public class ServerFileController {

	@ResponseBody
	@GetMapping("file/list")
	public ResponseEntity<List<FileProperty>> searchFile(String filePath) {
		if (StringUtils.isEmpty(filePath)) {
			throw new BusinessException(404, "参数为空");
		}
		log.info("list file:{}",filePath);
		List<FileProperty> fileList = new ArrayList<>();
		File file = new File(filePath);
		if (file.exists()) {
			if (file.isDirectory()) {
				File[] subFiles = file.listFiles();
				for (File str : subFiles) {
					fileList.add(new FileProperty(str));
				}
			} else {
				fileList.add(new FileProperty(file));
			}
		}
		return ResponseEntity.ok(fileList);
	}

	
	
	@GetMapping("file/downfile")
	@ResponseBody
	public File downloadFile1(String filePath, String fileName) {
		return new File(filePath);
	}

}
