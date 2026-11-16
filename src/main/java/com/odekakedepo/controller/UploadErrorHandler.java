package com.odekakedepo.controller;

// 【controller】アプリ全体のエラー処理
// @ControllerAdvice：すべての Controller で起きた例外（エラー）を、ここでまとめて受け取れる

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

// アップロードするファイルが大きすぎたとき、思い出の画面にメッセージ付きで戻す
@ControllerAdvice
public class UploadErrorHandler {

    // ファイルが大きすぎる（application.properties の上限を超えた）エラーを受け取り、思い出の画面に戻す
    // リクエストの URL（/outings/{id}/photos）から計画の URL を作っている
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String tooLarge(HttpServletRequest request) {
        String uri = request.getRequestURI(); // /outings/{id}/photos
        String base = uri.endsWith("/photos") ? uri.substring(0, uri.length() - "/photos".length()) : "/outings";
        return "redirect:" + base + "/memory?uploadError=size#photos";
    }
}
