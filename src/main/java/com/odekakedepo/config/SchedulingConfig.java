package com.odekakedepo.config;

// 【config】アプリ全体の設定
// @EnableScheduling を付けると、@Scheduled を付けたメソッドが決まった間隔で自動実行される
// （PhotoService の「期限切れの写真を消す」処理のため）

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// 期限切れの写真を定期的に消すために、スケジュール実行を有効にする
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
