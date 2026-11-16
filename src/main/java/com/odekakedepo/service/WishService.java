package com.odekakedepo.service;

// やりたいことの処理
// 【service】画面の処理の中身（ルール・計算）を担当するクラス。DB は repository を通して使う
// @Service を付けると、Spring がインスタンスを1つ作って管理し、Controller などに渡してくれる（DI）

import com.odekakedepo.entity.Wish;
import com.odekakedepo.repository.WishRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WishService {

    // 使う repository
    private final WishRepository wishRepository;

    public WishService(WishRepository wishRepository) {
        this.wishRepository = wishRepository;
    }

    // 計画のやりたいことをすべて
    public List<Wish> findByOutingId(long outingId) {
        return wishRepository.findByOutingId(outingId);
    }

    // やりたいことを追加し、追加したものを返す（画面に表示するため）
    public Wish add(long outingId, String content) {
        long id = wishRepository.insert(outingId, content);
        return wishRepository.findById(id).orElseThrow();
    }

    // やりたいことを削除。1行消えたら true
    public boolean delete(long id) {
        return wishRepository.delete(id) == 1;
    }
}
