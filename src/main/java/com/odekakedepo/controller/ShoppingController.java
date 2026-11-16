package com.odekakedepo.controller;

// S04 お店の商品を選ぶ画面と、そこからの操作（用意のしかた・商品のひも付け・これも買う・お気に入り）
// 【controller】ブラウザからのリクエスト（URL）を受け取り、service に処理を頼んで、表示する画面（テンプレート）を決めるクラス
// @Controller：戻り値の文字列が「表示するテンプレートの名前」になる（例："outings/list" → templates/outings/list.html）
// "redirect:/…" を返すと、ブラウザにその URL へ移動してもらう（保存のあとに使うと、再読み込みで二重に保存されない）

import com.odekakedepo.entity.Outing;
import com.odekakedepo.entity.PackingItem;
import com.odekakedepo.entity.Product;
import com.odekakedepo.repository.ProductRepository;
import com.odekakedepo.service.FavoriteService;
import com.odekakedepo.web.BrowserId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.odekakedepo.service.OutingService;
import com.odekakedepo.service.PackingItemService;
import com.odekakedepo.service.PackingItemService.LinkResult;
import com.odekakedepo.service.ProductSearchService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
public class ShoppingController {

    // URL の error=… の値と、画面に出すメッセージの対応表
    private static final Map<String, String> ERRORS = Map.of(
            "noitem", "先に左の一覧から持ち物を選んでください",
            "notfound", "その持ち物は見つかりませんでした",
            "prepared", "準備チェックが付いている持ち物は選べません",
            "noproduct", "その商品は見つかりませんでした");

    // 使うサービスと repository
    private final OutingService outingService;
    private final PackingItemService itemService;
    private final ProductRepository productRepository;
    // キーワード検索（ひらがな・カタカナ、言いかえもまとめて探す）
    private final ProductSearchService searchService;
    private final FavoriteService favoriteService;
    private final BrowserId browserId;

    // コンストラクタ：Spring が必要なサービスを自動で渡してくれる（DI）
    public ShoppingController(OutingService outingService, PackingItemService itemService,
                              ProductRepository productRepository, FavoriteService favoriteService,
                              BrowserId browserId,
                              ProductSearchService searchService) {
        this.searchService = searchService;
        this.outingService = outingService;
        this.itemService = itemService;
        this.productRepository = productRepository;
        this.favoriteService = favoriteService;
        this.browserId = browserId;
    }

    // U11 お店の商品を選ぶ画面（左にまだ準備できていない持ち物を固定表示）。思い出なら思い出の画面へ
    @GetMapping("/outings/{outingId}/products")
    public String show(@PathVariable long outingId,
                       @RequestParam(required = false) String category,
                       @RequestParam(required = false) Long itemId,
                       @RequestParam(required = false) String q,
                       @RequestParam(required = false) String error,
                       Model model, HttpServletRequest request, HttpServletResponse response) {
        Outing outing = outingService.findById(outingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (OutingController.isMemory(outing)) {
            return "redirect:/outings/" + outingId + "/memory";
        }
        boolean validCategory = category != null && ProductController.CATEGORIES.contains(category);
        // この計画の持ち物を1回だけ取り出し、そこから左の一覧・数・下のリストを作る
        List<PackingItem> items = itemService.findByOutingId(outingId);
        List<PackingItem> notPrepared = items.stream().filter(i -> !i.prepared()).toList();
        long undecided = items.stream().filter(PackingItem::undecided).count();
        // 左の一覧：まだ準備できていない持ち物のうち、「別で準備」にしたもの以外（別で準備にしたものは、下の折りたたみに分ける）
        List<PackingItem> unprepared = notPrepared.stream().filter(i -> !i.byOther()).toList();
        PackingItem selected = itemId == null ? null
                : unprepared.stream().filter(i -> i.id().equals(itemId)).findFirst().orElse(null);

        model.addAttribute("outing", outing);
        model.addAttribute("unprepared", unprepared);
        model.addAttribute("otherItems", notPrepared.stream().filter(PackingItem::byOther).toList());
        model.addAttribute("selectedItem", selected);
        // キーワード（q）があれば商品名・説明から探し、種類でも絞り込む
        String keyword = q == null ? "" : q.trim();
        List<Product> products = !keyword.isEmpty() ? searchService.search(keyword)
                : validCategory ? productRepository.findByCategory(category) : productRepository.findAll();
        if (!keyword.isEmpty() && validCategory) {
            products = products.stream().filter(p -> p.category().equals(category)).toList();
        }
        model.addAttribute("products", products);
        model.addAttribute("q", keyword);
        model.addAttribute("categories", ProductController.CATEGORIES);
        model.addAttribute("selectedCategory", validCategory ? category : "");
        model.addAttribute("total", itemService.totalPrice(outingId));
        model.addAttribute("favoriteIds", favoriteService.findProductIds(browserId.get(request, response)));
        model.addAttribute("errorMessage", error == null ? null : ERRORS.get(error));
        // 用意のしかたが決まっていない持ち物の数（「あと○つ」の表示用）
        model.addAttribute("undecidedCount", undecided);
        // ぜんぶ決まった（持ち物が1つ以上あり、決まっていないものが0）なら、でぽんたが次にすることを案内する
        model.addAttribute("allDecided", undecided == 0 && !items.isEmpty());
        // 下の「DEPOで買うもの」：商品を選んだ持ち物だけ
        model.addAttribute("depoList", items.stream().filter(i -> i.productId() != null).toList());
        // でぽんたのセリフと、商品ごとのひとこと（カードにマウスを乗せたとき）
        model.addAttribute("tanukiLines", TanukiLines.forSelect(outing, selected, undecided));
        Map<Long, String> productSays = new HashMap<>();
        for (Product p : products) {
            productSays.put(p.id(), "「" + p.name() + "」は" + String.format("%,d", p.price()) + "円。" + p.description() + "だよ");
        }
        model.addAttribute("productSays", productSays);
        return "products/select";
    }

    // U37 用意のしかたを決める（method：store＝お店で買う／other＝別で準備する）
    // お店で買うを選んだら、その持ち物を選んだ状態で戻る（右の商品から選べるように）。別で準備するなら、次に決める持ち物へ
    @PostMapping("/outings/{outingId}/items/{itemId}/prep")
    public String prep(@PathVariable long outingId, @PathVariable long itemId, @RequestParam String method,
                       @RequestParam(required = false) String category) {
        outingService.findById(outingId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        boolean ok = itemService.setPrepMethod(outingId, itemId, method);
        // 【バグ修正】以前は「!ok ? itemId : …」と書いていたため、itemId（long：null になれない型）に合わせて
        // 結果も long に変換され、次の持ち物がない（null）ときに NullPointerException でエラー画面になっていた。
        // Long（null になれる型）だけで計算するように直した
        Long next;
        if (!ok || "store".equals(method)) {
            next = Long.valueOf(itemId);
        } else {
            next = itemService.findNextUnselected(outingId).map(PackingItem::id).orElse(null);
        }
        // 次がない（ぜんぶ決まった）ときは、商品を選ぶ画面に「ぜんぶ決まったよ！」の案内が出る
        return backTo(outingId, null, category, next, ok ? null : "notfound");
    }

    // 「これも買う」：持ち物を選ばずに、商品をそのまま「DEPOで買うもの」へ
    @PostMapping("/outings/{outingId}/products/{productId}/add")
    public String addProduct(@PathVariable long outingId, @PathVariable long productId,
                             @RequestParam(required = false) String category,
                             @RequestParam(required = false) Long itemId,
                             @RequestParam(required = false) String back) {
        outingService.findById(outingId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        boolean ok = itemService.addProductToShopping(outingId, productId);
        return backTo(outingId, back, category, itemId, ok ? null : "noproduct");
    }

    // 気になるリストに入れる／外す（ハート）
    @PostMapping("/outings/{outingId}/favorites/{productId}")
    public String toggleFavorite(@PathVariable long outingId, @PathVariable long productId,
                                 @RequestParam(required = false) String category,
                                 @RequestParam(required = false) Long itemId,
                                 @RequestParam(required = false) String back,
                                 HttpServletRequest request, HttpServletResponse response) {
        outingService.findById(outingId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        boolean ok = favoriteService.toggle(browserId.get(request, response), productId);
        return backTo(outingId, back, category, itemId, ok ? null : "noproduct");
    }

    // 操作のあと、もとの画面（計画詳細か、商品を選ぶ画面）に戻る URL を作る
    // UriComponentsBuilder で、日本語などもきちんと URL 用に変換（エンコード）する
    private String backTo(long outingId, String back, String category, Long itemId, String error) {
        if ("detail".equals(back)) {
            return "redirect:/outings/" + outingId + "#favorites";
        }
        UriComponentsBuilder url = UriComponentsBuilder.fromPath("/outings/{outingId}/products");
        if (category != null && !category.isEmpty()) {
            url.queryParam("category", category);
        }
        if (itemId != null) {
            url.queryParam("itemId", itemId);
        }
        if (error != null) {
            url.queryParam("error", error);
        }
        return "redirect:" + url.buildAndExpand(outingId).encode().toUriString();
    }

    // U12 選んだ持ち物に商品をひも付ける。成功したら、次に商品が決まっていない持ち物を自動で選んだ状態で戻る
    @PostMapping("/outings/{outingId}/items/{itemId}/product")
    public String link(@PathVariable long outingId, @PathVariable long itemId,
                       @RequestParam long productId,
                       @RequestParam(required = false) String category) {
        LinkResult result = itemService.linkProduct(outingId, itemId, productId);
        UriComponentsBuilder url = UriComponentsBuilder.fromPath("/outings/{outingId}/products");
        if (category != null && !category.isEmpty()) {
            url.queryParam("category", category);
        }
        // 結果ごとに戻り先の URL を変える（-> は Java の新しい switch の書き方）
        switch (result) {
            case OK -> itemService.findNextUnselected(outingId)
                    .ifPresent(next -> url.queryParam("itemId", next.id())); // 次の「未選択」を自動で選ぶ
            case ITEM_NOT_FOUND -> url.queryParam("error", "notfound");
            case ALREADY_PREPARED -> url.queryParam("error", "prepared");
            case PRODUCT_NOT_FOUND -> url.queryParam("itemId", itemId).queryParam("error", "noproduct");
        }
        return "redirect:" + url.buildAndExpand(outingId).encode().toUriString();
    }
}
