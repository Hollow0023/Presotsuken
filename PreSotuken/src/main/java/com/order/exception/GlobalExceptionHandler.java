package com.order.exception;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.springframework.dao.DataAccessException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {
	


	@ExceptionHandler(Exception.class)
    public String handleAll(Exception ex, Model model) {


        
        // スタックトレース取得（デバッグ環境のみ　要削除）
        StringBuilder trace = new StringBuilder();
        for (int i = 0; i < Math.min(ex.getStackTrace().length, 5); i++) {
            trace.append(ex.getStackTrace()[i].toString()).append("<br/>");
        }
        model.addAttribute("stacktrace", trace.toString());

        return "error";
	}
	
	@ExceptionHandler(DataAccessException.class)
    public String handleDatabaseException(DataAccessException ex, Model model) {
        model.addAttribute("message", "データベースエラーが発生しました。");
        
        // スタックトレース（デバッグ用）
        StringBuilder trace = new StringBuilder();
        for (int i = 0; i < Math.min(ex.getStackTrace().length, 5); i++) {
            trace.append(ex.getStackTrace()[i].toString()).append("<br/>");
        }
        model.addAttribute("stacktrace", trace.toString());

        return "error"; // ← 任意のエラーページへ
    }
	
	@ExceptionHandler({ IOException.class, FileNotFoundException.class })
    public String handleFileIOException(Exception ex, Model model) {
        model.addAttribute("message", "ファイルの読み書き中にエラーが発生しました");
        
        // スタックトレース表示（任意）
        StringBuilder trace = new StringBuilder();
        for (int i = 0; i < Math.min(ex.getStackTrace().length, 5); i++) {
            trace.append(ex.getStackTrace()[i].toString()).append("<br/>");
        }
        model.addAttribute("stacktrace", trace.toString());

        return "error"; // ← ファイル関連用のエラーページを用意しておくと親切
    }
}
