package com.odekakedepo.service;

// 集計・ランキング（お店向け）の処理
// 【service】画面の処理の中身（ルール・計算）を担当するクラス。DB は repository を通して使う
// @Service を付けると、Spring がインスタンスを1つ作って管理し、Controller などに渡してくれる（DI）

import com.odekakedepo.entity.Product;
import com.odekakedepo.entity.RankingRow;
import com.odekakedepo.repository.PackingItemRepository;
import com.odekakedepo.repository.ProductRepository;
import com.odekakedepo.util.NameNormalizer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

// S06 集計：お客さんがよく持っていくもの（持ち物の名前をそろえて数える）
@Service
public class StatsService {

    // 使う repository
    private final PackingItemRepository itemRepository;
    private final ProductRepository productRepository;
    private final AliasService aliasService;

    public StatsService(PackingItemRepository itemRepository, ProductRepository productRepository,
                        AliasService aliasService) {
        this.itemRepository = itemRepository;
        this.productRepository = productRepository;
        this.aliasService = aliasService;
    }

    // 絞り込みつきのランキング。filter が instore ならお店にあるものだけ、nostore ならないものだけ
    public List<RankingRow> ranking(String filter) {
        return ranking().stream()
                .filter(r -> !"instore".equals(filter) || r.inStore())
                .filter(r -> !"nostore".equals(filter) || !r.inStore())
                .toList();
    }

    // すべての持ち物の名前を、書き方をそろえて数え、多い順に並べる
    public List<RankingRow> ranking() {
        // 言いかえの辞書：{言いかえ前のそろえた名前 → 代表の名前}
        Map<String, String> dict = aliasService.dictionary();
        // counts：数えるときのキーごとの件数。raw：元の書き方ごとの件数（代表の名前と内訳を作るため）
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> raw = new HashMap<>();
        // 名前 → 書き方をそろえる → 辞書にあれば代表の名前にする、の順でキーを作って数える
        // merge(キー, 1, Integer::sum) は「なければ1、あれば1足す」
        for (String name : itemRepository.findAllNames()) {
            String key = AliasService.resolveKey(dict, name);
            if (key.isEmpty()) {
                continue;
            }
            counts.merge(key, 1, Integer::sum);
            raw.computeIfAbsent(key, k -> new HashMap<>()).merge(name.trim(), 1, Integer::sum);
        }
        // キー → 代表の名前（辞書に登録された代表）
        Map<String, String> canonicalByKey = new HashMap<>();
        dict.values().forEach(c -> canonicalByKey.put(NameNormalizer.normalize(c), c));
        // お店の商品名も同じようにキーにして、持ち物の名前と比べられるようにする
        Map<String, String> productByKey = new HashMap<>();
        for (Product p : productRepository.findAll()) {
            productByKey.put(AliasService.resolveKey(dict, p.name()), p.name());
        }
        // 件数の多い順に並べ、RankingRow（名前・件数・お店の商品名・内訳）にする
        return counts.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .map(e -> {
                    String key = e.getKey();
                    Map<String, Integer> names = raw.get(key);
                    // 表示する名前：代表が登録されていれば代表、なければいちばん多い書き方
                    String display = canonicalByKey.getOrDefault(key, PackingItemService.mostUsed(names));
                    // 内訳：代表とは書き方の違う（辞書でまとめた）名前と件数
                    String breakdown = names.entrySet().stream()
                            .filter(n -> !NameNormalizer.normalize(n.getKey()).equals(key))
                            .sorted((x, y) -> y.getValue() - x.getValue())
                            .map(n -> n.getKey() + " " + n.getValue())
                            .collect(Collectors.joining("・"));
                    return new RankingRow(display, e.getValue(), productByKey.get(key), breakdown);
                })
                .toList();
    }
}
