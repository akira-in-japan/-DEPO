package com.odekakedepo.repository;

// 持ち物（packing_items テーブル）の SQL
// 【repository】DB とのやりとり（SQL）だけを担当するクラス。画面や計算のことは知らない
// @Repository を付けると、Spring がこのクラスのインスタンスを1つ作って管理し、必要なクラスに渡してくれる（DI：依存性の注入）

import com.odekakedepo.entity.PackingItem;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class PackingItemRepository {

    // Spring が用意した JdbcTemplate。SQL を実行するための道具。コンストラクタで受け取る（DI）
    private final JdbcTemplate jdbc;

    // 持ち物を取り出す SQL の共通部分。LEFT JOIN で商品の名前と値段もいっしょに取る
    // LEFT JOIN は「商品がひも付いていない持ち物も残す」結合（商品の列は null になる）
    private static final String SELECT =
            "SELECT pi.id, pi.outing_id, pi.name, pi.is_prepared, pi.quantity, pi.prep_method, pi.product_id, "
                    + "p.name AS product_name, p.price AS product_price "
                    + "FROM packing_items pi LEFT JOIN products p ON p.id = pi.product_id ";

    // RowMapper：SQL の結果1行（rs＝ResultSet）を、Java のレコードに変換するルール
    // rs.getLong("列名") のように、列の値を型を指定して取り出す。i は何行目か（ここでは使っていない）
    // product_id や price は null のことがあるので、getObject(列名, 型) で null のまま取り出す
    private static final RowMapper<PackingItem> MAPPER = (rs, i) -> new PackingItem(
            rs.getLong("id"), rs.getLong("outing_id"), rs.getString("name"),
            rs.getBoolean("is_prepared"), rs.getInt("quantity"), rs.getString("prep_method"),
            rs.getObject("product_id", Long.class),
            rs.getString("product_name"), rs.getObject("product_price", Integer.class));

    // コンストラクタ：Spring が JdbcTemplate を渡してくれるので、フィールドに入れておく
    public PackingItemRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // 計画の持ち物をすべて、追加した順に取り出す
    public List<PackingItem> findByOutingId(long outingId) {
        return jdbc.query(SELECT + "WHERE pi.outing_id = ? ORDER BY pi.id", MAPPER, outingId);
    }

    // 持ち物を id で1件探す。Optional は「見つからないかもしれない」値の箱
    public Optional<PackingItem> findById(long id) {
        return jdbc.query(SELECT + "WHERE pi.id = ?", MAPPER, id).stream().findFirst();
    }

    // その計画で、同じ商品がひも付いた持ち物を探す（「これも買う」で同じ商品を押したとき個数を増やすため）
    public Optional<PackingItem> findByOutingAndProduct(long outingId, long productId) {
        return jdbc.query(SELECT + "WHERE pi.outing_id = ? AND pi.product_id = ? ORDER BY pi.id", MAPPER,
                outingId, productId).stream().findFirst();
    }

    // 持ち物を追加する（商品なし）。下の insert を、商品 ID を null にして呼んでいる
    public long insert(long outingId, String name, int quantity) {
        return insert(outingId, name, quantity, null);
    }

    // 持ち物を追加する（商品つきも可）。自動で付いた id を返す
    // 同じ名前で引数がちがうメソッドを作ることを「オーバーロード」という
    public long insert(long outingId, String name, int quantity, Long productId) {
        // GeneratedKeyHolder：INSERT したときに DB が自動で付けた番号（AUTO_INCREMENT の id）を受け取る入れ物
        var keyHolder = new GeneratedKeyHolder();
        // con（DB への接続）から PreparedStatement（ps）を作る。SQL の ? の場所に、あとから値を入れる
        // 値を直接 SQL の文字につなげず ? で渡すことで、SQL インジェクション（悪意のある入力で SQL を書きかえる攻撃）を防ぐ
        // RETURN_GENERATED_KEYS で、自動で付いた id を返してもらう
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO packing_items (outing_id, name, quantity, product_id, prep_method) VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, outingId);
            ps.setString(2, name);
            ps.setInt(3, quantity);
            ps.setObject(4, productId);
            ps.setString(5, productId == null ? null : "store"); // 商品つきで入れたものは「お店で買う」
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    // 持ち物を削除する。戻り値は消えた行の数（1なら成功、0なら見つからなかった）
    public int delete(long id) {
        return jdbc.update("DELETE FROM packing_items WHERE id = ?", id);
    }

    // 準備チェックを付ける・外す
    public int updatePrepared(long id, boolean prepared) {
        return jdbc.update("UPDATE packing_items SET is_prepared = ? WHERE id = ?", prepared, id);
    }

    // 個数を変える
    public int updateQuantity(long id, int quantity) {
        // 個数が変わったら、もう一度準備が必要なので「済」（is_prepared）も外す
        return jdbc.update("UPDATE packing_items SET quantity = ?, is_prepared = FALSE WHERE id = ?", quantity, id);
    }

    // 持ち物にお店の商品をひも付ける（用意のしかたも「お店で買う」になる）
    public int linkProduct(long id, long productId) {
        return jdbc.update("UPDATE packing_items SET product_id = ?, prep_method = 'store' WHERE id = ?", productId, id);
    }

    // 用意のしかたを変える。「別で準備する」にしたときは、選んでいた商品を外す
    public int updatePrepMethod(long id, String method) {
        if ("other".equals(method)) {
            return jdbc.update("UPDATE packing_items SET prep_method = 'other', product_id = NULL WHERE id = ?", id);
        }
        return jdbc.update("UPDATE packing_items SET prep_method = ? WHERE id = ?", method, id);
    }

    // 商品のひも付けを外す（持ち物は残る）
    public int unlinkProduct(long id) {
        return jdbc.update("UPDATE packing_items SET product_id = NULL WHERE id = ?", id);
    }

    // 計画ごとに「持ち物の数」と「準備済みの数」を数える（計画一覧の進み具合のバー用）
    // GROUP BY で計画ごとにまとめ、SUM(CASE …) で準備済みだけを数えている
    // 戻り値は Map（キーと値の組）。キー＝計画 ID、値＝[全部の数, 準備済みの数]
    public Map<Long, int[]> countByOuting() {
        Map<Long, int[]> map = new HashMap<>();
        jdbc.query("SELECT outing_id, COUNT(*) AS total, "
                        + "SUM(CASE WHEN is_prepared THEN 1 ELSE 0 END) AS done FROM packing_items GROUP BY outing_id",
                // RowCallbackHandler：結果を1行ずつ受け取って、自分で Map に入れていく書き方
                rs -> {
                    map.put(rs.getLong("outing_id"), new int[] {rs.getInt("total"), rs.getInt("done")});
                });
        return map;
    }

    // F5-2 同じ種類のほかの計画の持ち物名（「ほかの人がよく持っていくもの」のおすすめ用）
    // JOIN で計画の種類を見て、今見ている計画（exceptOutingId）は除く
    public List<Object[]> findNamesOfOtherOutings(String category, long exceptOutingId) {
        return jdbc.query("SELECT pi.outing_id, pi.name FROM packing_items pi JOIN outings o ON o.id = pi.outing_id "
                        + "WHERE o.category = ? AND o.id <> ?",
                (rs, i) -> new Object[] {rs.getLong("outing_id"), rs.getString("name")}, category, exceptOutingId);
    }

    // すべての持ち物の名前（集計 S06 用）
    // 購入履歴：商品を選んでチェックを付けた（買った）持ち物を、計画の日付の新しい順に取り出す
    // [持ち物, 計画のタイトル, 計画の日付] の組にして返す
    public List<Object[]> findPurchased() {
        return jdbc.query(SELECT.replace("FROM packing_items pi", ", o.title AS outing_title, o.outing_date FROM packing_items pi")
                        + "JOIN outings o ON o.id = pi.outing_id WHERE pi.is_prepared = TRUE AND pi.product_id IS NOT NULL "
                        + "ORDER BY o.outing_date DESC, pi.id",
                (rs, i) -> new Object[] {MAPPER.mapRow(rs, i), rs.getString("outing_title"),
                        rs.getDate("outing_date").toLocalDate()});
    }

    // すべての持ち物の名前（集計画面用）。queryForList で1列だけをリストにして取る
    public List<String> findAllNames() {
        return jdbc.queryForList("SELECT name FROM packing_items", String.class);
    }
}
