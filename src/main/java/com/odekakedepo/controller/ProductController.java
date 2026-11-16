package com.odekakedepo.controller;

// S04 DEPO商品一覧（見るページ。操作はお気に入りのハートだけ）
// 左のメニュー（view）で表示を切りかえる：すべて／おすすめ（人気の商品）／特集・テーマ／お気に入り／買うものリスト／購入履歴
// 本文の上でキーワード検索（q）と、種類（キャンプ・旅行・ピクニック）の絞り込みができる
// 【controller】ブラウザからのリクエスト（URL）を受け取り、service に処理を頼んで、表示する画面（テンプレート）を決めるクラス

import com.odekakedepo.entity.OutingSummary;
import com.odekakedepo.entity.PackingItem;
import com.odekakedepo.entity.Product;
import com.odekakedepo.entity.Theme;
import com.odekakedepo.repository.PackingItemRepository;
import com.odekakedepo.repository.ProductRepository;
import com.odekakedepo.service.FavoriteService;
import com.odekakedepo.service.OutingService;
import com.odekakedepo.service.PackingItemService;
import com.odekakedepo.service.ProductSearchService;
import com.odekakedepo.service.ThemeService;
import com.odekakedepo.web.BrowserId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
public class ProductController {

    // 商品の種類（絞り込みのボタンに使う）。ほかの Controller からも使う
    static final List<String> CATEGORIES = List.of("キャンプ", "旅行", "ピクニック");
    // 左のメニューで選べる表示
    static final List<String> VIEWS = List.of("all", "recommend", "theme", "favorites", "shopping", "history");
    // おすすめ（人気の商品）に出す最大件数
    static final int POPULAR_LIMIT = 12;

    // 使う repository とサービス
    private final ProductRepository productRepository;
    // キーワード検索（ひらがな・カタカナ、言いかえもまとめて探す）
    private final ProductSearchService searchService;
    private final PackingItemRepository itemRepository;
    private final OutingService outingService;
    private final PackingItemService itemService;
    private final FavoriteService favoriteService;
    private final ThemeService themeService;
    private final BrowserId browserId;

    // コンストラクタ：Spring が必要なサービスを自動で渡してくれる（DI）
    public ProductController(ProductRepository productRepository, PackingItemRepository itemRepository,
                             OutingService outingService, PackingItemService itemService,
                             FavoriteService favoriteService, ThemeService themeService, BrowserId browserId,
                             ProductSearchService searchService) {
        this.productRepository = productRepository;
        this.searchService = searchService;
        this.itemRepository = itemRepository;
        this.outingService = outingService;
        this.itemService = itemService;
        this.favoriteService = favoriteService;
        this.themeService = themeService;
        this.browserId = browserId;
    }

    // U10 DEPO商品一覧
    // view：表示、theme：特集の id、q：キーワード、category：種類の絞り込み、done：直前の操作の結果。required = false は「なくてもよい」
    @GetMapping("/products")
    public String list(@RequestParam(defaultValue = "all") String view,
                       @RequestParam(required = false) Long theme,
                       @RequestParam(required = false) String q,
                       @RequestParam(required = false) String category,
                       @RequestParam(required = false) String done,
                       Model model, HttpServletRequest request, HttpServletResponse response) {
        String v = VIEWS.contains(view) ? view : "all";
        String keyword = q == null ? "" : q.trim();
        String cat = category != null && CATEGORIES.contains(category) ? category : "";
        String me = browserId.get(request, response); // このブラウザの ID（お気に入り用）

        // 表示する商品と見出しを決める
        List<Product> products = List.of();
        String heading;
        if (!keyword.isEmpty()) {
            products = searchService.search(keyword);
            heading = "「" + keyword + "」の検索結果";
        } else {
            switch (v) {
                case "recommend" -> {
                    // おすすめ：計画の持ち物としてよく選ばれている人気の商品
                    products = productRepository.findPopular(POPULAR_LIMIT);
                    heading = "おすすめ（よく選ばれている商品）";
                }
                case "theme" -> {
                    // 特集・テーマ：選んだ特集（なければ最初の特集）の商品を、決めた順に並べる
                    List<Theme> themes = themeService.findAll();
                    Theme t = themes.stream().filter(x -> x.id().equals(theme)).findFirst()
                            .orElse(themes.isEmpty() ? null : themes.get(0));
                    if (t != null) {
                        Map<Long, Product> byId = productRepository.findAll().stream()
                                .collect(Collectors.toMap(Product::id, Function.identity()));
                        products = t.productIds().stream().map(byId::get).filter(Objects::nonNull).toList();
                        model.addAttribute("currentTheme", t);
                    }
                    heading = t == null ? "特集・テーマ" : "特集：" + t.title();
                }
                case "favorites" -> {
                    products = favoriteService.findProducts(me);
                    heading = "お気に入り";
                }
                case "shopping" -> heading = "買うものリスト";
                case "history" -> heading = "購入履歴";
                default -> {
                    products = productRepository.findAll();
                    heading = "商品一覧";
                }
            }
        }
        // 種類の絞り込み（すべての表示に使える）
        if (!cat.isEmpty()) {
            products = products.stream().filter(p -> p.category().equals(cat)).toList();
        }
        model.addAttribute("view", keyword.isEmpty() ? v : "search");
        model.addAttribute("q", keyword);
        model.addAttribute("heading", heading);
        model.addAttribute("products", products);
        model.addAttribute("themes", themeService.findAll());
        model.addAttribute("categories", CATEGORIES);
        model.addAttribute("selectedCategory", cat);
        model.addAttribute("favoriteIds", favoriteService.findProductIds(me));
        // でぽんたが言う「○件表示しているよ」の数（買うものリスト・購入履歴では、商品ではなく行の数）
        int shownCount = products.size();
        // 買うものリスト：これからの計画ごとに、DEPOで買う商品（商品を選んだ持ち物）だけを並べる
        // 商品がまだ決まっていない持ち物（商品未定）は出さない
        if ("shopping".equals(v) && keyword.isEmpty()) {
            Map<OutingSummary, List<PackingItem>> lists = new LinkedHashMap<>();
            Map<Long, Integer> totals = new HashMap<>();
            for (OutingSummary s : outingService.findUpcoming()) {
                List<PackingItem> depoItems = itemService.findShoppingList(s.outing().id()).stream()
                        .filter(i -> i.productId() != null).toList();
                lists.put(s, depoItems);
                totals.put(s.outing().id(), depoItems.stream().mapToInt(PackingItem::subtotal).sum());
            }
            model.addAttribute("shoppingLists", lists);
            shownCount = lists.values().stream().mapToInt(List::size).sum();
            model.addAttribute("shoppingTotals", totals);
        }
        // 購入履歴：買った（商品を選んでチェックを付けた）もの。ログインがないので、すべての計画のもの
        if ("history".equals(v) && keyword.isEmpty()) {
            List<Object[]> history = itemRepository.findPurchased();
            model.addAttribute("history", history);
            shownCount = history.size();
        }
        model.addAttribute("doneMessage", "fav".equals(done) ? "お気に入りを更新しました" : null);
        // でぽんたのセリフ（DEPO商品一覧の案内）と、商品ごとのひとこと（カードにマウスを乗せたとき）
        model.addAttribute("tanukiLines", TanukiLines.forProducts(heading, shownCount));
        Map<Long, String> productSays = new HashMap<>();
        for (Product p : products) {
            productSays.put(p.id(), "「" + p.name() + "」は" + String.format("%,d", p.price()) + "円。" + p.description() + "だよ");
        }
        model.addAttribute("productSays", productSays);
        return "products/list";
    }

    // U20 お気に入りに入れる／外す（ハート）。元の表示に戻る
    @PostMapping("/products/{productId}/favorite")
    public String favorite(@PathVariable long productId,
                           @RequestParam(required = false) String view,
                           @RequestParam(required = false) Long theme,
                           @RequestParam(required = false) String q,
                           @RequestParam(required = false) String category,
                           HttpServletRequest request, HttpServletResponse response) {
        favoriteService.toggle(browserId.get(request, response), productId);
        UriComponentsBuilder url = UriComponentsBuilder.fromPath("/products").queryParam("done", "fav");
        if (view != null && VIEWS.contains(view)) {
            url.queryParam("view", view);
        }
        if (theme != null) {
            url.queryParam("theme", theme);
        }
        if (q != null && !q.isBlank()) {
            url.queryParam("q", q.trim());
        }
        if (category != null && CATEGORIES.contains(category)) {
            url.queryParam("category", category);
        }
        return "redirect:" + url.build().encode().toUriString();
    }
}
