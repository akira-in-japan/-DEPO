package com.odekakedepo.controller;

// S01 計画一覧・S02 計画をつくる／編集する・計画の削除
// 【controller】ブラウザからのリクエスト（URL）を受け取り、service に処理を頼んで、表示する画面（テンプレート）を決めるクラス
// @Controller：戻り値の文字列が「表示するテンプレートの名前」になる（例："outings/list" → templates/outings/list.html）
// "redirect:/…" を返すと、ブラウザにその URL へ移動してもらう（保存のあとに使うと、再読み込みで二重に保存されない）

import com.odekakedepo.entity.Outing;
import com.odekakedepo.entity.OutingSummary;
import com.odekakedepo.form.OutingForm;
import com.odekakedepo.service.OutingService;
import com.odekakedepo.service.PackingItemService;
import com.odekakedepo.service.PhotoService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;

// S01 計画一覧、S02 計画をつくる・編集する、計画の削除
@Controller
public class OutingController {

    // 使うサービス
    private final OutingService outingService;
    private final PackingItemService itemService;
    private final PhotoService photoService;

    // コンストラクタ：Spring が必要なサービスを自動で渡してくれる（DI）
    public OutingController(OutingService outingService, PackingItemService itemService, PhotoService photoService) {
        this.outingService = outingService;
        this.itemService = itemService;
        this.photoService = photoService;
    }

    // U02 計画一覧：これからの計画と思い出を画面に渡す
    // Model：画面（Thymeleaf）に渡すデータの入れ物。model.addAttribute("名前", 値) で入れると、画面で ${名前} として使える
    @GetMapping("/outings")
    public String list(Model model) {
        List<OutingSummary> upcoming = outingService.findUpcoming();
        // 種類ごとの件数（タブの「キャンプ（1）」などに使う）。groupingBy で種類ごとにまとめ、counting で数える
        Map<String, Long> kindCounts = upcoming.stream()
                .collect(Collectors.groupingBy(s -> s.outing().kindKey(), Collectors.counting()));
        model.addAttribute("upcoming", upcoming);
        model.addAttribute("memories", outingService.findMemories());
        model.addAttribute("kindCounts", kindCounts);
        List<OutingSummary> memories = outingService.findMemories();
        Map<Long, Object[]> photos = photoService.summaryByOuting();
        // 計画ごとの「決まっていない持ち物」の数（用意のしかた・商品）
        Map<Long, Long> undecided = new HashMap<>();
        upcoming.forEach(u -> undecided.put(u.outing().id(), itemService.countUndecided(u.outing().id())));
        String nickname = (String) model.getAttribute("nickname"); // CommonModelAdvice が入れたニックネーム
        model.addAttribute("tanukiLines", TanukiLines.forList(nickname, LocalTime.now(), upcoming, memories, undecided, photos));
        model.addAttribute("cardSays", TanukiLines.forCards(upcoming, memories, undecided, photos));
        return "outings/list";
    }

    // 計画をつくる画面を表示する。空のフォーム（OutingForm）を渡す
    @GetMapping("/outings/new")
    public String newForm(Model model) {
        model.addAttribute("outingForm", new OutingForm());
        return showForm(model, null);
    }

    // U05 計画を登録する
    // @Valid：OutingForm のルールで入力チェックする。結果は BindingResult（result）に入る
    // @ModelAttribute：フォームの値を OutingForm に入れて受け取る
    @PostMapping("/outings")
    public String create(@Valid @ModelAttribute OutingForm outingForm, BindingResult result, Model model) {
        // 新規は「今日以降」だけ。rejectValue で、日付の項目にエラーを追加する
        if (outingForm.getOutingDate() != null && outingForm.getOutingDate().isBefore(LocalDate.now())) {
            result.rejectValue("outingDate", "past", "今日以降の日付を選んでください");
        }
        // エラーがあれば保存せず、入力した値とメッセージを残したまま同じ画面を表示する
        if (result.hasErrors()) {
            return showForm(model, null);
        }
        // 保存して、できた計画の詳細画面へ移動する
        long id = outingService.create(outingForm);
        return "redirect:/outings/" + id;
    }

    // U06 編集画面。@PathVariable は URL の {outingId} の部分を受け取る
    // 思い出（日付が過ぎた計画）は編集できないので、思い出の画面へ移動する
    @GetMapping("/outings/{outingId}/edit")
    public String editForm(@PathVariable long outingId, Model model) {
        Outing o = find(outingId);
        if (isMemory(o)) {
            return "redirect:/outings/" + outingId + "/memory"; // 思い出は編集できない
        }
        // 今の計画の値をフォームに入れて表示する
        OutingForm form = new OutingForm();
        form.setTitle(o.title());
        form.setOutingDate(o.outingDate());
        form.setDestination(o.destination());
        form.setParticipants(o.participants());
        form.setCategory(o.category());
        form.setMemo(o.memo());
        model.addAttribute("outingForm", form);
        return showForm(model, o);
    }

    // U07 計画を更新。日付を変えていなければ過去の日付でも保存できる（思い出に書き足せるように）
    // U07 計画を更新する。思い出は更新できない
    @PostMapping("/outings/{outingId}/edit")
    public String update(@PathVariable long outingId, @Valid @ModelAttribute OutingForm outingForm,
                         BindingResult result, Model model) {
        Outing o = find(outingId);
        if (isMemory(o)) {
            return "redirect:/outings/" + outingId + "/memory";
        }
        // 日付を変える場合は、今日以降の日付だけ
        LocalDate date = outingForm.getOutingDate();
        if (date != null && !date.equals(o.outingDate()) && date.isBefore(LocalDate.now())) {
            result.rejectValue("outingDate", "past", "日付を変えるときは、今日以降の日付を選んでください");
        }
        if (result.hasErrors()) {
            return showForm(model, o);
        }
        outingService.update(outingId, outingForm);
        return "redirect:/outings/" + outingId;
    }

    // U03 計画を削除して、一覧へ戻る
    @PostMapping("/outings/{outingId}/delete")
    public String delete(@PathVariable long outingId) {
        outingService.delete(outingId);
        return "redirect:/outings";
    }

    // 計画をつくる・編集する画面で共通の準備。editing が null なら新規、そうでなければ編集
    private String showForm(Model model, Outing editing) {
        // 右側の狸が最初に話すセリフ（入力する欄を選ぶと、欄ごとの説明に変わる）
        model.addAttribute("guideLines", editing == null
                ? List.of("いっしょに計画をつくろう！まずはタイトルからだよ", "入力する欄をえらぶと、ぼくが説明するね")
                : List.of("計画を直すときは、変えたいところだけ書きかえてね", "やりたいことや持ち物は、計画詳細の画面で追加できるよ"));
        model.addAttribute("editing", editing);
        model.addAttribute("today", LocalDate.now());
        return "outings/form";
    }

    // 日付が過ぎた計画（思い出）か
    // 日付が今日より前なら思い出。ほかの Controller からも使う
    static boolean isMemory(Outing o) {
        return o.outingDate().isBefore(LocalDate.now());
    }

    // 計画を探し、なければ 404（ページが見つかりません）にする
    private Outing find(long id) {
        return outingService.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
