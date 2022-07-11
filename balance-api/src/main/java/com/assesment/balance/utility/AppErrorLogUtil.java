package com.assesment.balance.utility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppErrorLogUtil {
	private static final Logger log = LoggerFactory.getLogger(AppErrorLogUtil.class);
	
	public static void WriteErrorLog(String message, Exception ex) {
		String msg = message;
		log.error(msg, ex);
	}
}
