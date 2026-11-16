package com.odekakedepo.controller;

// S03 計画詳細と、その画面から行う操作（テンプレート・商品を外す・予定表）
// 【controller】ブラウザからのリクエスト（URL）を受け取り、service に処理を頼んで、表示する画面（テンプレート）を決めるクラス
// @Controller：戻り値の文字列が「表示するテンプレートの名前」になる（例："outings/list" → templates/outings/list.html）
// "redirect:/…" を返すと、ブラウザにその URL へ移動してもらう（保存のあとに使うと、再読み込みで二重に保存されない）

import com.odekakedepo.entity.ItemTemplate;
import com.odekakedepo.entity.Outing;
import com.odekakedepo.entity.PackingItem;
import com.odekakedepo.form.ScheduleForm;
import com.odekakedepo.repository.ItemTemplateRepository;
import com.odekakedepo.repository.ProductRepository;
import com.odekakedepo.service.FavoriteService;
import com.odekakedepo.service.OutingService;
import com.odekakedepo.service.PackingItemService;
import com.odekakedepo.service.ScheduleService;
import com.odekakedepo.service.WishService;
import com.odekakedepo.util.NameNormalizer;
import com.odekakedepo.web.BrowserId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// S03 計画詳細と、その画面から行う操作（テンプレート・買い物リストから戻す・予定表）
@Controller
public class OutingDetailController {

    // この画面で使うサービスと repository
    private final OutingService outingService;
    private final PackingItemService itemService;
    private final WishService wishService;
    private final ScheduleService scheduleService;
    private final ItemTemplateRepository templateRepository;
    private final ProductRepository productRepository;
    private final FavoriteService favoriteService;
    private final BrowserId browserId;

    // コンストラクタ：Spring が必要なサービスを自動で渡してくれる（DI）
    public OutingDetailController(OutingService outingService, PackingItemService itemService,
                                  WishService wishService, ScheduleService scheduleService,
                                  ItemTemplateRepository templateRepository, ProductRepository productRepository,
                                  FavoriteService favoriteService, BrowserId browserId) {
        this.outingService = outingService;
        this.itemService = itemService;
        this.wishService = wishService;
        this.scheduleService = scheduleService;
        this.templateRepository = templateRepository;
        this.productRepository = productRepository;
        this.favoriteService = favoriteService;
        this.browserId = browserId;
    }

    // U08 計画詳細。思い出なら思い出の画面へ移動する
    // Model：画面（Thymeleaf）に渡すデータの入れ物。model.addAttribute("名前", 値) で入れると、画面で ${名前} として使える
    @GetMapping("/outings/{outingId}")
    public String detail(@PathVariable long outingId, Model model,
                         HttpServletRequest request, HttpServletResponse response) {
        Outing outing = find(outingId);
        if (OutingController.isMemory(outing)) {
            return "redirect:/outings/" + outingId + "/memory"; // 思い出は、しおり・写真・共有だけ
        }
        // 持ち物・準備済みの数・今ある持ち物の名前（書き方をそろえたもの）
        List<PackingItem> items = itemService.findByOutingId(outingId);
        long prepared = items.stream().filter(PackingItem::prepared).count();
        Set<String> haveNames = items.stream().map(i -> NameNormalizer.normalize(i.name())).collect(Collectors.toSet());

        // 画面に渡すもの：計画・出発までの日数・やりたいこと・予定・持ち物・進み具合・買い物リスト・金額・気になる・おすすめ
        model.addAttribute("outing", outing);
        model.addAttribute("daysLeft", ChronoUnit.DAYS.between(LocalDate.now(), outing.outingDate()));
        model.addAttribute("wishes", wishService.findByOutingId(outingId));
        model.addAttribute("schedules", scheduleService.findByOutingId(outingId));
        model.addAttribute("items", items);
        model.addAttribute("preparedCount", prepared);
        model.addAttribute("progress", items.isEmpty() ? 0 : Math.round(prepared * 100f / items.size()));
        model.addAttribute("shoppingList", itemService.findShoppingList(outingId));
        model.addAttribute("total", itemService.totalPrice(outingId));
        model.addAttribute("remaining", itemService.remainingPrice(outingId));
        // お気に入り：このブラウザのお気に入り（計画をまたいで共通）。ここから計画の買い物リストに入れられる
        model.addAttribute("favorites", favoriteService.findProducts(browserId.get(request, response)));
        model.addAttribute("recommendProducts", itemService.recommendProducts(outing, items));
        model.addAttribute("recommendOthers", itemService.recommendFromOthers(outing, items));
        // テンプレートの一覧。すでにある持ち物は have=true（「追加ずみ」で選べない）
        model.addAttribute("templateRows", templateRepository.findByCategory(outing.category()).stream()
                .map(t -> Map.of("group", t.groupName(), "name", t.name(),
                        "have", haveNames.contains(NameNormalizer.normalize(t.name()))))
                .toList());
        // 持ち物の入力欄に出す候補（お店の商品名＋テンプレートの名前）
        Set<String> suggest = new LinkedHashSet<>();
        productRepository.findAll().forEach(p -> suggest.add(p.name()));
        templateRepository.findAll().stream().map(ItemTemplate::name).forEach(suggest::add);
        model.addAttribute("suggestions", suggest);
        // 予定の入力でエラーがあったときは、その入力値（フラッシュ属性）を使うので、なければ空のフォームを入れる
        // でぽんたのセリフ（準備状況の案内）
        model.addAttribute("tanukiLines", TanukiLines.forDetail(outing,
                ChronoUnit.DAYS.between(LocalDate.now(), outing.outingDate()), items,
                scheduleService.findByOutingId(outingId), itemService.totalPrice(outingId)));
        if (!model.containsAttribute("scheduleForm")) {
            model.addAttribute("scheduleForm", new ScheduleForm());
        }
        return "outings/detail";
    }

    // テンプレートからまとめて追加。@RequestParam で、チェックした名前（names）をリストで受け取る
    @PostMapping("/outings/{outingId}/items/template")
    public String addFromTemplate(@PathVariable long outingId,
                                 @RequestParam(name = "names", required = false) List<String> names) {
        find(outingId);
        if (names != null) {
            itemService.addMany(outingId, names);
        }
        return "redirect:/outings/" + outingId + "#items";
    }

    // U09 選んだ商品を外す（持ち物は残る）
    @PostMapping("/outings/{outingId}/items/{itemId}/unlink")
    public String unlink(@PathVariable long outingId, @PathVariable long itemId) {
        itemService.unlink(outingId, itemId);
        return "redirect:/outings/" + outingId + "#shopping";
    }

    // 予定を追加
    // 予定を追加。エラーのときは RedirectAttributes のフラッシュ属性でメッセージを次の画面に1回だけ渡す
    @PostMapping("/outings/{outingId}/schedules")
    public String addSchedule(@PathVariable long outingId, @Valid @ModelAttribute ScheduleForm scheduleForm,
                              BindingResult result, RedirectAttributes ra) {
        find(outingId);
        if (result.hasErrors()) {
            ra.addFlashAttribute("scheduleError", result.getAllErrors().get(0).getDefaultMessage());
            ra.addFlashAttribute("scheduleForm", scheduleForm);
        } else {
            scheduleService.add(outingId, scheduleForm);
        }
        return "redirect:/outings/" + outingId + "#schedule";
    }

    // 予定を削除
    // 予定を削除
    @PostMapping("/outings/{outingId}/schedules/{scheduleId}/delete")
    public String deleteSchedule(@PathVariable long outingId, @PathVariable long scheduleId) {
        scheduleService.delete(outingId, scheduleId);
        return "redirect:/outings/" + outingId + "#schedule";
    }

    // 計画を探し、なければ 404 にする
    private Outing find(long id) {
        return outingService.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
