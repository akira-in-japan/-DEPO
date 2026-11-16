package com.odekakedepo.service;

// 計画（登録・更新・削除・一覧）の処理
// 【service】画面の処理の中身（ルール・計算）を担当するクラス。DB は repository を通して使う
// @Service を付けると、Spring がインスタンスを1つ作って管理し、Controller などに渡してくれる（DI）

import com.odekakedepo.entity.Outing;
import com.odekakedepo.entity.OutingSummary;
import com.odekakedepo.form.OutingForm;
import com.odekakedepo.repository.OutingRepository;
import com.odekakedepo.repository.PackingItemRepository;
import com.odekakedepo.repository.WishRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutingService {

    // 使う repository やサービス。final は「一度入れたら変えない」という意味
    private final OutingRepository outingRepository;
    private final PackingItemRepository itemRepository;
    private final WishRepository wishRepository;
    private final PhotoService photoService;

    // コンストラクタ：Spring が必要なものを自動で渡してくれる（コンストラクタ・インジェクション）
    public OutingService(OutingRepository outingRepository, PackingItemRepository itemRepository,
                         WishRepository wishRepository, PhotoService photoService) {
        this.outingRepository = outingRepository;
        this.itemRepository = itemRepository;
        this.wishRepository = wishRepository;
        this.photoService = photoService;
    }

    // 計画一覧のカード用の情報を作る（出発までの日数・持ち物の数・準備済みの数）
    public List<OutingSummary> findSummaries() {
        LocalDate today = LocalDate.now();
        // 計画ごとの持ち物の数を、SQL で一度にまとめて数えておく（計画ごとに SQL を呼ぶより速い）
        Map<Long, int[]> counts = itemRepository.countByOuting();
        // 計画1件ずつ、OutingSummary に変換する。getOrDefault は「なければ [0, 0] を使う」
        return outingRepository.findAll().stream().map(o -> {
            int[] c = counts.getOrDefault(o.id(), new int[] {0, 0});
            return new OutingSummary(o, ChronoUnit.DAYS.between(today, o.outingDate()), c[0], c[1]);
        }).toList();
    }

    // これからの計画（日付が今日以降）。findAll が日付順なので、出発の近い順になる
    public List<OutingSummary> findUpcoming() {
        return findSummaries().stream().filter(s -> !s.isMemory()).toList();
    }

    // 思い出（日付が過ぎた計画）を新しい順に。Comparator で日付の比べ方を決め、reversed() で逆順にする
    public List<OutingSummary> findMemories() {
        return findSummaries().stream().filter(OutingSummary::isMemory)
                .sorted(Comparator.comparing((OutingSummary s) -> s.outing().outingDate()).reversed())
                .toList();
    }

    // id で計画を探す（そのまま repository に頼む）
    public Optional<Outing> findById(long id) {
        return outingRepository.findById(id);
    }

    // 共有用の文字列で計画を探す
    public Optional<Outing> findByShareToken(String token) {
        return outingRepository.findByShareToken(token);
    }

    // 計画を登録し、やりたいこととテンプレートの持ち物もまとめて入れる
    // @Transactional：この中の SQL を「全部成功するか、全部なかったことにするか」のどちらかにする
    // 途中でエラーが出ても、計画だけ登録されて持ち物が入らない、という中途半端な状態にならない
    @Transactional
    public long create(OutingForm form) {
        // UUID（ほぼ重ならないランダムな文字列）から「-」を消して、共有用の文字列にする
        String token = UUID.randomUUID().toString().replace("-", ""); // 推測できない共有用の文字列
        long id = outingRepository.insert(form, token);
        // やりたいことを1件ずつ登録する
        form.cleanWishes().forEach(w -> wishRepository.insert(id, w));
        // テンプレートで選んだ持ち物。Set は同じものを2回入れない入れ物（LinkedHashSet は入れた順を保つ）
        Set<String> names = new LinkedHashSet<>();
        form.getTemplateItems().stream().map(String::trim)
                .filter(n -> !n.isEmpty() && n.length() <= 100).forEach(names::add);
        // 個数1で持ち物を登録する
        names.forEach(n -> itemRepository.insert(id, n, 1));
        return id;
    }

    // 計画を更新する
    public void update(long id, OutingForm form) {
        outingRepository.update(id, form);
    }

    // 計画を削除する。先に写真・動画のファイルを消してから、DB の行を消す
    public void delete(long id) {
        photoService.deleteAllFiles(id); // 写真・動画のファイルも消す
        outingRepository.delete(id);
    }
}
