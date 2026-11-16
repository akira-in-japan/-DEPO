package com.odekakedepo.entity;

// 【entity】おすすめ（ほかの人がよく持っていくもの）1件分の入れ物。DB のテーブルではなく、集計した結果を入れる
// record（レコード）は、値を入れるだけのクラスを短く書ける Java の書き方。
// ( ) の中に書いた項目ごとに、値を取り出すメソッド（例：id()）が自動で作られる。
// 画面（Thymeleaf）では ${変数.id} のように書くと、この id() が呼ばれる。

// ほかの人がよく持っていくもの（F5-2）：持ち物名と、選ばれた計画の数
public record Recommendation(String name, int count) {
}
