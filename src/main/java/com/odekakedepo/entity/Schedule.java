package com.odekakedepo.entity;

// 【entity】予定（schedules テーブル）1行分の入れ物
// startTime は LocalTime（時刻だけを表す型）。時間が空欄の予定は null になる
// record（レコード）は、値を入れるだけのクラスを短く書ける Java の書き方。
// ( ) の中に書いた項目ごとに、値を取り出すメソッド（例：id()）が自動で作られる。
// 画面（Thymeleaf）では ${変数.id} のように書くと、この id() が呼ばれる。

import java.time.LocalTime;

// 予定（schedules テーブルの1行）。時間は空欄でもよい
public record Schedule(Long id, Long outingId, LocalTime startTime, String content, String place) {
}
