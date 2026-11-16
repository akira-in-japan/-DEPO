package com.odekakedepo.service;

// 持ち物・買い物リスト・おすすめの処理
// 【service】画面の処理の中身（ルール・計算）を担当するクラス。DB は repository を通して使う
// @Service を付けると、Spring がインスタンスを1つ作って管理し、Controller などに渡してくれる（DI）

import com.odekakedepo.entity.Outing;
import com.odekakedepo.entity.PackingItem;
import com.odekakedepo.entity.Product;
import com.odekakedepo.entity.Recommendation;
import com.odekakedepo.repository.PackingItemRepository;
import com.odekakedepo.repository.ProductRepository;
import com.odekakedepo.util.NameNormalizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PackingItemService {

    // F5-2：ほかの計画で何件以上選ばれていたらおすすめに出すか（未決事項。ここを変えれば調整できる）
    // 「ほかの人がよく持っていくもの」を何件以上でおすすめするか。static final は「変わらない決まった値（定数）」
    public static final int MIN_OTHERS_COUNT = 2;
    // おすすめを最大何件出すか
    public static final int MAX_RECOMMEND = 3;

    // 使う repository
    private final PackingItemRepository itemRepository;
    private final ProductRepository productRepository;
    // 言いかえの辞書（おすすめで、似た名前を同じものとして数えるため）
    private final AliasService aliasService;

    // コンストラクタ：Spring が repository を渡してくれる
    public PackingItemService(PackingItemRepository itemRepository, ProductRepository productRepository,
                              AliasService aliasService) {
        this.itemRepository = itemRepository;
        this.productRepository = productRepository;
        this.aliasService = aliasService;
    }

    // 買い物リストに入れたときの結果
    // enum（列挙型）：決まった選択肢だけを持つ型。商品を選んだときの結果を、成功・持ち物がない・準備済み・商品がない の4つで表す
    public enum LinkResult { OK, ITEM_NOT_FOUND, ALREADY_PREPARED, PRODUCT_NOT_FOUND }

    // 計画の持ち物をすべて
    public List<PackingItem> findByOutingId(long outingId) {
        return itemRepository.findByOutingId(outingId);
    }

    // 持ち物を id で1件
    public Optional<PackingItem> findById(long id) {
        return itemRepository.findById(id);
    }

    // 個数の下限と上限
    public static final int MIN_QUANTITY = 1;
    // 個数の上限（これより多くは選べない）
    public static final int MAX_QUANTITY = 99;

    // 買い物リスト：持ち物のうち inShoppingList() が true のもの（PackingItem にルールを書いている）
    // PackingItem::inShoppingList は「メソッド参照」。i -> i.inShoppingList() と同じ意味
    public List<PackingItem> findShoppingList(long outingId) {
        return findByOutingId(outingId).stream().filter(PackingItem::inShoppingList).toList();
    }

    // 合計金額：商品が決まったものの「値段 × 個数」を足す（商品未定は 0 円）。mapToInt で数字にしてから sum() で合計
    public int totalPrice(long outingId) {
        return findShoppingList(outingId).stream().mapToInt(PackingItem::subtotal).sum();
    }

    // まだ買っていない（チェックなし）ものだけの合計
    public int remainingPrice(long outingId) {
        return findShoppingList(outingId).stream().filter(i -> !i.prepared()).mapToInt(PackingItem::subtotal).sum();
    }

    // 個数を変える。1〜99 の外なら false を返して保存しない
    public boolean updateQuantity(long id, int quantity) {
        if (quantity < MIN_QUANTITY || quantity > MAX_QUANTITY) {
            return false;
        }
        return itemRepository.updateQuantity(id, quantity) == 1;
    }

    // 「これも買う」：持ち物を選ばずに、商品をそのまま「DEPOで買うもの」に入れる
    // 同じ商品の、まだ買っていない持ち物があれば個数を1つ増やし、なければ商品名で持ち物を作る
    // @Transactional：個数を増やす・持ち物を作る、を1つのまとまりとして実行する
    @Transactional
    public boolean addProductToShopping(long outingId, long productId) {
        Optional<Product> product = productRepository.findById(productId);
        if (product.isEmpty()) {
            return false;
        }
        // .filter(it -> !it.prepared()) で、すでに買った（チェック済みの）ものは対象から外す
        Optional<PackingItem> same = itemRepository.findByOutingAndProduct(outingId, productId)
                .filter(it -> !it.prepared());
        // Math.min で 99 を超えないようにする
        if (same.isPresent()) {
            itemRepository.updateQuantity(same.get().id(), Math.min(MAX_QUANTITY, same.get().quantity() + 1));
        } else {
            itemRepository.insert(outingId, product.get().name(), 1, productId); // 商品つきなので「お店で買う」
        }
        return true;
    }

    // まだ準備できていない持ち物（商品を選ぶ画面の左側に出す）
    public List<PackingItem> findUnprepared(long outingId) {
        return findByOutingId(outingId).stream().filter(i -> !i.prepared()).toList();
    }

    // 次に決める持ち物：用意のしかたが決まっていないか、「お店で買う」で商品をまだ選んでいない最初の1件
    // 「別で準備する」にしたものは飛ばす
    public Optional<PackingItem> findNextUnselected(long outingId) {
        return findUnprepared(outingId).stream().filter(PackingItem::undecided).findFirst();
    }

    // まだ決まっていない持ち物の数（忘れ物を防ぐための表示と、狸のセリフに使う）
    public long countUndecided(long outingId) {
        return findByOutingId(outingId).stream().filter(PackingItem::undecided).count();
    }

    // 用意のしかたを決める（store：お店で買う／other：別で準備する）。準備済みやほかの計画の持ち物は変えない
    public boolean setPrepMethod(long outingId, long itemId, String method) {
        if (!PackingItem.STORE.equals(method) && !PackingItem.OTHER.equals(method)) {
            return false;
        }
        Optional<PackingItem> item = itemRepository.findById(itemId);
        if (item.isEmpty() || item.get().outingId() != outingId || item.get().prepared()) {
            return false;
        }
        return itemRepository.updatePrepMethod(itemId, method) == 1;
    }

    // 持ち物を1件追加し、追加したものを返す（画面に表示するため）
    // orElseThrow は「なければエラーにする」
    public PackingItem add(long outingId, String name, int quantity) {
        long id = itemRepository.insert(outingId, name, quantity);
        return itemRepository.findById(id).orElseThrow();
    }

    // テンプレートからまとめて追加。すでにある持ち物（書き方の違いも含む）は入れない
    // テンプレートからまとめて追加する処理を、1つのまとまりとして実行する
    @Transactional
    // テンプレートからまとめて追加する。すでにある持ち物（書き方の違いも含む）は入れない
    // 戻り値は追加できた件数
    public int addMany(long outingId, List<String> names) {
        // 今ある持ち物の名前を、書き方をそろえて Set に入れておく
        Set<String> have = findByOutingId(outingId).stream()
                .map(i -> NameNormalizer.normalize(i.name())).collect(Collectors.toCollection(HashSet::new));
        int added = 0;
        // have.add(…) は、新しく入ったら true、すでにあれば false を返すので、重なっていたら入れない
        for (String n : names) {
            String name = n == null ? "" : n.trim();
            if (name.isEmpty() || name.length() > 100 || !have.add(NameNormalizer.normalize(name))) {
                continue;
            }
            itemRepository.insert(outingId, name, 1);
            added++;
        }
        return added;
    }

    // 持ち物を削除。1行消えたら true
    public boolean delete(long id) {
        return itemRepository.delete(id) == 1;
    }

    // 準備チェックを付ける・外す
    public boolean updatePrepared(long id, boolean prepared) {
        return itemRepository.updatePrepared(id, prepared) == 1;
    }

    // U12 持ち物にお店の商品をひも付ける。順番にチェックして、だめなら理由を返す
    public LinkResult linkProduct(long outingId, long itemId, long productId) {
        Optional<PackingItem> item = itemRepository.findById(itemId);
        // 持ち物がない、またはほかの計画の持ち物なら失敗
        if (item.isEmpty() || item.get().outingId() != outingId) {
            return LinkResult.ITEM_NOT_FOUND;
        }
        // 準備済み（チェック済み）の持ち物には付けない
        if (item.get().prepared()) {
            return LinkResult.ALREADY_PREPARED;
        }
        if (productRepository.findById(productId).isEmpty()) {
            return LinkResult.PRODUCT_NOT_FOUND;
        }
        itemRepository.linkProduct(itemId, productId);
        return LinkResult.OK;
    }

    // U09 選んだ商品を外す（持ち物は残り、「商品未定」に戻る）
    public boolean unlink(long outingId, long itemId) {
        Optional<PackingItem> item = itemRepository.findById(itemId);
        if (item.isEmpty() || item.get().outingId() != outingId) {
            return false;
        }
        return itemRepository.unlinkProduct(itemId) == 1;
    }

    // F2-3 おすすめ①：計画の種類と同じ種類の商品のうち、まだ選ばれていない・同じ名前の持ち物がないもの
    public List<Product> recommendProducts(Outing outing, List<PackingItem> items) {
        // すでに選ばれている商品 ID。Objects::nonNull で null（商品なし）を除く
        Set<Long> selected = items.stream().map(PackingItem::productId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // 今ある持ち物の名前（書き方をそろえたもの）
        Set<String> haveNames = items.stream().map(i -> NameNormalizer.normalize(i.name())).collect(Collectors.toSet());
        return productRepository.findByCategory(outing.category()).stream()
                .filter(p -> !selected.contains(p.id()))
                .filter(p -> !haveNames.contains(NameNormalizer.normalize(p.name())))
                .limit(MAX_RECOMMEND).toList();
    }

    // F5-2 おすすめ②：同じ種類のほかの計画でよく持っていくもの（自分のリストにないもの）
    public List<Recommendation> recommendFromOthers(Outing outing, List<PackingItem> items) {
        // 言いかえの辞書を使って、似た名前は同じものとして扱う
        Map<String, String> dict = aliasService.dictionary();
        Set<String> haveNames = items.stream().map(i -> AliasService.resolveKey(dict, i.name())).collect(Collectors.toSet());
        // 名前ごとに「何個の計画で使われているか」を数えるための Map（キー：そろえた名前、値：計画 ID の Set）
        Map<String, Set<Long>> outingsByName = new LinkedHashMap<>();
        // 名前ごとに「元の書き方が何回使われたか」。いちばん多い書き方を画面に出すため
        Map<String, Map<String, Integer>> rawNames = new HashMap<>();
        // row[0] が計画 ID、row[1] が持ち物の名前。(String) は型を変える「キャスト」
        for (Object[] row : itemRepository.findNamesOfOtherOutings(outing.category(), outing.id())) {
            String raw = (String) row[1];
            String key = AliasService.resolveKey(dict, raw);
            if (key.isEmpty() || haveNames.contains(key)) {
                continue;
            }
            outingsByName.computeIfAbsent(key, k -> new HashSet<>()).add((Long) row[0]);
            rawNames.computeIfAbsent(key, k -> new HashMap<>()).merge(raw.trim(), 1, Integer::sum);
        }
        // MIN_OTHERS_COUNT 件以上の計画で使われているものを、多い順に MAX_RECOMMEND 件まで
        return outingsByName.entrySet().stream()
                .filter(e -> e.getValue().size() >= MIN_OTHERS_COUNT)
                .sorted((a, b) -> b.getValue().size() - a.getValue().size())
                .limit(MAX_RECOMMEND)
                .map(e -> new Recommendation(canonicalOr(dict, e.getKey(), mostUsed(rawNames.get(e.getKey()))),
                        e.getValue().size()))
                .toList();
    }

    // いちばん多く使われている書き方を返す（集計画面でも使う）
    static String canonicalOr(Map<String, String> dict, String key, String fallback) {
        // 辞書に代表の名前があればそれを、なければ fallback（いちばん多い書き方）を返す
        return dict.values().stream().filter(c -> NameNormalizer.normalize(c).equals(key)).findFirst().orElse(fallback);
    }

    // いちばん多く使われている書き方を返す（集計画面でも使う）
    static String mostUsed(Map<String, Integer> counts) {
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");
    }
}
