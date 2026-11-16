package com.odekakedepo.entity;

// 【entity】特集・テーマ（themes テーブル）1行分＋入っている商品の ID（並び順）
// record（レコード）は、値を入れるだけのクラスを短く書ける Java の書き方。

import java.util.List;

public record Theme(Long id, String title, String description, List<Long> productIds) {
}
