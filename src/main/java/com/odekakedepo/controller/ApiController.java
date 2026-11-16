package com.odekakedepo.controller;

// 【controller】JavaScript（fetch）から呼ばれる API。画面ではなく JSON（データ）を返す
// @RestController：戻り値がそのまま JSON になる（@Controller ＋ @ResponseBody と同じ）
// ResponseEntity<?>：返すデータと HTTP のステータス（200 成功・201 作成・400 入力エラー・404 見つからない）をまとめたもの

import com.odekakedepo.service.OutingService;
import com.odekakedepo.service.PackingItemService;
import com.odekakedepo.service.WishService;
import com.odekakedepo.service.ScheduleService;
import com.odekakedepo.form.ScheduleForm;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// JavaScript（fetch）から呼ぶ API。画面は返さず JSON を返す（再読み込みなしの操作）
@RestController
public class ApiController {

    // 使うサービス
    private final OutingService outingService;
    private final PackingItemService itemService;
    private final WishService wishService;
    private final ScheduleService scheduleService;

    // コンストラクタ：Spring が必要なサービスを自動で渡してくれる（DI）
    public ApiController(OutingService outingService, PackingItemService itemService, WishService wishService,
                         ScheduleService scheduleService) {
        this.outingService = outingService;
        this.itemService = itemService;
        this.wishService = wishService;
        this.scheduleService = scheduleService;
    }

    // JavaScript から送られてくる JSON を受け取る入れ物。{"name": "水筒", "quantity": 2} の形が、自動でこのレコードになる
    public record TextRequest(String name) {}
    public record ItemRequest(String name, Integer quantity) {}
    public record PreparedRequest(Boolean prepared) {}
    public record QuantityRequest(Integer quantity) {}
    public record PrepRequest(String method) {}
    public record ScheduleRequest(String startTime, String content, String place) {}

    // エラーのときの返事を作る。HTTP のステータス（400：入力がおかしい、404：見つからない）と、メッセージの JSON
    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }

    // A01 持ち物を追加。@RequestBody：送られてきた JSON を ItemRequest にして受け取る
    // 成功したら 201（作成した）と、追加した持ち物を JSON で返す（画面に1行足すため）
    @PostMapping("/api/outings/{outingId}/items")
    public ResponseEntity<?> addItem(@PathVariable long outingId, @RequestBody ItemRequest req) {
        String name = req.name() == null ? "" : req.name().trim();
        int quantity = req.quantity() == null ? 1 : req.quantity();
        if (name.isEmpty() || name.length() > 100) {
            return error(HttpStatus.BAD_REQUEST, "持ち物の名前を100文字以内で入力してください");
        }
        if (quantity < PackingItemService.MIN_QUANTITY || quantity > PackingItemService.MAX_QUANTITY) {
            return error(HttpStatus.BAD_REQUEST, "個数は1〜99で入力してください");
        }
        if (outingService.findById(outingId).isEmpty()) {
            return error(HttpStatus.NOT_FOUND, "計画が見つかりません");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(itemService.add(outingId, name, quantity));
    }

    // A06 個数を変える。合計金額も変わるので、新しい合計とまだ買っていない金額も返す
    @PatchMapping("/api/items/{itemId}/quantity")
    public ResponseEntity<?> quantity(@PathVariable long itemId, @RequestBody QuantityRequest req) {
        var item = itemService.findById(itemId);
        if (item.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, "持ち物が見つかりません");
        }
        if (req.quantity() == null || !itemService.updateQuantity(itemId, req.quantity())) {
            return error(HttpStatus.BAD_REQUEST, "個数は1〜99で入力してください");
        }
        long outingId = item.get().outingId();
        return ResponseEntity.ok(Map.of("quantity", req.quantity(),
                "total", itemService.totalPrice(outingId), "remaining", itemService.remainingPrice(outingId)));
    }

    // A02 持ち物を削除
    @DeleteMapping("/api/items/{itemId}")
    public ResponseEntity<?> deleteItem(@PathVariable long itemId) {
        if (!itemService.delete(itemId)) {
            return error(HttpStatus.NOT_FOUND, "持ち物が見つかりません");
        }
        return ResponseEntity.ok(Map.of("result", "ok"));
    }

    // A03 準備チェックを付ける・外す（チェックなし＝買い物リストに出る）
    @PatchMapping("/api/items/{itemId}/prepared")
    public ResponseEntity<?> prepared(@PathVariable long itemId, @RequestBody PreparedRequest req) {
        if (req.prepared() == null) {
            return error(HttpStatus.BAD_REQUEST, "prepared を指定してください");
        }
        if (!itemService.updatePrepared(itemId, req.prepared())) {
            return error(HttpStatus.NOT_FOUND, "持ち物が見つかりません");
        }
        return ResponseEntity.ok(Map.of("result", "ok"));
    }

    // A04 やりたいことを追加
    @PostMapping("/api/outings/{outingId}/wishes")
    public ResponseEntity<?> addWish(@PathVariable long outingId, @RequestBody TextRequest req) {
        String content = req.name() == null ? "" : req.name().trim();
        if (content.isEmpty() || content.length() > 100) {
            return error(HttpStatus.BAD_REQUEST, "やりたいことを100文字以内で入力してください");
        }
        if (outingService.findById(outingId).isEmpty()) {
            return error(HttpStatus.NOT_FOUND, "計画が見つかりません");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(wishService.add(outingId, content));
    }

    // A05 やりたいことを削除
    @DeleteMapping("/api/wishes/{wishId}")
    public ResponseEntity<?> deleteWish(@PathVariable long wishId) {
        if (!wishService.delete(wishId)) {
            return error(HttpStatus.NOT_FOUND, "やりたいことが見つかりません");
        }
        return ResponseEntity.ok(Map.of("result", "ok"));
    }

    // A07 用意のしかたを決める（store＝お店で買う／other＝別で準備する）。変えたあとの持ち物を返す
    @PatchMapping("/api/items/{itemId}/prep")
    public ResponseEntity<?> prep(@PathVariable long itemId, @RequestBody PrepRequest req) {
        var item = itemService.findById(itemId);
        if (item.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, "持ち物が見つかりません");
        }
        if (!itemService.setPrepMethod(item.get().outingId(), itemId, req.method())) {
            return error(HttpStatus.BAD_REQUEST, "用意のしかたを変えられませんでした（準備済みの持ち物は変えられません）");
        }
        return ResponseEntity.ok(itemService.findById(itemId).orElseThrow());
    }

    // A08 予定を追加する（再読み込みなし）。並べ直した予定の一覧を返す
    // startTime は "10:00" の形か空。やることは必須100文字まで、場所は100文字まで
    @PostMapping("/api/outings/{outingId}/schedules")
    public ResponseEntity<?> addSchedule(@PathVariable long outingId, @RequestBody ScheduleRequest req) {
        if (outingService.findById(outingId).isEmpty()) {
            return error(HttpStatus.NOT_FOUND, "計画が見つかりません");
        }
        String content = req.content() == null ? "" : req.content().trim();
        if (content.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "やることを入力してください");
        }
        if (content.length() > 100) {
            return error(HttpStatus.BAD_REQUEST, "やることは100文字以内で入力してください");
        }
        if (req.place() != null && req.place().trim().length() > 100) {
            return error(HttpStatus.BAD_REQUEST, "場所は100文字以内で入力してください");
        }
        ScheduleForm form = new ScheduleForm();
        try {
            form.setStartTime(req.startTime() == null || req.startTime().isBlank() ? null : LocalTime.parse(req.startTime()));
        } catch (DateTimeParseException e) {
            return error(HttpStatus.BAD_REQUEST, "時間は「10:00」の形で入力してください");
        }
        form.setContent(content);
        form.setPlace(req.place());
        scheduleService.add(outingId, form);
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduleService.findByOutingId(outingId));
    }

    // A09 予定を削除する（再読み込みなし）。並べ直した予定の一覧を返す
    @DeleteMapping("/api/outings/{outingId}/schedules/{scheduleId}")
    public ResponseEntity<?> deleteSchedule(@PathVariable long outingId, @PathVariable long scheduleId) {
        if (outingService.findById(outingId).isEmpty()) {
            return error(HttpStatus.NOT_FOUND, "計画が見つかりません");
        }
        scheduleService.delete(outingId, scheduleId);
        return ResponseEntity.ok(scheduleService.findByOutingId(outingId));
    }
}
