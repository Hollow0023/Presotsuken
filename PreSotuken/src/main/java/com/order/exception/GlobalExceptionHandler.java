package com.order.exception;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;

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
	
	/**
	 * パラメータ型変換エラーを処理
	 * （例: paymentId=undefinedなど無効な値が送信された場合）
	 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Object handleTypeMismatch(MethodArgumentTypeMismatchException ex, Model model, HttpServletRequest request) {
        // APIリクエストの場合はJSONレスポンスを返す
        if (request.getRequestURI().startsWith("/api/")) {
            return ResponseEntity.badRequest().body("Invalid parameter: " + ex.getName());
        }
        
        // 通常のページリクエストの場合はエラーページを表示
        model.addAttribute("message", "パラメータの形式が正しくありません。");
        
        // スタックトレース表示（デバッグ用）
        StringBuilder trace = new StringBuilder();
        for (int i = 0; i < Math.min(ex.getStackTrace().length, 5); i++) {
            trace.append(ex.getStackTrace()[i].toString()).append("<br/>");
        }
        model.addAttribute("stacktrace", trace.toString());

        return "error";
    }
}
