package com.odekakedepo.controller;

// 仮のマイページ（ニックネームだけ。パスワード・ログインはない）と、使い方ガイド・よくある質問
// ニックネームはブラウザの Cookie に保存する。サーバーの DB には保存しない（個人情報を持たない方針のため）
// 本物のログインは今後の提案（10_未決定事項）
// 【controller】ブラウザからのリクエスト（URL）を受け取り、service に処理を頼んで、表示する画面（テンプレート）を決めるクラス

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class MyPageController {

    // Cookie の名前と、残す期間（1年＝秒で表す）
    public static final String COOKIE = "odekake_nickname";
    private static final int ONE_YEAR = 60 * 60 * 24 * 365;
    // ニックネームの文字数の上限
    public static final int MAX_LENGTH = 20;

    // U28 マイページ。ニックネーム（なければ空）は CommonModelAdvice が全画面に渡している
    @GetMapping("/mypage")
    public String mypage(@RequestParam(required = false) String msg, Model model) {
        model.addAttribute("message", "saved".equals(msg) ? "ニックネームを保存しました"
                : "cleared".equals(msg) ? "ニックネームを消しました"
                : "invalid".equals(msg) ? "ニックネームは1〜" + MAX_LENGTH + "文字で入力してください" : null);
        model.addAttribute("isError", "invalid".equals(msg));
        return "mypage/index";
    }

    // U29 ニックネームを保存する（Cookie に入れる）
    // Cookie には日本語をそのまま入れられないので、URLEncoder で %E3%… の形に変換して入れる
    @PostMapping("/mypage")
    public String save(@RequestParam(required = false) String nickname, HttpServletResponse response) {
        String name = nickname == null ? "" : nickname.trim();
        if (name.isEmpty() || name.length() > MAX_LENGTH) {
            return "redirect:/mypage?msg=invalid";
        }
        Cookie cookie = new Cookie(COOKIE, URLEncoder.encode(name, StandardCharsets.UTF_8));
        cookie.setPath("/");          // どの画面でも読めるように
        cookie.setMaxAge(ONE_YEAR);   // 1年間残す
        cookie.setHttpOnly(true);     // JavaScript からは読めないようにする（盗まれにくくする）
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
        return "redirect:/mypage?msg=saved";
    }

    // U30 ニックネームを消す（Cookie の期限を 0 にすると、ブラウザが消す）
    @PostMapping("/mypage/clear")
    public String clear(HttpServletResponse response) {
        Cookie cookie = new Cookie(COOKIE, "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return "redirect:/mypage?msg=cleared";
    }

    // U38 ログイン（画面だけ）。sent=true のときは「準備中」を出す
    @GetMapping("/login")
    public String login(@RequestParam(required = false) Boolean sent, Model model) {
        model.addAttribute("sent", Boolean.TRUE.equals(sent));
        return "mypage/login";
    }

    // U39 ログインの送信（画面だけ。認証も保存もしない。入力されたパスワードなどはどこにも残さない）
    @PostMapping("/login")
    public String loginSubmit() {
        return "redirect:/login?sent=true";
    }

    // U40 ユーザー情報の設定（画面だけ）
    @GetMapping("/mypage/settings")
    public String settings(@RequestParam(required = false) Boolean sent, Model model) {
        model.addAttribute("sent", Boolean.TRUE.equals(sent));
        return "mypage/settings";
    }

    // U41 ユーザー情報の送信（画面だけ。メールアドレスなどの個人情報は保存しない）
    @PostMapping("/mypage/settings")
    public String settingsSubmit() {
        return "redirect:/mypage/settings?sent=true";
    }

    // U31 使い方ガイド
    @GetMapping("/guide")
    public String guide() {
        return "help/guide";
    }

    // U32 よくある質問
    @GetMapping("/faq")
    public String faq() {
        return "help/faq";
    }
}
