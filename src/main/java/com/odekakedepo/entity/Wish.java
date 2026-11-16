package com.odekakedepo.entity;

// 【entity】やりたいこと（outing_wishes テーブル）1行分の入れ物
// record（レコード）は、値を入れるだけのクラスを短く書ける Java の書き方。
// ( ) の中に書いた項目ごとに、値を取り出すメソッド（例：id()）が自動で作られる。
// 画面（Thymeleaf）では ${変数.id} のように書くと、この id() が呼ばれる。

// やりたいこと（outing_wishes テーブルの1行）
public record Wish(Long id, Long outingId, String content) {
}
