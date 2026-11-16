package com.odekakedepo.entity;

// 【entity】計画（outings テーブル）1行分の入れ物
// shareToken は、しおりを共有する URL に使う推測できない文字列
// record（レコード）は、値を入れるだけのクラスを短く書ける Java の書き方。
// ( ) の中に書いた項目ごとに、値を取り出すメソッド（例：id()）が自動で作られる。
// 画面（Thymeleaf）では ${変数.id} のように書くと、この id() が呼ばれる。

import java.time.LocalDate;

// 計画（outings テーブルの1行）
public record Outing(Long id, String title, LocalDate outingDate, String destination,
                     int participants, String category, String memo, String shareToken) {

    // 種類の英語名（CSS のクラスやアイコンに使う）
    // 種類（キャンプ／旅行／ピクニック）を英語の短い名前にする。
    // HTML の class 名（camp など）に使い、種類ごとの色を CSS で付けるため
    public String kindKey() {
        // switch 式：category の値によって、返す文字列を切りかえる
        return switch (category) {
            case "キャンプ" -> "camp";
            case "旅行" -> "travel";
            default -> "picnic";
        };
    }

    // 種類ごとのアイコンの名前（CSS の i-tent などに使う）
    public String icon() {
        return switch (category) {
            case "キャンプ" -> "tent";
            case "旅行" -> "suitcase";
            default -> "basket";
        };
    }
}
