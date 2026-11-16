package com.odekakedepo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.odekakedepo.util.NameNormalizer;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/** アプリ全体が通しで動くかの確認（テスト用に H2 を MySQL モードで使う） */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class AppTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.odekakedepo.service.PackingItemService itemService;
    @Autowired com.odekakedepo.service.PhotoService photoService;
    @Autowired com.odekakedepo.service.StatsService statsService;
    @Autowired com.odekakedepo.config.SchemaUpdater schemaUpdater;

    private long createOuting(String title, LocalDate date, String category, String... templateItems) throws Exception {
        var req = post("/outings").param("title", title).param("outingDate", date.toString())
                .param("destination", "公園").param("participants", "4").param("category", category)
                .param("wishes", "テントを張る", "", "焚き火をする").param("memo", "ゴミは持ち帰り");
        for (String t : templateItems) {
            req = req.param("templateItems", t);
        }
        mvc.perform(req).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("/outings/*"));
        return jdbc.queryForObject("SELECT MAX(id) FROM outings", Long.class);
    }

    @Test
    void dataLoaded() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products", Integer.class)).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM item_templates", Integer.class)).isEqualTo(137);
    }

    @Test
    void pagesRender() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(containsString("もっと")));
        mvc.perform(get("/outings")).andExpect(status().isOk());
        mvc.perform(get("/outings/new")).andExpect(status().isOk()).andExpect(content().string(containsString("data-tanuki")))
                .andExpect(content().string(not(containsString("持ち物テンプレート"))));
        mvc.perform(get("/products").param("category", "ピクニック")).andExpect(status().isOk())
                .andExpect(content().string(containsString("水筒")));
        mvc.perform(get("/stats")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/admin"));
        mvc.perform(get("/admin")).andExpect(status().isOk());
        mvc.perform(get("/outings/999999")).andExpect(status().isNotFound());
        mvc.perform(get("/share/nothing")).andExpect(status().isNotFound());
    }

    @Test
    void createValidation() throws Exception {
        mvc.perform(post("/outings").param("title", "").param("outingDate", LocalDate.now().minusDays(1).toString())
                        .param("destination", "公園").param("participants", "0").param("category", "キャンプ"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("タイトルを入力してください")))
                .andExpect(content().string(containsString("今日以降の日付を選んでください")))
                .andExpect(content().string(containsString("人数は1人以上にしてください")));
    }

    @Test
    void fullFlow() throws Exception {
        long id = createOuting("秋のキャンプ", LocalDate.now().plusDays(3), "キャンプ", "テント", "寝袋", "ランタン");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outing_wishes WHERE outing_id = ?", Integer.class, id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM packing_items WHERE outing_id = ?", Integer.class, id)).isEqualTo(3);

        mvc.perform(get("/outings/" + id)).andExpect(status().isOk())
                .andExpect(content().string(containsString("あと")))
                .andExpect(content().string(containsString("テントを張る")));
        mvc.perform(get("/outings")).andExpect(content().string(containsString("秋のキャンプ")));

        // テンプレートからまとめて追加（すでにあるものは入らない）
        mvc.perform(post("/outings/" + id + "/items/template").param("names", "テント", "タオル", "防寒着"))
                .andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM packing_items WHERE outing_id = ?", Integer.class, id)).isEqualTo(5);

        // API：持ち物・やりたいこと
        mvc.perform(post("/api/outings/" + id + "/items").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"クーラーボックス\"}"))
                .andExpect(status().isCreated());
        long cooler = jdbc.queryForObject("SELECT MAX(id) FROM packing_items", Long.class);
        mvc.perform(post("/api/outings/" + id + "/items").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/outings/" + id + "/wishes").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"星空をながめる\"}"))
                .andExpect(status().isCreated());
        long wish = jdbc.queryForObject("SELECT MAX(id) FROM outing_wishes", Long.class);
        mvc.perform(delete("/api/wishes/" + wish)).andExpect(status().isOk());

        // S04：商品を選ぶ → 合計 → チェック（買った）→ 戻す
        mvc.perform(get("/outings/" + id + "/products").param("itemId", String.valueOf(cooler))).andExpect(status().isOk())
                .andExpect(content().string(containsString("まだ準備できていない持ち物")));
        mvc.perform(post("/outings/" + id + "/items/" + cooler + "/product").param("productId", "8"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/outings/" + id + "/products")).andExpect(content().string(containsString("8,800円")));
        mvc.perform(patch("/api/items/" + cooler + "/prepared").contentType(MediaType.APPLICATION_JSON).content("{\"prepared\":true}"))
                .andExpect(status().isOk());
        mvc.perform(post("/outings/" + id + "/items/" + cooler + "/product").param("productId", "8"))
                .andExpect(redirectedUrlPattern("/outings/*/products?error=prepared"));
        mvc.perform(post("/outings/" + id + "/items/" + cooler + "/unlink")).andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForObject("SELECT product_id FROM packing_items WHERE id = ?", Long.class, cooler)).isNull();

        // 予定表：時間なしは最後
        mvc.perform(post("/outings/" + id + "/schedules").param("content", "散歩")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/outings/" + id + "/schedules").param("startTime", "18:00").param("content", "バーベキュー"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/outings/" + id + "/schedules").param("startTime", "10:00").param("content", "到着").param("place", "キャンプ場"))
                .andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForList("SELECT content FROM schedules WHERE outing_id = ? "
                + "ORDER BY CASE WHEN start_time IS NULL THEN 1 ELSE 0 END, start_time, id", String.class, id))
                .containsExactly("到着", "バーベキュー", "散歩");
        mvc.perform(post("/outings/" + id + "/schedules").param("content", "")).andExpect(status().is3xxRedirection());

        // しおりと共有
        mvc.perform(get("/outings/" + id + "/shiori")).andExpect(status().isOk())
                .andExpect(content().string(containsString("URL・QRで共有")))
                .andExpect(content().string(containsString("ゴミは持ち帰り")));
        String token = jdbc.queryForObject("SELECT share_token FROM outings WHERE id = ?", String.class, id);
        assertThat(token).hasSize(32);
        mvc.perform(get("/share/" + token)).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("URL・QRで共有"))))
                .andExpect(content().string(containsString("秋のキャンプ")));

        // 編集：日付を今日より前に変えることはできない
        mvc.perform(post("/outings/" + id + "/edit").param("title", "x")
                        .param("outingDate", LocalDate.now().minusDays(5).toString()).param("destination", "公園")
                        .param("participants", "4").param("category", "キャンプ"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("今日以降の日付")));

        // 思い出（日付が過ぎた計画）は、計画詳細・編集・商品選びではなく思い出の画面に行く
        jdbc.update("UPDATE outings SET outing_date = ? WHERE id = ?", LocalDate.now().minusDays(10), id);
        mvc.perform(get("/outings/" + id)).andExpect(redirectedUrlPattern("/outings/*/memory"));
        mvc.perform(get("/outings/" + id + "/edit")).andExpect(redirectedUrlPattern("/outings/*/memory"));
        mvc.perform(post("/outings/" + id + "/edit").param("title", "x")
                        .param("outingDate", LocalDate.now().minusDays(10).toString()).param("destination", "公園")
                        .param("participants", "4").param("category", "キャンプ"))
                .andExpect(redirectedUrlPattern("/outings/*/memory"));
        mvc.perform(get("/outings/" + id + "/products")).andExpect(redirectedUrlPattern("/outings/*/memory"));
        mvc.perform(get("/outings/" + id + "/memory")).andExpect(status().isOk())
                .andExpect(content().string(containsString("写真・動画")))
                .andExpect(content().string(not(containsString("/edit"))));
        mvc.perform(get("/outings")).andExpect(content().string(containsString("思い出を開く")));

        // 削除するとまとめて消える
        mvc.perform(post("/outings/" + id + "/delete")).andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM packing_items WHERE outing_id = ?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM schedules WHERE outing_id = ?", Integer.class, id)).isZero();
    }

    @Test
    void recommendAndStats() throws Exception {
        long a = createOuting("ピクニックA", LocalDate.now().plusDays(5), "ピクニック", "レジャーシート", "ウェットティッシュ");
        long b = createOuting("ピクニックB", LocalDate.now().plusDays(6), "ピクニック", "ウェットティッシュ");
        jdbc.update("INSERT INTO packing_items (outing_id, name) VALUES (?, ?)", b, "うぇっとてぃっしゅ");
        long c = createOuting("ピクニックC", LocalDate.now().plusDays(7), "ピクニック");
        // F5-2：ほかの2つの計画で選ばれている「ウェットティッシュ」がおすすめに出る
        mvc.perform(get("/outings/" + c)).andExpect(content().string(containsString("ほかのピクニックの計画で2件")));
        // S06：書き方の違いもまとめて数える／お店にない商品の表示
        mvc.perform(get("/admin")).andExpect(status().isOk())
                .andExpect(content().string(containsString("お店に商品なし")));
        // 絞り込み：お店にないものだけ／あるものだけ
        mvc.perform(get("/admin").param("filter", "nostore")).andExpect(content().string(not(containsString("instore\">あり"))))
                .andExpect(content().string(containsString("ウェットティッシュ")));
        mvc.perform(get("/admin").param("filter", "instore")).andExpect(content().string(containsString("レジャーシート")))
                .andExpect(content().string(not(containsString("<td>ウェットティッシュ</td>"))));
        // 計画をつくる画面：西暦のカレンダー（flatpickr）
        mvc.perform(get("/outings/new")).andExpect(content().string(containsString("flatpickr.min.css")))
                .andExpect(content().string(containsString("class=\"date-input\"")));
        assertThat(a).isPositive();
    }

    @Test
    void quantityToBuyAndFavorites() throws Exception {
        long id = createOuting("家族でピクニック", LocalDate.now().plusDays(2), "ピクニック");
        // テンプレートを選ばなければ、持ち物は空で始まる
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM packing_items WHERE outing_id = ?", Integer.class, id)).isZero();

        // 個数つきで追加
        mvc.perform(post("/api/outings/" + id + "/items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"水筒\",\"quantity\":3}")).andExpect(status().isCreated());
        long bottle = jdbc.queryForObject("SELECT MAX(id) FROM packing_items", Long.class);
        mvc.perform(post("/api/outings/" + id + "/items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"お弁当\"}")).andExpect(status().isCreated());

        // チェックが付いていない持ち物は、自動で買い物リストに入る（商品未定）
        assertThat(itemService.findShoppingList(id)).hasSize(2); // 商品が決まっていなくても買い物リストに入る
        mvc.perform(get("/outings/" + id)).andExpect(content().string(not(containsString("買い物リストへ"))));

        // 商品を選ぶと 値段×個数 で合計
        mvc.perform(post("/outings/" + id + "/items/" + bottle + "/product").param("productId", "20"));
        mvc.perform(get("/outings/" + id + "/products")).andExpect(content().string(containsString("8,340円")));
        mvc.perform(patch("/api/items/" + bottle + "/quantity").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("\"total\":5560")));
        mvc.perform(patch("/api/items/" + bottle + "/quantity").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":0}"))
                .andExpect(status().isBadRequest());

        // これも欲しい：持ち物を選ばずに商品を入れる。同じ商品は個数が増える
        mvc.perform(post("/outings/" + id + "/products/18/add")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/outings/" + id + "/products/18/add")).andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForObject("SELECT quantity FROM packing_items WHERE outing_id = ? AND product_id = 18", Integer.class, id)).isEqualTo(2);

        // お気に入り（ブラウザごと）：入れる → 同じブラウザ（Cookie）の計画詳細に出る → 外す
        var first = mvc.perform(get("/products")).andReturn().getResponse().getCookie("odekake_browser");
        assertThat(first).isNotNull();
        mvc.perform(post("/outings/" + id + "/favorites/16").param("back", "detail").cookie(first)).andExpect(redirectedUrlPattern("/outings/*"));
        mvc.perform(get("/outings/" + id).cookie(first)).andExpect(content().string(containsString("ピクニックバスケット")));
        mvc.perform(get("/outings/" + id + "/products").cookie(first)).andExpect(status().isOk()).andExpect(content().string(containsString("heart-btn on")));
        // 別のブラウザ（Cookie なし）には出ない
        mvc.perform(get("/products").param("view", "favorites")).andExpect(content().string(not(containsString("ピクニックバスケット"))));
        mvc.perform(post("/outings/" + id + "/favorites/16").cookie(first)).andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM favorites WHERE browser_id = ?", Integer.class, first.getValue())).isZero();

        // チェックすると：商品を選んだものは「買った」で残り、選んでいないものは買い物リストから消える
        long lunch = jdbc.queryForObject("SELECT id FROM packing_items WHERE outing_id = ? AND name = 'お弁当'", Long.class, id);
        mvc.perform(patch("/api/items/" + bottle + "/prepared").contentType(MediaType.APPLICATION_JSON).content("{\"prepared\":true}"));
        mvc.perform(patch("/api/items/" + lunch + "/prepared").contentType(MediaType.APPLICATION_JSON).content("{\"prepared\":true}"));
        var shopping = itemService.findShoppingList(id).stream().map(com.odekakedepo.entity.PackingItem::id).toList();
        assertThat(shopping).contains(bottle).doesNotContain(lunch);
        mvc.perform(get("/outings/" + id + "/shiori")).andExpect(content().string(containsString("水筒 ×2")));
    }

    @Test
    void memoryPhotos() throws Exception {
        long id = createOuting("夏の旅行", LocalDate.now().plusDays(1), "旅行");
        // まだ日付が来ていない計画にはアップロードできない
        var jpg = new org.springframework.mock.web.MockMultipartFile("files", "海.jpg", "image/jpeg", new byte[] {1, 2, 3});
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/outings/" + id + "/photos").file(jpg))
                .andExpect(redirectedUrlPattern("/outings/*"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outing_photos", Integer.class)).isZero();

        jdbc.update("UPDATE outings SET outing_date = ? WHERE id = ?", LocalDate.now().minusDays(1), id);
        var bad = new org.springframework.mock.web.MockMultipartFile("files", "メモ.txt", "text/plain", new byte[] {1});
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/outings/" + id + "/photos").file(jpg).file(bad))
                .andExpect(status().is3xxRedirection());
        String token = jdbc.queryForObject("SELECT token FROM outing_photos WHERE outing_id = ?", String.class, id);
        mvc.perform(get("/photos/" + token)).andExpect(status().isOk());
        mvc.perform(get("/outings/" + id + "/memory")).andExpect(content().string(containsString("あと6日で消えます")));
        // 共有された人にも写真が見える
        String share = jdbc.queryForObject("SELECT share_token FROM outings WHERE id = ?", String.class, id);
        mvc.perform(get("/share/" + share)).andExpect(content().string(containsString("/photos/" + token)));

        // 期限が切れると見えなくなり、掃除でファイルも消える
        jdbc.update("UPDATE outing_photos SET expires_at = ? WHERE token = ?",
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusMinutes(1)), token);
        mvc.perform(get("/photos/" + token)).andExpect(status().isNotFound());
        assertThat(photoService.deleteExpired()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outing_photos", Integer.class)).isZero();
    }

    @Test
    void aliases() throws Exception {
        long a = createOuting("旅行A", LocalDate.now().plusDays(3), "旅行");
        long b = createOuting("旅行B", LocalDate.now().plusDays(4), "旅行");
        long c = createOuting("旅行C", LocalDate.now().plusDays(5), "旅行");
        jdbc.update("INSERT INTO packing_items (outing_id, name) VALUES (?, ?)", a, "マイボトル");
        jdbc.update("INSERT INTO packing_items (outing_id, name) VALUES (?, ?)", b, "すいとう");
        jdbc.update("INSERT INTO packing_items (outing_id, name) VALUES (?, ?)", b, "水筒");

        // 入力がない／同じ単語どうし → 登録できない
        mvc.perform(post("/admin/aliases").param("aliasName", "").param("canonical", "水筒"))
                .andExpect(redirectedUrlPattern("/admin?msg=errAliasEmpty*"));
        mvc.perform(post("/admin/aliases").param("aliasName", "すいとう").param("canonical", "スイトウ"))
                .andExpect(redirectedUrlPattern("/admin?msg=errAliasSame*"));

        // 「マイボトル」「すいとう」を「水筒」にまとめる → 集計で水筒 3件・内訳つき、お店の商品「水筒（800ml）」と一致
        mvc.perform(post("/admin/aliases").param("aliasName", "マイボトル").param("canonical", "水筒"))
                .andExpect(redirectedUrlPattern("/admin?msg=aliasAdded*"));
        mvc.perform(post("/admin/aliases").param("aliasName", "すいとう").param("canonical", "水筒"));
        var row = statsService.ranking().stream().filter(r -> r.name().equals("水筒")).findFirst().orElseThrow();
        assertThat(row.count()).isEqualTo(3);
        assertThat(row.breakdown()).contains("マイボトル 1").contains("すいとう 1");
        assertThat(row.productName()).isEqualTo("水筒（800ml）");
        mvc.perform(get("/admin")).andExpect(content().string(containsString("を含む")))
                .andExpect(content().string(containsString("言いかえの辞書")));

        // おすすめ：ほかの計画の「マイボトル」「水筒」が同じものとして2件になる
        mvc.perform(get("/outings/" + c)).andExpect(content().string(containsString("ほかの旅行の計画で2件")));

        // 代表を付けかえる：「水筒」を「ボトル」にまとめると、マイボトル・すいとうもボトルの言いかえになる
        jdbc.update("INSERT INTO packing_items (outing_id, name) VALUES (?, ?)", a, "ボトル");
        mvc.perform(post("/admin/aliases").param("aliasName", "水筒").param("canonical", "ボトル"));
        assertThat(jdbc.queryForList("SELECT DISTINCT canonical_name FROM item_aliases", String.class)).containsExactly("ボトル");
        assertThat(statsService.ranking().stream().filter(r -> r.name().equals("ボトル")).findFirst().orElseThrow().count()).isEqualTo(4);

        // 解除すると、次の集計から別々に数える
        long id = jdbc.queryForObject("SELECT id FROM item_aliases WHERE alias_name = 'マイボトル'", Long.class);
        // 編集：「すいとう」を「マグボトル」の言いかえに変える
        long suito = jdbc.queryForObject("SELECT id FROM item_aliases WHERE alias_name = 'すいとう'", Long.class);
        mvc.perform(post("/admin/aliases/" + suito + "/edit").param("aliasName", "すいとう").param("canonical", "マグボトル"))
                .andExpect(redirectedUrlPattern("/admin?msg=aliasUpdated*"));
        assertThat(jdbc.queryForObject("SELECT canonical_name FROM item_aliases WHERE alias_name = 'すいとう'", String.class)).isEqualTo("マグボトル");
        // 編集の失敗（同じ単語どうし）は元のまま
        long suito2 = jdbc.queryForObject("SELECT id FROM item_aliases WHERE alias_name = 'すいとう'", Long.class);
        mvc.perform(post("/admin/aliases/" + suito2 + "/edit").param("aliasName", "すいとう").param("canonical", "スイトウ"))
                .andExpect(redirectedUrlPattern("/admin?msg=errAliasSame*"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM item_aliases WHERE alias_name = 'すいとう'", Integer.class)).isEqualTo(1);
        mvc.perform(post("/admin/aliases/" + id + "/delete")).andExpect(redirectedUrlPattern("/admin?msg=aliasDeleted*"));
        assertThat(statsService.ranking().stream().anyMatch(r -> r.name().equals("マイボトル"))).isTrue();
        jdbc.update("DELETE FROM item_aliases");
    }

    @Test
    void mypageCatalogAndHelp() throws Exception {
        // マイページ：ニックネームを Cookie に保存 → 計画一覧のでぽんたが「こんにちは、○○さん」（左の「ようこそ」は広告スペースにしたので出ない）
        mvc.perform(get("/outings")).andExpect(content().string(containsString("ゲストさん")))
                .andExpect(content().string(containsString("使い方ガイド")));
        var res = mvc.perform(post("/mypage").param("nickname", "たかはし"))
                .andExpect(redirectedUrlPattern("/mypage?msg=saved")).andReturn().getResponse();
        var cookie = res.getCookie("odekake_nickname");
        assertThat(cookie).isNotNull();
        mvc.perform(get("/outings").cookie(cookie)).andExpect(content().string(containsString("たかはしさん")));
        mvc.perform(get("/mypage").cookie(cookie)).andExpect(content().string(containsString("こんにちは、たかはしさん")));
        mvc.perform(post("/mypage").param("nickname", "  ")).andExpect(redirectedUrlPattern("/mypage?msg=invalid"));
        mvc.perform(post("/mypage").param("nickname", "あ".repeat(21))).andExpect(redirectedUrlPattern("/mypage?msg=invalid"));
        mvc.perform(get("/guide")).andExpect(status().isOk());
        mvc.perform(get("/faq")).andExpect(status().isOk());

        // DEPO商品一覧：検索・種類・おすすめ（人気）・特集・お気に入り・買うものリスト・購入履歴
        long id = createOuting("はじめてのキャンプ計画", LocalDate.now().plusDays(1), "キャンプ");
        mvc.perform(get("/products")).andExpect(content().string(containsString("DEPO商品一覧")))
                .andExpect(content().string(not(containsString("入れる計画"))));
        mvc.perform(get("/products").param("q", "テント")).andExpect(content().string(containsString("ソロテント")))
                .andExpect(content().string(not(containsString("フリスビー"))));
        mvc.perform(get("/products").param("q", "%")).andExpect(content().string(containsString("見つかりませんでした")));
        mvc.perform(get("/products").param("category", "ピクニック")).andExpect(content().string(containsString("フリスビー")))
                .andExpect(content().string(not(containsString("ソロテント"))));
        mvc.perform(post("/outings/" + id + "/products/5/add"));
        mvc.perform(get("/products").param("view", "recommend")).andExpect(content().string(containsString("LEDランタン")));
        mvc.perform(get("/products").param("view", "theme").param("theme", "3"))
                .andExpect(content().string(containsString("特集：家族でピクニック"))).andExpect(content().string(containsString("フリスビー")));
        var browser = mvc.perform(get("/products")).andReturn().getResponse().getCookie("odekake_browser");
        mvc.perform(post("/products/7/favorite").param("view", "favorites").cookie(browser))
                .andExpect(redirectedUrlPattern("/products?*view=favorites*"));
        mvc.perform(get("/products").param("view", "favorites").cookie(browser)).andExpect(content().string(containsString("焚き火台")));
        mvc.perform(get("/products").param("view", "shopping"))
                .andExpect(content().string(containsString("LEDランタン"))).andExpect(content().string(containsString("はじめてのキャンプ計画")));
        long lantern = jdbc.queryForObject("SELECT id FROM packing_items WHERE outing_id = ? AND product_id = 5", Long.class, id);
        mvc.perform(patch("/api/items/" + lantern + "/prepared").contentType(MediaType.APPLICATION_JSON).content("{\"prepared\":true}"));
        mvc.perform(get("/products").param("view", "history")).andExpect(content().string(containsString("LEDランタン ×1")));

        // 管理者ページ：特集の追加・入力エラー・編集・削除
        mvc.perform(post("/admin/themes").param("title", "夏の川あそび").param("description", "水辺で1日").param("productIds", "17", "20"))
                .andExpect(redirectedUrlPattern("/admin?msg=themeAdded*"));
        long themeId = jdbc.queryForObject("SELECT id FROM themes WHERE title = '夏の川あそび'", Long.class);
        mvc.perform(get("/products").param("view", "theme").param("theme", String.valueOf(themeId)))
                .andExpect(content().string(containsString("保冷バッグ")));
        mvc.perform(post("/admin/themes").param("title", "").param("description", "x").param("productIds", "1"))
                .andExpect(redirectedUrlPattern("/admin?err=*"));
        mvc.perform(post("/admin/themes").param("title", "a").param("description", "x"))
                .andExpect(redirectedUrlPattern("/admin?err=*"));
        mvc.perform(post("/admin/themes/" + themeId + "/edit").param("title", "夏の川あそび").param("description", "水辺").param("productIds", "18"))
                .andExpect(redirectedUrlPattern("/admin?msg=themeUpdated*"));
        assertThat(jdbc.queryForList("SELECT product_id FROM theme_products WHERE theme_id = ?", Long.class, themeId)).containsExactly(18L);
        mvc.perform(post("/admin/themes/" + themeId + "/delete")).andExpect(redirectedUrlPattern("/admin?msg=themeDeleted*"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM theme_products WHERE theme_id = ?", Integer.class, themeId)).isZero();
        mvc.perform(get("/admin")).andExpect(content().string(containsString("特集・テーマ")))
                .andExpect(content().string(not(containsString("選んだものをまとめる"))));

        // 計画一覧の狸のセリフ
        mvc.perform(get("/outings")).andExpect(content().string(containsString("tanuki-lines")));
    }

    @Test
    void prepMethodAndListTanuki() throws Exception {
        long id = createOuting("用意のテスト", LocalDate.now().plusDays(1), "ピクニック");
        for (String n : new String[] {"レジャーシート", "お弁当", "タオル"}) {
            mvc.perform(post("/api/outings/" + id + "/items").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + n + "\"}"));
        }
        long sheet = jdbc.queryForObject("SELECT id FROM packing_items WHERE outing_id = ? AND name = 'レジャーシート'", Long.class, id);
        long lunch = jdbc.queryForObject("SELECT id FROM packing_items WHERE outing_id = ? AND name = 'お弁当'", Long.class, id);
        // 最初はどれも決まっていない（あと3つ）。計画詳細では「まだ決まっていない」に出る
        mvc.perform(get("/outings/" + id + "/products")).andExpect(content().string(containsString("あと3つ")));
        mvc.perform(get("/outings/" + id)).andExpect(content().string(containsString("まだ決まっていない")))
                .andExpect(content().string(not(containsString("prep-btn"))));
        // お弁当は「別で準備」→ 次に決める持ち物（タオル）が選ばれた状態で戻る
        mvc.perform(post("/outings/" + id + "/items/" + lunch + "/prep").param("method", "other"))
                .andExpect(redirectedUrlPattern("/outings/*/products?itemId=*"));
        // レジャーシートは「お店で買う」→ その持ち物を選んだ状態で戻る。商品を選ぶまでは「決まっていない」に数える
        mvc.perform(post("/outings/" + id + "/items/" + sheet + "/prep").param("method", "store"))
                .andExpect(redirectedUrlPattern("/outings/*/products?itemId=" + sheet));
        mvc.perform(get("/outings/" + id + "/products")).andExpect(content().string(containsString("あと2つ")))
                .andExpect(content().string(containsString("商品を選んでね")));
        mvc.perform(post("/outings/" + id + "/items/" + sheet + "/product").param("productId", "15"));
        assertThat(jdbc.queryForObject("SELECT prep_method FROM packing_items WHERE id = ?", String.class, sheet)).isEqualTo("store");
        // 「別で準備」にすると選んでいた商品は外れる
        mvc.perform(post("/outings/" + id + "/items/" + sheet + "/prep").param("method", "other"));
        assertThat(jdbc.queryForObject("SELECT product_id FROM packing_items WHERE id = ?", Long.class, sheet)).isNull();
        mvc.perform(post("/outings/" + id + "/items/" + sheet + "/prep").param("method", "x"))
                .andExpect(redirectedUrlPattern("/outings/*/products?*error=notfound*"));
        assertThat(jdbc.queryForObject("SELECT prep_method FROM packing_items WHERE id = ?", String.class, sheet)).isEqualTo("other");
        // 別で準備にしたものは左の一覧から外れ、「別で準備するもの」の折りたたみに入る
        mvc.perform(get("/outings/" + id + "/products")).andExpect(content().string(containsString("別で準備するもの（2）")));

        // 計画一覧：「新しい計画をつくる」はない。狸のあいさつ・明日の出発・決まっていない持ち物・カードのセリフ
        mvc.perform(get("/outings")).andExpect(content().string(not(containsString("新しい計画をつくる"))))
                .andExpect(content().string(containsString("さん！")))
                .andExpect(content().string(containsString("data-say=")))
                .andExpect(content().string(containsString("images/tanuki/normal.webp")));
        var lines = com.odekakedepo.controller.TanukiLines.forList("たかはし", java.time.LocalTime.of(8, 0),
                java.util.List.of(new com.odekakedepo.entity.OutingSummary(
                        new com.odekakedepo.entity.Outing(1L, "秋のキャンプ", LocalDate.now().plusDays(1), "白馬", 4, "キャンプ", null, "x"), 1, 5, 2)),
                java.util.List.of(), java.util.Map.of(1L, 2L), java.util.Map.of());
        assertThat(lines.get(0)).isEqualTo("おはよう、たかはしさん！");
        assertThat(lines).contains("「秋のキャンプ」は明日だね！持ち物は大丈夫？", "「秋のキャンプ」は、どう用意するか決まっていない持ち物が2つあるよ");
        assertThat(lines).hasSizeLessThanOrEqualTo(5);
    }

    @Test
    void schemaUpdaterAddsColumn() throws Exception {
        // 古いデータベース（prep_method がない）でも、起動時に列が足される
        jdbc.execute("ALTER TABLE packing_items DROP COLUMN prep_method");
        jdbc.execute("ALTER TABLE products DROP COLUMN stock");
        schemaUpdater.run(null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM packing_items WHERE prep_method IS NOT NULL OR prep_method IS NULL", Integer.class)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE stock IS NULL", Integer.class)).isEqualTo(20);
        schemaUpdater.run(null); // 2回目は何もしない
    }

    @Test
    void round8Screens() throws Exception {
        // ホーム：使い方は /guide へ、でぽんたの自己紹介、下の説明はなし
        mvc.perform(get("/")).andExpect(content().string(containsString("href=\"/guide\"")))
                .andExpect(content().string(containsString("でぽんただよ")))
                .andExpect(content().string(not(containsString("おでかけDEPOの使い方"))))
                .andExpect(content().string(containsString("odekake-depo-logo")));
        // 計画をつくる・計画一覧・DEPO商品一覧：見出しにでぽんた
        mvc.perform(get("/outings/new")).andExpect(content().string(containsString("depota-intro")));
        mvc.perform(get("/outings")).andExpect(content().string(containsString("guide-col")));
        mvc.perform(get("/products")).andExpect(content().string(containsString("depota-intro")))
                .andExpect(content().string(containsString("data-say=")));
        // ログイン・ユーザー情報（画面だけ）
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(post("/login").param("email", "a@example.com").param("password", "x"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/login?sent=true"));
        mvc.perform(get("/login").param("sent", "true")).andExpect(content().string(containsString("準備中")));
        mvc.perform(get("/mypage/settings")).andExpect(content().string(containsString("メールアドレス")));
        mvc.perform(post("/mypage/settings").param("email", "a@example.com")).andExpect(status().is3xxRedirection());

        // 計画詳細：予定表と用意のしかたは API（再読み込みなし）。買い物リストは確認だけ
        long id = createOuting("詳細テスト", LocalDate.now().plusDays(2), "キャンプ");
        mvc.perform(post("/api/outings/" + id + "/schedules").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTime\":\"\",\"content\":\"散歩\",\"place\":\"\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/outings/" + id + "/schedules").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTime\":\"09:30\",\"content\":\"出発\",\"place\":\"家\"}"))
                .andExpect(status().isCreated());
        String first = jdbc.queryForObject("SELECT content FROM schedules WHERE outing_id = ? "
                + "ORDER BY CASE WHEN start_time IS NULL THEN 1 ELSE 0 END, start_time, id LIMIT 1", String.class, id);
        assertThat(first).isEqualTo("出発");
        mvc.perform(post("/api/outings/" + id + "/schedules").contentType(MediaType.APPLICATION_JSON)
                .content("{\"startTime\":\"25:99\",\"content\":\"x\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/outings/" + id + "/schedules").contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\" \"}")).andExpect(status().isBadRequest());
        long sid = jdbc.queryForObject("SELECT id FROM schedules WHERE outing_id = ? AND content = '散歩'", Long.class, id);
        mvc.perform(delete("/api/outings/" + id + "/schedules/" + sid)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM schedules WHERE outing_id = ?", Integer.class, id)).isEqualTo(1);

        mvc.perform(post("/api/outings/" + id + "/items").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"テント\"}"));
        long tent = jdbc.queryForObject("SELECT id FROM packing_items WHERE outing_id = ? AND name = 'テント'", Long.class, id);
        mvc.perform(patch("/api/items/" + tent + "/prep").contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"other\"}"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("\"prepMethod\":\"other\"")));
        mvc.perform(patch("/api/items/" + tent + "/prep").contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"x\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/outings/" + id)).andExpect(content().string(containsString("DEPO商品一覧を見る")))
                .andExpect(content().string(containsString("guide-col")))
                .andExpect(content().string(containsString("DEPOで買う")))
                .andExpect(content().string(containsString("class=\"stamp\"")))
                .andExpect(content().string(not(containsString("id=\"shopping\""))))
                .andExpect(content().string(not(containsString("pick-product"))));
        // しおり：左のメニューなし
        mvc.perform(get("/outings/" + id + "/shiori")).andExpect(content().string(not(containsString("<aside"))));
        // お店の商品を選ぶ：でぽんた・右上のハート・DEPOで買うものだけ
        mvc.perform(get("/outings/" + id + "/products")).andExpect(content().string(containsString("guide-col")))
                .andExpect(content().string(containsString("fav-corner")))
                .andExpect(content().string(containsString("DEPOで買うもの")))
                .andExpect(content().string(containsString("data-keep-scroll")));
    }

    @Test
    void round11() throws Exception {
        long id = createOuting("札のテスト", LocalDate.now().plusDays(3), "キャンプ");
        mvc.perform(post("/api/outings/" + id + "/items").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"ランタン\"}"));
        long lantern = jdbc.queryForObject("SELECT id FROM packing_items WHERE outing_id = ? AND name = 'ランタン'", Long.class, id);
        mvc.perform(get("/outings/" + id)).andExpect(content().string(containsString("まだ決まっていない")))
                .andExpect(content().string(containsString("DEPOの商品で準備しよう")));
        mvc.perform(post("/outings/" + id + "/items/" + lantern + "/product").param("productId", "5"));
        mvc.perform(get("/outings/" + id)).andExpect(content().string(containsString("DEPOで商品を選択した")));
        // 済 → 準備完了！。個数を変えると済が外れる
        mvc.perform(patch("/api/items/" + lantern + "/prepared").contentType(MediaType.APPLICATION_JSON).content("{\"prepared\":true}"));
        mvc.perform(get("/outings/" + id)).andExpect(content().string(containsString("準備完了！")));
        mvc.perform(patch("/api/items/" + lantern + "/quantity").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"));
        assertThat(jdbc.queryForObject("SELECT is_prepared FROM packing_items WHERE id = ?", Boolean.class, lantern)).isFalse();
        // お店の商品を選ぶ：検索
        mvc.perform(get("/outings/" + id + "/products").param("q", "テント")).andExpect(content().string(containsString("ソロテント")))
                .andExpect(content().string(not(containsString("フリスビー"))));
        mvc.perform(get("/outings/" + id + "/products").param("q", "テント").param("category", "ピクニック"))
                .andExpect(content().string(containsString("見つかりませんでした")));
    }

    @Test
    void round12() throws Exception {
        long id = createOuting("ボタンのテスト", LocalDate.now().plusDays(2), "キャンプ");
        mvc.perform(post("/api/outings/" + id + "/items").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"テント\"}"));
        long tent = jdbc.queryForObject("SELECT id FROM packing_items WHERE outing_id = ? AND name = 'テント'", Long.class, id);
        mvc.perform(get("/outings/" + id + "/products").param("itemId", String.valueOf(tent)))
                .andExpect(content().string(containsString("「テント」用に選ぶ")))
                .andExpect(content().string(containsString("これも買う")))
                .andExpect(content().string(containsString("在庫確認")));
        // 買うものリスト：過去の計画は入らない
        long past = createOuting("過去の計画", LocalDate.now().plusDays(1), "キャンプ");
        mvc.perform(post("/outings/" + past + "/products/5/add"));
        jdbc.update("UPDATE outings SET outing_date = ? WHERE id = ?", LocalDate.now().minusDays(3), past);
        mvc.perform(get("/products").param("view", "shopping")).andExpect(content().string(not(containsString("過去の計画"))));
        // 管理者ページ：商品の追加・入力エラー・変更（在庫数）
        mvc.perform(post("/admin/products").param("name", "折りたたみテーブル").param("category", "キャンプ")
                        .param("price", "5980").param("description", "たためて軽い").param("stock", ""))
                .andExpect(redirectedUrlPattern("/admin?msg=productAdded*"));
        long pid = jdbc.queryForObject("SELECT id FROM products WHERE name = '折りたたみテーブル'", Long.class);
        mvc.perform(get("/products").param("q", "折りたたみテーブル")).andExpect(content().string(containsString("在庫情報はまだないよ")));
        mvc.perform(post("/admin/products").param("name", "x").param("category", "海").param("price", "1").param("description", "x"))
                .andExpect(redirectedUrlPattern("/admin?err=*"));
        mvc.perform(post("/admin/products").param("name", "x").param("category", "キャンプ").param("price", "-1").param("description", "x"))
                .andExpect(redirectedUrlPattern("/admin?err=*"));
        mvc.perform(post("/admin/products/" + pid + "/edit").param("name", "折りたたみテーブル").param("category", "キャンプ")
                        .param("price", "5480").param("description", "たためて軽い").param("stock", "2"))
                .andExpect(redirectedUrlPattern("/admin?msg=productUpdated*"));
        mvc.perform(get("/products").param("q", "折りたたみテーブル")).andExpect(content().string(containsString("残りわずか（2個）")));
        mvc.perform(get("/admin")).andExpect(content().string(containsString("新しい商品を追加する")));
        jdbc.update("DELETE FROM products WHERE id = ?", pid); // ほかのテストの件数に影響しないように片付ける
    }

    @Test
    void round13() throws Exception {
        // 持ち物をぜんぶ「別で準備」にしても、エラー画面にならず「ぜんぶ決まったよ！」の案内が出る（以前は NullPointerException で 500）
        long id = createOuting("ぜんぶ決めるテスト", LocalDate.now().plusDays(3), "キャンプ");
        mvc.perform(post("/api/outings/" + id + "/items").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"テント\"}"));
        mvc.perform(post("/api/outings/" + id + "/items").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"水筒\"}"));
        for (Long itemId : jdbc.queryForList("SELECT id FROM packing_items WHERE outing_id = ?", Long.class, id)) {
            mvc.perform(post("/outings/" + id + "/items/" + itemId + "/prep").param("method", "other"))
                    .andExpect(status().is3xxRedirection());
        }
        mvc.perform(get("/outings/" + id + "/products"))
                .andExpect(content().string(containsString("ぜんぶ決まったよ！")))
                .andExpect(content().string(containsString("計画に戻ってしおりを作る")));
        // 商品の検索：ひらがな・半角カナ・2つのことばでも見つかる
        mvc.perform(get("/outings/" + id + "/products").param("q", "てんと")).andExpect(content().string(containsString("ソロテント")));
        mvc.perform(get("/outings/" + id + "/products").param("q", "ﾃﾝﾄ")).andExpect(content().string(containsString("ファミリーテント")));
        mvc.perform(get("/products").param("q", "テント　大きな")).andExpect(content().string(containsString("ファミリーテント")))
                .andExpect(content().string(not(containsString("ソロテント"))));
        // ホームの見出しの最後に「。」を付けない、計画をつくる画面の左にメニューを出さない
        mvc.perform(get("/")).andExpect(content().string(not(containsString("楽しく</span>。"))));
        mvc.perform(get("/outings/new")).andExpect(content().string(containsString("ad-slot")))
                .andExpect(content().string(not(containsString("side-note"))));
        // タイトル背景の写真用のクラスが付いている（画像を置けば表示される）
        mvc.perform(get("/outings")).andExpect(content().string(containsString("page-hero hero-list")));
        // 買うものリスト：商品が決まっていない持ち物（商品未定）は出さない
        mvc.perform(get("/products").param("view", "shopping")).andExpect(content().string(not(containsString("商品未定"))));
    }

    @Test
    void normalizer() {
        assertThat(NameNormalizer.normalize(" すいとう ")).isEqualTo(NameNormalizer.normalize("ｽｲﾄｳ"));
        assertThat(NameNormalizer.normalize("水筒（800ml）")).isEqualTo("水筒");
    }
}
