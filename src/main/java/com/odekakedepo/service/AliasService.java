package com.odekakedepo.service;

// 言いかえの辞書（似た名前を代表の名前にまとめる）の処理
// 使う人：保守担当（私たち）とお店のスタッフ。集計・おすすめの数え方に使う
// 【service】画面の処理の中身（ルール・計算）を担当するクラス。DB は repository を通して使う

import com.odekakedepo.entity.ItemAlias;
import com.odekakedepo.repository.ItemAliasRepository;
import com.odekakedepo.util.NameNormalizer;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AliasService {

    // まとめたときの結果
    public enum MergeResult { OK, TOO_FEW, NO_CANONICAL, SAME, NOT_FOUND }

    // 使う repository
    private final ItemAliasRepository aliasRepository;

    // コンストラクタ：Spring が repository を渡してくれる（DI）
    public AliasService(ItemAliasRepository aliasRepository) {
        this.aliasRepository = aliasRepository;
    }

    // 登録した言いかえの一覧（画面の表示用）
    public List<ItemAlias> findAll() {
        return aliasRepository.findAll();
    }

    // 辞書：{言いかえ前のそろえた名前 → 代表の名前}。集計やおすすめで、名前を数える前に使う
    public Map<String, String> dictionary() {
        Map<String, String> dict = new HashMap<>();
        for (ItemAlias a : aliasRepository.findAll()) {
            dict.put(a.aliasKey(), a.canonicalName());
        }
        return dict;
    }

    // 名前を「数えるときのキー」にする：まず書き方をそろえ、辞書にあれば代表の名前（をそろえたもの）にする
    public static String resolveKey(Map<String, String> dict, String name) {
        String key = NameNormalizer.normalize(name);
        String canonical = dict.get(key);
        return canonical == null ? key : NameNormalizer.normalize(canonical);
    }

    // 選んだ名前を、代表の名前にまとめて辞書に登録する
    // ・2つ以上選んでいないとまとめられない（TOO_FEW）。代表は選んだ名前の中から選ぶ（NO_CANONICAL）
    // ・代表がすでに別の名前の言いかえなら、その先の代表にそろえる（何段もつながらないように）
    // ・まとめられる名前を代表にしていた言いかえも、新しい代表に付けかえる
    @Transactional
    public MergeResult merge(List<String> names, String canonical) {
        Set<String> selected = new LinkedHashSet<>();
        if (names != null) {
            names.stream().map(String::trim).filter(n -> !n.isEmpty() && n.length() <= 100).forEach(selected::add);
        }
        if (selected.size() < 2) {
            return MergeResult.TOO_FEW;
        }
        if (canonical == null || !selected.contains(canonical.trim())) {
            return MergeResult.NO_CANONICAL;
        }
        Map<String, String> dict = dictionary();
        String canonicalName = dict.getOrDefault(NameNormalizer.normalize(canonical), canonical.trim());
        String canonicalKey = NameNormalizer.normalize(canonicalName);
        LocalDateTime now = LocalDateTime.now();
        for (String name : selected) {
            String key = NameNormalizer.normalize(name);
            if (key.equals(canonicalKey)) {
                continue; // 代表そのものは登録しない
            }
            aliasRepository.upsert(key, name, canonicalName, now);
            // この名前を代表にしていた言いかえを、新しい代表に付けかえる
            for (ItemAlias a : aliasRepository.findAll()) {
                if (NameNormalizer.normalize(a.canonicalName()).equals(key)) {
                    aliasRepository.updateCanonical(a.id(), canonicalName);
                }
            }
        }
        return MergeResult.OK;
    }

    // 管理者ページから1件登録する：まとめる単語（aliasName）を、代表の名前（canonical）にまとめる
    // 同じ単語どうしはまとめられない（SAME）。中身は merge と同じルール
    @Transactional
    public MergeResult add(String aliasName, String canonical) {
        if (aliasName == null || canonical == null || aliasName.isBlank() || canonical.isBlank()) {
            return MergeResult.TOO_FEW;
        }
        if (NameNormalizer.normalize(aliasName).equals(NameNormalizer.normalize(canonical))) {
            return MergeResult.SAME;
        }
        return merge(List.of(aliasName.trim(), canonical.trim()), canonical.trim());
    }

    // 登録した言いかえを編集する：いったん消して、新しい内容で登録し直す（失敗したら元に戻る）
    @Transactional
    public MergeResult update(long id, String aliasName, String canonical) {
        if (aliasRepository.findById(id).isEmpty()) {
            return MergeResult.NOT_FOUND;
        }
        aliasRepository.delete(id);
        MergeResult result = add(aliasName, canonical);
        if (result != MergeResult.OK) {
            throw new IllegalArgumentException(result.name()); // ロールバックして、消したものを元に戻す
        }
        return result;
    }

    // 言いかえを解除する（次の集計から元どおり別々に数える。持ち物のデータは変えない）
    public boolean delete(long id) {
        return aliasRepository.delete(id) == 1;
    }
}
