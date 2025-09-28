package com.cgb.decp.dcepagentserver.exception;

/**
 * 业务异常
 */
public class BusinessException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private String resCode;
	private String resMsg;

	public BusinessException(String resMsg) {
		super(resMsg);
		this.resMsg = resMsg;
	}

	public BusinessException(String resCode, String resMsg) {
		super(resMsg);
		this.resCode = resCode;
		this.resMsg = resMsg;

	}

	public BusinessException(int resCode, String resMsg) {
		super(resMsg);
		this.resCode = resCode + "";
		this.resMsg = resMsg;

	}

	public BusinessException(IErrorCode errorCode) {
		super(errorCode.getErrorCode());
		this.resMsg = errorCode.getErrorMessage();
	}

	public String getResCode() {
		return resCode;
	}

	public void setResCode(String resCode) {
		this.resCode = resCode;
	}

	public String getResMsg() {
		return resMsg;
	}

	public void setResMsg(String resMsg) {
		this.resMsg = resMsg;
	}

}
