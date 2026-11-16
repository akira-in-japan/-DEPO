package com.odekakedepo.controller;

// S06 管理者ページ（保守担当・お店のスタッフ向け）
// ・持ち物のランキング（お店にある／ないで絞り込み）
// ・言いかえの辞書（まとめる単語 → 代表の名前）の登録・編集・削除
// ・特集・テーマの登録・編集・削除（DEPO商品一覧に出る）
// ログインがないので、URL を知っていれば誰でも使える（10_未決定事項）
// 【controller】ブラウザからのリクエスト（URL）を受け取り、service に処理を頼んで、表示する画面（テンプレート）を決めるクラス

import com.odekakedepo.repository.ProductRepository;
import com.odekakedepo.service.AliasService;
import com.odekakedepo.service.AliasService.MergeResult;
import com.odekakedepo.service.StatsService;
import com.odekakedepo.service.ThemeService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
public class AdminController {

    // URL の msg=… の値と、画面に出すメッセージの対応表（err で始まるものはエラー）
    private static final Map<String, String> MESSAGES = Map.ofEntries(
            Map.entry("aliasAdded", "言いかえを登録しました。次の集計から代表の名前で数えます"),
            Map.entry("aliasUpdated", "言いかえを変更しました"),
            Map.entry("aliasDeleted", "言いかえを削除しました"),
            Map.entry("errAliasEmpty", "まとめる単語と代表の名前を入力してください"),
            Map.entry("errAliasSame", "まとめる単語と代表の名前が同じです"),
            Map.entry("errAliasNotFound", "その言いかえは見つかりませんでした"),
            Map.entry("themeAdded", "特集を追加しました"),
            Map.entry("themeUpdated", "特集を変更しました"),
            Map.entry("themeDeleted", "特集を削除しました"),
            Map.entry("errThemeNotFound", "その特集は見つかりませんでした"),
            Map.entry("productAdded", "商品を追加しました"),
            Map.entry("productUpdated", "商品を変更しました"),
            Map.entry("errProductNotFound", "その商品は見つかりませんでした"));

    // 使うサービスと repository
    private final StatsService statsService;
    private final AliasService aliasService;
    private final ThemeService themeService;
    private final ProductRepository productRepository;

    // コンストラクタ：Spring が必要なサービスを自動で渡してくれる（DI）
    public AdminController(StatsService statsService, AliasService aliasService, ThemeService themeService,
                           ProductRepository productRepository) {
        this.statsService = statsService;
        this.aliasService = aliasService;
        this.themeService = themeService;
        this.productRepository = productRepository;
    }

    // 前の URL（/stats）で開いても、管理者ページへ移動する
    @GetMapping("/stats")
    public String oldUrl() {
        return "redirect:/admin";
    }

    // U14 管理者ページ。filter：ランキングの絞り込み、msg：直前の操作の結果、err：特集の入力エラー（文言そのもの）
    @GetMapping("/admin")
    public String admin(@RequestParam(defaultValue = "all") String filter,
                        @RequestParam(required = false) String msg,
                        @RequestParam(required = false) String err, Model model) {
        model.addAttribute("ranking", statsService.ranking(filter));
        model.addAttribute("filter", filter);
        model.addAttribute("aliases", aliasService.findAll());
        model.addAttribute("themes", themeService.findAll());
        model.addAttribute("products", productRepository.findAll());
        model.addAttribute("categories", ProductController.CATEGORIES);
        model.addAttribute("message", err != null ? err : (msg == null ? null : MESSAGES.get(msg)));
        model.addAttribute("isError", err != null || (msg != null && msg.startsWith("err")));
        return "admin/index";
    }

    // U26 言いかえを登録する（まとめる単語 → 代表の名前）
    @PostMapping("/admin/aliases")
    public String addAlias(@RequestParam(required = false) String aliasName,
                           @RequestParam(required = false) String canonical) {
        return back(aliasMessage(aliasService.add(aliasName, canonical), "aliasAdded"), "aliases");
    }

    // U33 言いかえを編集する
    @PostMapping("/admin/aliases/{aliasId}/edit")
    public String editAlias(@PathVariable long aliasId, @RequestParam(required = false) String aliasName,
                            @RequestParam(required = false) String canonical) {
        try {
            return back(aliasMessage(aliasService.update(aliasId, aliasName, canonical), "aliasUpdated"), "aliases");
        } catch (IllegalArgumentException e) {
            // update の中で失敗したとき（元に戻したうえで、理由をメッセージにする）
            return back(aliasMessage(MergeResult.valueOf(e.getMessage()), "aliasUpdated"), "aliases");
        }
    }

    // U27 言いかえを削除する
    @PostMapping("/admin/aliases/{aliasId}/delete")
    public String deleteAlias(@PathVariable long aliasId) {
        return back(aliasService.delete(aliasId) ? "aliasDeleted" : "errAliasNotFound", "aliases");
    }

    // U34 特集を追加する。productIds：チェックした商品（選んだ順）
    @PostMapping("/admin/themes")
    public String addTheme(@RequestParam(required = false) String title,
                           @RequestParam(required = false) String description,
                           @RequestParam(name = "productIds", required = false) List<Long> productIds) {
        String error = themeService.validate(title, description, productIds);
        if (error != null) {
            return backWithError(error);
        }
        themeService.create(title, description, productIds);
        return back("themeAdded", "themes");
    }

    // U35 特集を編集する
    @PostMapping("/admin/themes/{themeId}/edit")
    public String editTheme(@PathVariable long themeId, @RequestParam(required = false) String title,
                            @RequestParam(required = false) String description,
                            @RequestParam(name = "productIds", required = false) List<Long> productIds) {
        String error = themeService.validate(title, description, productIds);
        if (error != null) {
            return backWithError(error);
        }
        return back(themeService.update(themeId, title, description, productIds) ? "themeUpdated" : "errThemeNotFound", "themes");
    }

    // U36 特集を削除する
    @PostMapping("/admin/themes/{themeId}/delete")
    public String deleteTheme(@PathVariable long themeId) {
        return back(themeService.delete(themeId) ? "themeDeleted" : "errThemeNotFound", "themes");
    }

    // U42 商品を追加する（名前・種類・値段・説明・在庫数）
    @PostMapping("/admin/products")
    public String addProduct(@RequestParam(required = false) String name, @RequestParam(required = false) String category,
                             @RequestParam(required = false) String price, @RequestParam(required = false) String description,
                             @RequestParam(required = false) String stock) {
        String error = validateProduct(name, category, price, description, stock);
        if (error != null) {
            return backWithError(error, "products");
        }
        productRepository.insert(name.trim(), category, Integer.parseInt(price.trim()), description.trim(), toStock(stock));
        return back("productAdded", "products");
    }

    // U43 商品を変更する
    @PostMapping("/admin/products/{productId}/edit")
    public String editProduct(@PathVariable long productId, @RequestParam(required = false) String name,
                              @RequestParam(required = false) String category, @RequestParam(required = false) String price,
                              @RequestParam(required = false) String description, @RequestParam(required = false) String stock) {
        String error = validateProduct(name, category, price, description, stock);
        if (error != null) {
            return backWithError(error, "products");
        }
        int n = productRepository.update(productId, name.trim(), category, Integer.parseInt(price.trim()), description.trim(), toStock(stock));
        return back(n == 1 ? "productUpdated" : "errProductNotFound", "products");
    }

    // 商品の入力チェック。問題があればメッセージを返す（なければ null）
    static String validateProduct(String name, String category, String price, String description, String stock) {
        if (name == null || name.isBlank() || name.trim().length() > 100) {
            return "商品名は1〜100文字で入力してください";
        }
        if (category == null || !ProductController.CATEGORIES.contains(category)) {
            return "種類はキャンプ・旅行・ピクニックから選んでください";
        }
        if (price == null || !price.trim().matches("\\d{1,7}")) {
            return "値段は0〜9999999の数字で入力してください";
        }
        if (description == null || description.isBlank() || description.trim().length() > 255) {
            return "説明は1〜255文字で入力してください";
        }
        if (stock != null && !stock.isBlank() && !stock.trim().matches("\\d{1,5}")) {
            return "在庫数は0〜99999の数字で入力してください（わからないときは空のまま）";
        }
        return null;
    }

    // 在庫数：空なら null（在庫情報なし）
    private static Integer toStock(String stock) {
        return stock == null || stock.isBlank() ? null : Integer.valueOf(stock.trim());
    }

    // 言いかえの結果を、メッセージの名前にする
    private static String aliasMessage(MergeResult result, String ok) {
        return switch (result) {
            case OK -> ok;
            case SAME -> "errAliasSame";
            case NOT_FOUND -> "errAliasNotFound";
            default -> "errAliasEmpty";
        };
    }

    // 管理者ページの決まった場所（#aliases など）へ戻る
    private String back(String msg, String anchor) {
        return "redirect:" + UriComponentsBuilder.fromPath("/admin").queryParam("msg", msg)
                .fragment(anchor).build().encode().toUriString();
    }

    // 入力エラーの文言をそのまま付けて戻る（anchor：戻る場所）
    private String backWithError(String error) {
        return backWithError(error, "themes");
    }

    private String backWithError(String error, String anchor) {
        return "redirect:" + UriComponentsBuilder.fromPath("/admin").queryParam("err", error)
                .fragment(anchor).build().encode().toUriString();
    }
}
