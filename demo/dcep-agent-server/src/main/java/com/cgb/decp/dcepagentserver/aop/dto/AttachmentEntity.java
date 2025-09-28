package com.cgb.decp.dcepagentserver.aop.dto;

import java.io.File;
import java.io.InputStream;

public class AttachmentEntity<T> {
	
	private String filename;
	
	private T attachment;
	
	public AttachmentEntity() {
	}

	public AttachmentEntity(String filename, T attachment) {
		super();
		if(!(attachment instanceof File || attachment instanceof InputStream)) {
			throw new RuntimeException("attachment只能为java.io.File或java.io.InputStream类型！");
		}
		this.filename = filename;
		this.attachment = attachment;
	}

	public String getFilename() {
		return filename;
	}

	public void setFilename(String filename) {
		this.filename = filename;
	}

	public T getAttachment() {
		return attachment;
	}

	public void setAttachment(T attachment) {
		if(!(attachment instanceof File || attachment instanceof InputStream)) {
			throw new RuntimeException("attachment只能为java.io.File或java.io.InputStream类型！");
		}
		this.attachment = attachment;
	}
	
	

}
