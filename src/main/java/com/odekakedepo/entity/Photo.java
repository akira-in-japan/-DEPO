package com.odekakedepo.entity;

// 【entity】思い出の写真・動画（outing_photos テーブル）1行分の入れ物
// token：ファイル名と URL に使う推測できない文字列、expiresAt：消える日時
// record（レコード）は、値を入れるだけのクラスを短く書ける Java の書き方。
// ( ) の中に書いた項目ごとに、値を取り出すメソッド（例：id()）が自動で作られる。
// 画面（Thymeleaf）では ${変数.id} のように書くと、この id() が呼ばれる。

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

// 思い出の写真・動画（outing_photos テーブルの1行）
public record Photo(Long id, Long outingId, String token, String originalName, String contentType,
                    long fileSize, LocalDateTime uploadedAt, LocalDateTime expiresAt) {

    // Content-Type（ファイルの種類）が video/ で始まれば動画
    public boolean isVideo() {
        return contentType.startsWith("video/");
    }

    // 消えるまでの日数（今日消えるなら 0）
    // 消えるまでの日数。ChronoUnit.DAYS.between(A, B) は A から B までの日数
    // Math.max(0, …) で、マイナスにならないようにしている
    public long daysLeft() {
        return Math.max(0, ChronoUnit.DAYS.between(LocalDateTime.now(), expiresAt));
    }
}
