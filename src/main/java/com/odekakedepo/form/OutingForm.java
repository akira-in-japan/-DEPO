package com.odekakedepo.form;

// 【form】計画をつくる・編集する画面（S02）から送られてくる入力値の入れ物
// Bean Validation：項目の上の @NotBlank などの「アノテーション」で入力チェックのルールを書く。
// Controller で @Valid を付けると、Spring がこのルールで自動的にチェックし、結果を BindingResult に入れてくれる。
// ゲッター・セッター（getXxx / setXxx）は、画面のフォームの値を Spring が出し入れするために必要。

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

// 計画をつくる・編集する画面（S02）の入力値。
// 日付の「今日以降」のチェックは、新規と編集でルールが違うので OutingController で行う。
public class OutingForm {

    // タイトル：空はだめ（@NotBlank は空白だけも不可）、100文字まで
    @NotBlank(message = "タイトルを入力してください")
    @Size(max = 100, message = "タイトルは100文字以内で入力してください")
    private String title;

    // 日付：必須。@DateTimeFormat で "2026-11-12" の文字を LocalDate（日付の型）に変換する
    // 「今日以降」のチェックは新規と編集でルールが違うので、OutingController で行う
    @NotNull(message = "日付を選んでください")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate outingDate;

    // 行き先：必須、100文字まで
    @NotBlank(message = "行き先を入力してください")
    @Size(max = 100, message = "行き先は100文字以内で入力してください")
    private String destination;

    // 人数：必須、1以上。最初に表示する値は 2
    @NotNull(message = "人数を入力してください")
    @Min(value = 1, message = "人数は1人以上にしてください")
    private Integer participants = 2;

    // 種類：必須。@Pattern でキャンプ・旅行・ピクニックのどれかだけを受け付ける
    @NotBlank(message = "種類を選んでください")
    @Pattern(regexp = "キャンプ|旅行|ピクニック", message = "種類はキャンプ・旅行・ピクニックから選んでください")
    private String category = "キャンプ";

    // メモ：空でもよい、500文字まで
    @Size(max = 500, message = "メモは500文字以内で入力してください")
    private String memo;

    // やりたいこと：新規作成のときに複数入力できる。1件100文字まで
    private List<@Size(max = 100, message = "やりたいことは1件100文字以内で入力してください") String> wishes = new ArrayList<>();

    // テンプレートで選んだ持ち物の名前（新規作成のときだけ）
    private List<String> templateItems = new ArrayList<>();

    // 前後の空白を取る小さな道具。null のときは null のまま返す
    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    // ---- ここから下はゲッター・セッター。セッターでは前後の空白を取ってから入れる ----
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = trim(title); }
    public LocalDate getOutingDate() { return outingDate; }
    public void setOutingDate(LocalDate outingDate) { this.outingDate = outingDate; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = trim(destination); }
    public Integer getParticipants() { return participants; }
    public void setParticipants(Integer participants) { this.participants = participants; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo == null || memo.isBlank() ? null : memo.trim(); }
    public List<String> getWishes() { return wishes; }
    public void setWishes(List<String> wishes) { this.wishes = wishes == null ? new ArrayList<>() : wishes; }
    public List<String> getTemplateItems() { return templateItems; }
    public void setTemplateItems(List<String> templateItems) { this.templateItems = templateItems == null ? new ArrayList<>() : templateItems; }

    // 空の行を除いたやりたいことだけを返す。
    // stream() は「リストを流れ作業で処理する」書き方。map で変換、filter で絞り込み、toList でリストに戻す
    public List<String> cleanWishes() {
        return wishes.stream().map(String::trim).filter(w -> !w.isEmpty()).toList();
    }
}
