package com.odekakedepo.entity;

// 【entity】持ち物（packing_items テーブル）1行分＋ひも付いた商品の名前と値段
// productId・productName・productPrice は、商品を選んでいなければ null（Integer/Long は null を入れられる型）
// record（レコード）は、値を入れるだけのクラスを短く書ける Java の書き方。
// ( ) の中に書いた項目ごとに、値を取り出すメソッド（例：id()）が自動で作られる。
// 画面（Thymeleaf）では ${変数.id} のように書くと、この id() が呼ばれる。

// 持ち物（packing_items テーブルの1行＋ひも付いた商品の名前と値段）。
// 買い物リストに出るのは「まだ準備できていない持ち物」と「商品を選んで買ったもの」。
public record PackingItem(Long id, Long outingId, String name, boolean prepared, int quantity, String prepMethod,
                          Long productId, String productName, Integer productPrice) {

    // 用意のしかた：お店（DEPO）で買う／別で準備する。まだ決めていなければ prepMethod は null
    public static final String STORE = "store";
    public static final String OTHER = "other";

    public boolean byStore() {
        return STORE.equals(prepMethod);
    }

    public boolean byOther() {
        return OTHER.equals(prepMethod);
    }

    // まだ決まっていない（忘れ物の元）：用意のしかたが未選択か、「お店で買う」なのに商品を選んでいない
    public boolean undecided() {
        return !prepared && (prepMethod == null || (byStore() && productId == null));
    }

    // この持ち物の金額＝値段 × 個数。商品が決まっていない（productPrice が null）ときは 0
    public int subtotal() {
        return productPrice == null ? 0 : productPrice * quantity;
    }

    // 買い物リストに出すか：準備できていないもの、または商品を選んで買ったもの
    // 買い物リストに出すかどうかのルールを、ここ1か所にまとめている
    // 「! 」は否定。|| は「または」
    public boolean inShoppingList() {
        return !prepared || productId != null;
    }
}
