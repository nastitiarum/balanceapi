package com.assesment.balance.utility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppLogUtil {
	private static final Logger log = LoggerFactory.getLogger(AppLogUtil.class);
	
	public static void WriteInfoLog(String message) {
		String msg = message;
		log.info(msg);
	}
	
	public static void WriteErrorLog(String message, Exception ex) {
		AppErrorLogUtil.WriteErrorLog(message, ex);
	}
}
