package com.odekakedepo.web;

// ブラウザごとの ID（ログインの代わり）。Cookie「odekake_browser」に推測できない文字列（UUID）を入れて見分ける
// お気に入りをブラウザごとに分けるために使う。本物のログインを作ったら、ユーザー ID に置きかえる（10_未決定事項）
// @Component を付けると、Spring がインスタンスを1つ作って管理し、必要なクラスに渡してくれる（DI）

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class BrowserId {

    // Cookie の名前と、残す期間（1年）
    public static final String COOKIE = "odekake_browser";
    private static final int ONE_YEAR = 60 * 60 * 24 * 365;

    // Cookie に ID があればそれを返し、なければ新しく作って Cookie に入れる
    public String get(HttpServletRequest request, HttpServletResponse response) {
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                // 32文字の英数字（UUID から「-」を消したもの）だけを正しい ID として使う
                if (COOKIE.equals(c.getName()) && c.getValue() != null && c.getValue().matches("[0-9a-f]{32}")) {
                    return c.getValue();
                }
            }
        }
        String id = UUID.randomUUID().toString().replace("-", "");
        Cookie cookie = new Cookie(COOKIE, id);
        cookie.setPath("/");
        cookie.setMaxAge(ONE_YEAR);
        cookie.setHttpOnly(true);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
        return id;
    }
}
