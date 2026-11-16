package com.odekakedepo.util;

// 【util】持ち物の名前の書き方をそろえる道具（集計・おすすめ・テンプレートの重複チェックで使う）
// 例：「すいとう」「ｽｲﾄｳ」「スイトウ」を同じものとして数えるため。画面には元の名前を表示する

import java.text.Normalizer;

// 持ち物の名前の書き方をそろえる（集計・比較用）。画面には元の名前を表示する。
// ・前後の空白を取る ・全角と半角をそろえる（NFKC） ・ひらがなをカタカナにする
// ・（ ）の中身を外す（例：「水筒（800ml）」→「水筒」）
public final class NameNormalizer {

    // このクラスは道具（static メソッド）だけなので、new できないように private にしている
    private NameNormalizer() {
    }

    // 持ち物の名前を、比べやすい形にそろえて返す
    public static String normalize(String name) {
        if (name == null) {
            return "";
        }
        // Normalizer（Java に最初からある道具）の NFKC で、全角英数字→半角、半角カナ→全角カナにそろえる
        String s = Normalizer.normalize(name, Normalizer.Form.NFKC).trim();
        // （ ）の中身を消す。例：「水筒（800ml）」→「水筒」
        // replaceAll は「正規表現」というパターンに合う部分を置きかえるメソッド
        s = s.replaceAll("\\(.*?\\)", "").trim();
        // StringBuilder は、文字を1つずつつなげて文字列を作るときに使う（+ でつなげるより速い）
        StringBuilder sb = new StringBuilder(s.length());
        // 1文字ずつ見て、ひらがな（ぁ〜ゖ）ならカタカナに変える
        // Unicode では、ひらがなとカタカナの文字コードの差がちょうど 0x60 なので、足すだけで変換できる
        for (char c : s.toCharArray()) {
            // ひらがな（ぁ〜ゖ）をカタカナ（ァ〜ヶ）に
            sb.append(c >= '\u3041' && c <= '\u3096' ? (char) (c + 0x60) : c);
        }
        // 最後に、間の空白もすべて消す
        return sb.toString().replaceAll("\\s+", "");
    }
}
