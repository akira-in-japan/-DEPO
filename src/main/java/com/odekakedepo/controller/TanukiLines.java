package com.odekakedepo.controller;

// 計画一覧の狸が話すセリフを作るクラス（画面のための文章づくりなので controller パッケージに置いている）
// ページでやること（これからの計画と思い出を見る・開く・写真を共有する）に合わせて話す
// 順番：あいさつ → 急ぎのお知らせ → 準備の声かけ → 思い出 → ページの使い方（当てはまるものだけ、最大5つ）

import com.odekakedepo.entity.Outing;
import com.odekakedepo.entity.OutingSummary;
import com.odekakedepo.entity.PackingItem;
import com.odekakedepo.entity.Schedule;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TanukiLines {

    // 一度に話すセリフの最大数
    static final int MAX_LINES = 5;
    // 「もうすぐ消える」とみなす写真の残り日数
    static final int PHOTO_WARN_DAYS = 3;
    // ページの使い方のヒント（空いている数だけ、順番に足す）
    static final List<String> TIPS = List.of(
            "カードを押すと、計画を開いて編集できるよ",
            "思い出を開くと、写真や動画のアップロードと共有ができるよ",
            "上のタブで、キャンプ・旅行・ピクニックに分けて見られるよ");

    private TanukiLines() {
    }

    // 計画一覧の狸のセリフ
    // undecided：計画ごとの決まっていない持ち物の数、photos：計画ごとの [写真の件数, いちばん早く消える日時]
    public static List<String> forList(String nickname, LocalTime now, List<OutingSummary> upcoming,
                                       List<OutingSummary> memories, Map<Long, Long> undecided,
                                       Map<Long, Object[]> photos) {
        List<String> lines = new ArrayList<>();
        // 1. あいさつ（時間帯で変える）
        String hello = now.isBefore(LocalTime.of(10, 0)) && now.isAfter(LocalTime.of(4, 59)) ? "おはよう"
                : now.isBefore(LocalTime.of(18, 0)) && now.isAfter(LocalTime.of(9, 59)) ? "こんにちは" : "こんばんは";
        lines.add(hello + "、" + (nickname == null || nickname.isEmpty() ? "ゲスト" : nickname) + "さん！");

        if (upcoming.isEmpty() && memories.isEmpty()) {
            lines.add("まだ計画がないよ。上のメニューの「計画をつくる」から始めよう！");
            return lines;
        }
        // 2. 急ぎのお知らせ：今日・明日の出発
        if (!upcoming.isEmpty() && upcoming.get(0).daysLeft() <= 1) {
            OutingSummary next = upcoming.get(0);
            lines.add(next.isToday() ? "「" + next.outing().title() + "」は今日だね！いってらっしゃい！"
                    : "「" + next.outing().title() + "」は明日だね！持ち物は大丈夫？");
        }
        // 2. 急ぎのお知らせ：もうすぐ消える写真
        for (OutingSummary m : memories) {
            Object[] p = photos.get(m.outing().id());
            if (p != null) {
                long days = Math.max(0, ChronoUnit.DAYS.between(LocalDateTime.now(), (LocalDateTime) p[1]));
                if (days <= PHOTO_WARN_DAYS) {
                    lines.add("「" + m.outing().title() + "」の写真が、" + (days == 0 ? "今日" : "あと" + days + "日で")
                            + "消えちゃうよ。保存してね");
                    break;
                }
            }
        }
        // 3. 準備の声かけ（いちばん出発が近い計画）
        if (!upcoming.isEmpty()) {
            OutingSummary next = upcoming.get(0);
            String t = "「" + next.outing().title() + "」";
            long u = undecided.getOrDefault(next.outing().id(), 0L);
            int notReady = next.itemCount() - next.preparedCount();
            if (u > 0) {
                lines.add(t + "は、どう用意するか決まっていない持ち物が" + u + "つあるよ");
            } else if (notReady > 0) {
                lines.add(t + "は、まだ準備できていないものが" + notReady + "つあるよ");
            } else if (next.itemCount() > 0) {
                lines.add(t + "は準備ばっちり！楽しんできてね");
            } else {
                lines.add(t + "の持ち物リストがまだ空っぽだよ。テンプレートから選ぶと早いよ");
            }
        }
        // 4. 思い出：写真がまだない思い出
        for (OutingSummary m : memories) {
            if (!photos.containsKey(m.outing().id())) {
                lines.add("「" + m.outing().title() + "」の写真はまだないよ。思い出をアップロードしよう");
                break;
            }
        }
        // 5. ページの使い方（空いている数だけ）
        for (String tip : TIPS) {
            if (lines.size() >= MAX_LINES) {
                break;
            }
            lines.add(tip);
        }
        return lines.size() > MAX_LINES ? lines.subList(0, MAX_LINES) : lines;
    }

    // カードにマウスを乗せた（スマホはタップした）ときのセリフ：{計画ID: セリフ}
    public static Map<Long, String> forCards(List<OutingSummary> upcoming, List<OutingSummary> memories,
                                             Map<Long, Long> undecided, Map<Long, Object[]> photos) {
        Map<Long, String> says = new HashMap<>();
        for (OutingSummary s : upcoming) {
            String text = "「" + s.outing().title() + "」は" + (s.isToday() ? "今日だね！" : "あと" + s.daysLeft() + "日、")
                    + "準備は" + s.progress() + "％だよ";
            long u = undecided.getOrDefault(s.outing().id(), 0L);
            if (u > 0) {
                text += "。決まっていない持ち物が" + u + "つあるよ";
            }
            says.put(s.outing().id(), text);
        }
        for (OutingSummary m : memories) {
            Object[] p = photos.get(m.outing().id());
            if (p == null) {
                says.put(m.outing().id(), "「" + m.outing().title() + "」の写真はまだないよ。思い出を開いてアップロードしよう");
            } else {
                long days = Math.max(0, ChronoUnit.DAYS.between(LocalDateTime.now(), (LocalDateTime) p[1]));
                says.put(m.outing().id(), "「" + m.outing().title() + "」の写真・動画は" + p[0] + "件あるよ"
                        + (days <= PHOTO_WARN_DAYS ? "。もうすぐ消えるものがあるから、保存してね" : ""));
            }
        }
        return says;
    }

    // 計画詳細のでぽんたのセリフ（準備状況の案内）
    public static List<String> forDetail(Outing outing, long daysLeft,
                                         List<PackingItem> items,
                                         List<Schedule> schedules, int total) {
        List<String> lines = new ArrayList<>();
        String t = "「" + outing.title() + "」";
        long done = items.stream().filter(PackingItem::prepared).count();
        int pct = items.isEmpty() ? 0 : Math.round(done * 100f / items.size());
        long undecided = items.stream().filter(PackingItem::undecided).count();
        long storeCount = items.stream().filter(i -> !i.prepared() && i.byStore()).count();
        lines.add(t + (daysLeft == 0 ? "は今日だね！" : "まで、あと" + daysLeft + "日だよ！"));
        if (items.isEmpty()) {
            lines.add("持ち物リストがまだ空っぽだよ。「テンプレートから追加」を使うと早いよ");
        } else {
            lines.add("準備は" + pct + "％だよ（準備済み" + done + "つ・まだ" + (items.size() - done) + "つ）");
            if (undecided > 0) {
                lines.add("どう用意するか決まっていない持ち物が" + undecided + "つあるよ。「DEPOの商品で準備しよう」で決めてね");
            } else if (storeCount > 0) {
                lines.add("DEPOで買うものは" + storeCount + "つ、合計" + String.format("%,d", total) + "円だよ");
            } else if (done == items.size()) {
                lines.add("準備ばっちり！楽しんできてね");
            }
        }
        if (!schedules.isEmpty()) {
            var first = schedules.get(0);
            lines.add(first.startTime() == null ? "予定表に「" + first.content() + "」が入っているよ"
                    : "最初の予定は " + first.startTime() + " の「" + first.content() + "」だよ");
        } else {
            lines.add("予定表に、時間とやることを入れてみよう。時間は決まっていなくても大丈夫だよ");
        }
        lines.add("足りないものは「DEPOの商品で準備しよう」から探してみてね");
        return lines.size() > MAX_LINES ? lines.subList(0, MAX_LINES) : lines;
    }

    // DEPO商品一覧のでぽんたのセリフ
    public static List<String> forProducts(String heading, int count) {
        return List.of(
                heading + "を" + count + "件表示しているよ",
                "上の検索ボックスで、アイテムの名前やキーワードから探せるよ",
                "キャンプ・旅行・ピクニックのボタンで、種類ごとに見られるよ",
                "気になる商品は、右上のハートでお気に入りに入れておこう",
                "左の「特集・テーマ」も見てみてね。おすすめのセットがあるよ");
    }

    // お店の商品を選ぶ画面のでぽんたのセリフ
    public static List<String> forSelect(Outing outing, PackingItem selected,
                                         long undecided) {
        List<String> lines = new ArrayList<>();
        if (undecided == 0) {
            // ぜんぶ決まったら、次にすること（商品をもっと見る／しおりを作る）を案内する
            lines.add("ぜんぶ決まったよ！このままDEPOの商品を見るか、計画に戻ってしおりを作ってみよう");
        }
        if (selected != null) {
            lines.add("「" + selected.name() + "」に合う商品の「「" + selected.name() + "」用に選ぶ」を押してね");
        }
        if (undecided > 0) {
            lines.add("決まっていない持ち物があと" + undecided + "つあるよ");
        }
        lines.add("DEPO にないものは、左で「別で準備」を選んでね。一覧から外れるよ");
        lines.add("気になる商品は、右上のハートでお気に入りに入れておこう");
        lines.add("下の「DEPOで買うもの」で、金額と合計を確かめられるよ");
        return lines;
    }
}
