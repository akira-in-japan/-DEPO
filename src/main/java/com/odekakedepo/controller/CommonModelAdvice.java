package com.odekakedepo.controller;

// 全画面で共通に使う値を、Model に自動で入れるクラス
// @ControllerAdvice ＋ @ModelAttribute：どの Controller の画面を表示するときにも、このメソッドの戻り値が Model に入る

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class CommonModelAdvice {

    // ニックネーム：Cookie から読み、URLDecoder で日本語に戻す。なければ空文字
    // 画面では ${nickname} で使える（「ようこそ○○さん」・マイページ）
    @ModelAttribute("nickname")
    public String nickname(@CookieValue(name = MyPageController.COOKIE, required = false) String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        try {
            String name = URLDecoder.decode(value, StandardCharsets.UTF_8).trim();
            return name.length() > MyPageController.MAX_LENGTH ? "" : name;
        } catch (IllegalArgumentException e) {
            return ""; // 壊れた Cookie は無視する
        }
    }
}
