package com.odekakedepo.service;

// 特集・テーマの処理（DEPO商品一覧で表示し、管理者ページで追加・編集する）
// 【service】画面の処理の中身（ルール・計算）を担当するクラス。DB は repository を通して使う

import com.odekakedepo.entity.Theme;
import com.odekakedepo.repository.ProductRepository;
import com.odekakedepo.repository.ThemeRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ThemeService {

    // タイトル・説明の文字数の上限
    public static final int MAX_TITLE = 50;
    public static final int MAX_DESCRIPTION = 200;

    // 使う repository
    private final ThemeRepository themeRepository;
    private final ProductRepository productRepository;

    // コンストラクタ：Spring が repository を渡してくれる（DI）
    public ThemeService(ThemeRepository themeRepository, ProductRepository productRepository) {
        this.themeRepository = themeRepository;
        this.productRepository = productRepository;
    }

    public List<Theme> findAll() {
        return themeRepository.findAll();
    }

    public Optional<Theme> findById(long id) {
        return themeRepository.findById(id);
    }

    // 入力チェック：エラーなら画面に出すメッセージを返す（問題なければ null）
    public String validate(String title, String description, List<Long> productIds) {
        if (title == null || title.isBlank() || title.trim().length() > MAX_TITLE) {
            return "特集のタイトルは1〜" + MAX_TITLE + "文字で入力してください";
        }
        if (description == null || description.isBlank() || description.trim().length() > MAX_DESCRIPTION) {
            return "特集の説明は1〜" + MAX_DESCRIPTION + "文字で入力してください";
        }
        if (productIds == null || productIds.isEmpty()) {
            return "特集に入れる商品を1つ以上選んでください";
        }
        return null;
    }

    // 特集を追加する（商品も一緒に入れるので、1つのまとまりとして実行）
    @Transactional
    public long create(String title, String description, List<Long> productIds) {
        long id = themeRepository.insert(title.trim(), description.trim());
        themeRepository.replaceProducts(id, existingProducts(productIds));
        return id;
    }

    // 特集を編集する。見つからなければ false
    @Transactional
    public boolean update(long id, String title, String description, List<Long> productIds) {
        if (themeRepository.update(id, title.trim(), description.trim()) != 1) {
            return false;
        }
        themeRepository.replaceProducts(id, existingProducts(productIds));
        return true;
    }

    public boolean delete(long id) {
        return themeRepository.delete(id) == 1;
    }

    // 実際にある商品の ID だけを、重ならないように選んだ順で残す
    private List<Long> existingProducts(List<Long> productIds) {
        Set<Long> ids = new LinkedHashSet<>(productIds);
        return ids.stream().filter(pid -> productRepository.findById(pid).isPresent()).toList();
    }
}
