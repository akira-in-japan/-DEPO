package com.odekakedepo.controller;

// ホーム（トップページ）。でぽんたの自己紹介も表示する
// 【controller】ブラウザからのリクエスト（URL）を受け取り、表示する画面（テンプレート）を決めるクラス

import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    // でぽんたの自己紹介（順番に話す）
    static final List<String> INTRO = List.of(
            "でぽんただよ！おでかけDEPO の案内係をしているよ",
            "計画をつくる案内を、最後までしっかりするよ！",
            "持ち物の準備も、お店での買いものも、ぼくにまかせてね",
            "一緒に楽しい思い出をつくっていこうね！");

    // U01 ホーム
    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("introLines", INTRO);
        return "index";
    }
}
