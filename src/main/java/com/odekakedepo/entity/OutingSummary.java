package com.odekakedepo.entity;

// 【entity】計画一覧のカードに出す情報をまとめた入れ物（DB のテーブルではない）
// daysLeft：出発までの日数、itemCount：持ち物の数、preparedCount：準備済みの数
// record（レコード）は、値を入れるだけのクラスを短く書ける Java の書き方。
// ( ) の中に書いた項目ごとに、値を取り出すメソッド（例：id()）が自動で作られる。
// 画面（Thymeleaf）では ${変数.id} のように書くと、この id() が呼ばれる。

// 計画一覧のカードに出す情報（計画＋出発までの日数＋準備の進み具合）
public record OutingSummary(Outing outing, long daysLeft, int itemCount, int preparedCount) {

    // 準備の進み具合（％）。持ち物が0件のときは 0 で割らないように 0 を返す
    // 100f の f は「小数で計算する」という意味。整数どうしだと割り算の小数点以下が切り捨てられるため
    public int progress() {
        return itemCount == 0 ? 0 : Math.round(preparedCount * 100f / itemCount);
    }

    // 出発日が今日か（あと0日）
    public boolean isToday() {
        return daysLeft == 0;
    }

    // 出発日が過ぎたか（マイナスの日数）＝思い出
    public boolean isMemory() {
        return daysLeft < 0;
    }
}
