package com.odekakedepo.service;

// 商品のキーワード検索（DEPO商品一覧と、お店の商品を選ぶ画面で使う）
// 【service】画面と DB の間で、決まりごと（ビジネスロジック）を担当するクラス
// 以前は SQL の LIKE で探していたが、DB の文字コードの設定などで結果が0件になることがあったため、
// 商品をすべて取り出してから Java の中で比べる方法に変えた（商品は数十件なので速さも問題ない）

import com.odekakedepo.entity.Product;
import com.odekakedepo.repository.ProductRepository;
import com.odekakedepo.util.NameNormalizer;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ProductSearchService {

    // 使う repository とサービス（コンストラクタで Spring が渡してくれる：DI）
    private final ProductRepository productRepository;
    private final AliasService aliasService;

    public ProductSearchService(ProductRepository productRepository, AliasService aliasService) {
        this.productRepository = productRepository;
        this.aliasService = aliasService;
    }

    // キーワードで商品を探す
    // ・空白（全角・半角）で区切ると、すべてのことばを含む商品だけになる（例：「テント 大きな」）
    // ・ひらがな／カタカナ、全角／半角、大文字／小文字のちがいは同じとみなす（例：「てんと」でもテントが出る）
    // ・管理者ページで登録した「言いかえ」も使う（例：「シュラフ」→ 代表の名前「寝袋」でも探す）
    // ・商品名・説明・種類のどれかに含まれていれば見つかる
    public List<Product> search(String keyword) {
        List<String> words = new ArrayList<>();
        for (String w : (keyword == null ? "" : keyword).trim().split("[\\s　]+")) {
            if (!w.isEmpty()) {
                words.add(w);
            }
        }
        if (words.isEmpty()) {
            return List.of();
        }
        Map<String, String> dict = aliasService.dictionary();
        List<Product> hits = new ArrayList<>();
        for (Product p : productRepository.findAll()) {
            // 比べる文章：商品名＋説明＋種類を、比べやすい形にそろえておく
            String text = fold(p.name()) + " " + fold(p.description()) + " " + fold(p.category());
            boolean all = true;
            for (String w : words) {
                if (!matches(text, w, dict)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                hits.add(p);
            }
        }
        return hits;
    }

    // 1つのことばが、商品の文章に含まれるか（言いかえの代表の名前も試す）
    private static boolean matches(String text, String word, Map<String, String> dict) {
        if (text.contains(fold(word))) {
            return true;
        }
        String canonical = dict.get(NameNormalizer.normalize(word));
        return canonical != null && text.contains(fold(canonical));
    }

    // 比べやすい形にそろえる：NFKC（全角英数→半角、半角カナ→全角カナ）、小文字、ひらがな→カタカナ、空白を消す
    // null（値がない）のときは空の文字にする
    static String fold(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFKC).toLowerCase();
        StringBuilder sb = new StringBuilder(n.length());
        for (char c : n.toCharArray()) {
            sb.append(c >= 'ぁ' && c <= 'ゖ' ? (char) (c + 0x60) : c);
        }
        return sb.toString().replaceAll("\\s+", "");
    }
}
