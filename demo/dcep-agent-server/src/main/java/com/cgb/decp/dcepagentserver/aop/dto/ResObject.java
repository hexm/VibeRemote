package com.cgb.decp.dcepagentserver.aop.dto;

import java.io.Serializable;

public class ResObject<T> implements Serializable {

	private static final long serialVersionUID = 1L;

	/*private static final long serialVersionUID = 2979073976984276946L;
	private String transactiontype; //存放http的header中
	private String username; //存放http的header中
	private String timestamp; //存放http的header中
*/	
	public ResObject() {
	}
	
	public ResObject(String code, String msg, T data) {
		super();
		this.code = code;
		this.msg = msg;
		this.data = data;
	}
	private String code; //200
	private String msg;  //成功
	
	//private long totalRecord;
	private T data;
	/*public String getTransactiontype() {
		return transactiontype;
	}
	public void setTransactiontype(String transactiontype) {
		this.transactiontype = transactiontype;
	}
	public String getUsername() {
		return username;
	}
	public void setUsername(String username) {
		this.username = username;
	}*/
	/*public long getTotalRecord() {
		return totalRecord;
	}
	public void setTotalRecord(long totalRecord) {
		this.totalRecord = totalRecord;
	}*/

	/*public String getTimestamp() {
		return timestamp;
	}
	public void setTimestamp(String timestamp) {
		this.timestamp = timestamp;
	}*/
	public String getCode() {
		return code;
	}
	public void setCode(String code) {
		this.code = code;
	}
	public String getMsg() {
		return msg;
	}
	public void setMsg(String msg) {
		this.msg = msg;
	}
	public T getData() {
		return data;
	}
	public void setData(T data) {
		this.data = data;
	}
	
	/*
	 * public class Model{
	 *     public List<String> list;
	 *     public String total;
	 * }
	 */
	/*@FieldNote(desc="错误代码")
	@FieldNote(desc="错误信息描述")
	@FieldNote(desc="服务端流水号")
	@FieldNote(desc="客户端流水号")*/
 
}
