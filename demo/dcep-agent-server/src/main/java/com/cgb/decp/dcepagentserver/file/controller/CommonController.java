package com.cgb.decp.dcepagentserver.file.controller;

import com.xxl.rpc.core.util.IpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestOperations;

@Controller
public class CommonController {

	@Autowired
	private RestOperations restOperations;
	
	
	public String index() {
		return "index";
	}

	@GetMapping("/serverIp")
	@ResponseBody
	public ResponseEntity<String> serverIp() {
		return ResponseEntity.ok(IpUtil.getIp());
	}

	@GetMapping("/restOperationsTest")
	@ResponseBody
	public ResponseEntity<String> restOperationsTest() {
		return ResponseEntity.ok(restOperations.toString());
	}

}
