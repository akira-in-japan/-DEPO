package com.odekakedepo.entity;

// 【entity】商品（products テーブル）1行分の入れ物
// record（レコード）は、値を入れるだけのクラスを短く書ける Java の書き方。
// ( ) の中に書いた項目ごとに、値を取り出すメソッド（例：id()）が自動で作られる。
// 画面（Thymeleaf）では ${変数.id} のように書くと、この id() が呼ばれる。

// 商品（products テーブルの1行）
public record Product(Long id, String name, String category, int price, String description, Integer stock) {

    // 在庫の表示：null＝在庫情報なし、0＝在庫なし、3以下＝残りわずか、それより多い＝在庫あり
    public static final int FEW = 3;

    public String stockLabel() {
        if (stock == null) {
            return "在庫情報はまだないよ。お店に聞いてみてね";
        }
        return stock == 0 ? "在庫なし" : stock <= FEW ? "残りわずか（" + stock + "個）" : "在庫あり（" + stock + "個）";
    }
}
