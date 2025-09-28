package com.cgb.decp.dcepagentserver.file.model;

import com.cgb.decp.dcepagentserver.file.util.FileSizeUtil;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;
import java.text.SimpleDateFormat;

@Data
@NoArgsConstructor
public class FileProperty {

	private String fullName;

	private String name;

	private String lastModified;

	private String size;

	private String type;//F:file  D:directory

	public FileProperty(File file) {
		this.fullName = replace(file.getAbsolutePath());
		this.name = file.getName();
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		this.lastModified = dateFormat.format(file.lastModified());
		this.size = FileSizeUtil.sizeFormat(file.length());
		if (file.isDirectory()) {
			type = "D";
		} else {
			type = "F";
		}
	}
	
	private String replace(String path) {
		return path.replaceAll("\\\\","/");
	}

}
