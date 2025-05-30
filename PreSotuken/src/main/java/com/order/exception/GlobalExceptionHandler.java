package com.order.exception;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(Exception.class)
	public String handleAllExceptions(Exception ex, Model model) {
	    model.addAttribute("message", "エラーが発生しました：" + ex.getMessage());

	    // スタックトレースの一部（先頭5行）を文字列にして渡す
	    StringBuilder traceBuilder = new StringBuilder();
	    StackTraceElement[] trace = ex.getStackTrace();
	    for (int i = 0; i < Math.min(trace.length, 5); i++) {
	        traceBuilder.append(trace[i].toString()).append("<br/>");
	    }
	    model.addAttribute("stacktrace", traceBuilder.toString());

	    return "error";
	}

}
