package com.odekakedepo.entity;

// 【entity】言いかえの辞書（item_aliases テーブル）1行分の入れ物
// aliasKey：言いかえ前の名前を NameNormalizer でそろえたもの（比べる用）
// aliasName：言いかえ前の名前（画面の表示用）、canonicalName：代表の名前
// record（レコード）は、値を入れるだけのクラスを短く書ける Java の書き方。
// ( ) の中に書いた項目ごとに、値を取り出すメソッド（例：id()）が自動で作られる。

import java.time.LocalDateTime;

public record ItemAlias(Long id, String aliasKey, String aliasName, String canonicalName, LocalDateTime createdAt) {
}
