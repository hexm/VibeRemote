package com.cgb.decp.dcepagentserver.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public abstract class DateHelper {
	
 
	public static Date parseDate(String dateStr, String pattern) {  
		Date date = null;
    	try {
			SimpleDateFormat sdf= new SimpleDateFormat(pattern);
			 date = sdf.parse(dateStr);
		} catch (ParseException e) {
			throw new RuntimeException("日期类型转换错误！！！");
		}
    	return date;
    } 
	
	public static String toDateString(Date date){
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd"); 
		return df.format(date);
	}
	public static String toDateString(Date date,String pattern){
		SimpleDateFormat df = new SimpleDateFormat(pattern);
		return df.format(date);
	}
	public static String toDateString(String time,String frompattern ,String topattern){
		String date = null ;
		try {
			 SimpleDateFormat format = new SimpleDateFormat(frompattern);
		     Date parse = format.parse(time);
		     SimpleDateFormat dayformat = new SimpleDateFormat(topattern);
		     date = dayformat.format(parse);
			
		} catch (Exception e) {
			throw new RuntimeException("日期类型转换错误！！！");
		}
	        return date ;
	}
    public static String toDateTimeString(long timeInMillis) {  
    	Calendar calendar = Calendar.getInstance();
		calendar.setTimeInMillis(timeInMillis);
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); 
		return df.format(calendar.getTime());
    } 

	
	 /** 
     * 获取默认格式日期字符串 
     * @return 
     */  
    public static String toDateTimeString() {  
    	Calendar calendar = Calendar.getInstance();  
    	SimpleDateFormat sdf = new SimpleDateFormat();  
        sdf.applyPattern("yyyy-MM-dd HH:mm:ss");  
        return sdf.format(calendar.getTime());  
    } 
    
    //yyyy-MM-dd HH:mm:ss
    public static long toLong(String dateStr, String pattern) {  
    	//String str="2012-5-27";
    	long value = 0;
    	try {
			SimpleDateFormat sdf= new SimpleDateFormat(pattern);
			Date date = sdf.parse(dateStr);
			value = date.getTime();
		} catch (ParseException e) {
			throw new RuntimeException("日期类型转换错误！！！");
		}
    	return value;
    }

	/**
	 * 前/后?分钟
	 * @param d
	 * @param minute
	 * @return
	 */
	public static Date rollMinute(Date d, int minute) {
		return new Date(d.getTime() + minute * 60 * 1000);
	}
	/**
	 * 前/后?月
	 *
	 * @param d
	 * @param mon
	 * @return
	 */
	public static Date rollMon(Date d, int mon) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(d);
		cal.add(Calendar.MONTH, mon);
		return cal.getTime();
	}

	/**
	 * 前/后?年
	 *
	 * @param d
	 * @param year
	 * @return
	 */
	public static Date rollYear(Date d, int year) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(d);
		cal.add(Calendar.YEAR, year);
		return cal.getTime();
	}

	public static Date rollDate(Date d, int year, int mon, int day) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(d);
		cal.add(Calendar.YEAR, year);
		cal.add(Calendar.MONTH, mon);
		cal.add(Calendar.DAY_OF_MONTH, day);
		return cal.getTime();
	}


	public static void main(String[] args) {
		Date date = new Date();
		date.setTime(date.getTime() + 60*60*1000);
		System.out.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss SSS").format(date));
	}

}
