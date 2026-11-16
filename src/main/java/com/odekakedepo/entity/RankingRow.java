package com.odekakedepo.entity;

// 【entity】集計画面（S06）の1行分の入れ物。DB のテーブルではなく、集計した結果を入れる
// name：代表の名前、count：件数、productName：同じ名前のお店の商品（なければ null）
// breakdown：まとめた言いかえの内訳（例：「すいとう 2・マイボトル 2」。なければ空）
// record（レコード）は、値を入れるだけのクラスを短く書ける Java の書き方。

public record RankingRow(String name, int count, String productName, String breakdown) {

    // お店の商品と名前が一致したら productName に商品名が入る。入っていれば「お店にある」
    public boolean inStore() {
        return productName != null;
    }

    // ほかの名前をまとめた行か（内訳があるか）
    public boolean merged() {
        return breakdown != null && !breakdown.isEmpty();
    }
}
