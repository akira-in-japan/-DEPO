package com.odekakedepo.entity;

// 【entity】DB の1行を Java で扱うための入れ物
// record（レコード）は、値を入れるだけのクラスを短く書ける Java の書き方。
// ( ) の中に書いた項目ごとに、値を取り出すメソッド（例：id()）が自動で作られる。
// 画面（Thymeleaf）では ${変数.id} のように書くと、この id() が呼ばれる。

// 持ち物テンプレート（item_templates テーブルの1行）。groupName は「基本装備」などの見出し
public record ItemTemplate(Long id, String category, String groupName, String name) {
}
