package com.odekakedepo.form;

// 【form】予定表に予定を1件追加するときの入力値の入れ物
// Bean Validation：項目の上の @NotBlank などの「アノテーション」で入力チェックのルールを書く。
// Controller で @Valid を付けると、Spring がこのルールで自動的にチェックし、結果を BindingResult に入れてくれる。
// ゲッター・セッター（getXxx / setXxx）は、画面のフォームの値を Spring が出し入れするために必要。

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import org.springframework.format.annotation.DateTimeFormat;

// 予定表に予定を1件追加するときの入力値。時間と場所は空欄でもよい
public class ScheduleForm {

    // 時間："10:00" の文字を LocalTime（時刻の型）に変換する。空欄なら null（時間未定）
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime startTime;

    // やること：必須、100文字まで
    @NotBlank(message = "やることを入力してください")
    @Size(max = 100, message = "やることは100文字以内で入力してください")
    private String content;

    // 場所：空でもよい、100文字まで
    @Size(max = 100, message = "場所は100文字以内で入力してください")
    private String place;

    // ---- ゲッター・セッター。空白だけの場所は null にする ----
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content == null ? null : content.trim(); }
    public String getPlace() { return place; }
    public void setPlace(String place) { this.place = place == null || place.isBlank() ? null : place.trim(); }
}
