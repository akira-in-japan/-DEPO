package com.odekakedepo.service;

// お気に入りの処理（ブラウザごと）。DEPO商品一覧・お店の商品を選ぶ画面・計画詳細で共通に使う
// 【service】画面の処理の中身（ルール・計算）を担当するクラス。DB は repository を通して使う

import com.odekakedepo.entity.Product;
import com.odekakedepo.repository.FavoriteRepository;
import com.odekakedepo.repository.ProductRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class FavoriteService {

    // 使う repository
    private final FavoriteRepository favoriteRepository;
    private final ProductRepository productRepository;

    // コンストラクタ：Spring が repository を渡してくれる（DI）
    public FavoriteService(FavoriteRepository favoriteRepository, ProductRepository productRepository) {
        this.favoriteRepository = favoriteRepository;
        this.productRepository = productRepository;
    }

    // お気に入りの商品
    public List<Product> findProducts(String browserId) {
        return favoriteRepository.findProducts(browserId);
    }

    // お気に入りの商品 ID を Set で（contains で「入っているか」をすぐ調べられる）
    public Set<Long> findProductIds(String browserId) {
        return new HashSet<>(favoriteRepository.findProductIds(browserId));
    }

    // ハートを押したとき：入っていなければ入れ、入っていれば外す。商品がなければ false
    public boolean toggle(String browserId, long productId) {
        if (productRepository.findById(productId).isEmpty()) {
            return false;
        }
        if (favoriteRepository.exists(browserId, productId)) {
            favoriteRepository.delete(browserId, productId);
        } else {
            favoriteRepository.insert(browserId, productId);
        }
        return true;
    }
}
