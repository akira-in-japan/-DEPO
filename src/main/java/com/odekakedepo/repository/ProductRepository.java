package com.odekakedepo.repository;

// 商品（products テーブル）の SQL。商品は data.sql で最初に20件入る
// キーワード検索は ProductSearchService（Java の中で比べる）が担当する
// 【repository】DB とのやりとり（SQL）だけを担当するクラス。画面や計算のことは知らない
// @Repository を付けると、Spring がこのクラスのインスタンスを1つ作って管理し、必要なクラスに渡してくれる（DI：依存性の注入）

import com.odekakedepo.entity.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ProductRepository {

    // Spring が用意した JdbcTemplate。SQL を実行するための道具。コンストラクタで受け取る（DI）
    private final JdbcTemplate jdbc;

    // RowMapper：SQL の結果1行（rs＝ResultSet）を、Java のレコードに変換するルール
    // rs.getLong("列名") のように、列の値を型を指定して取り出す。i は何行目か（ここでは使っていない）
    private static final RowMapper<Product> MAPPER = (rs, i) -> new Product(
            rs.getLong("id"), rs.getString("name"), rs.getString("category"),
            rs.getInt("price"), rs.getString("description"), rs.getObject("stock", Integer.class));

    // コンストラクタ：Spring が JdbcTemplate を渡してくれるので、フィールドに入れておく
    public ProductRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // 商品をすべて取り出す
    public List<Product> findAll() {
        return jdbc.query("SELECT * FROM products ORDER BY id", MAPPER);
    }

    // 商品を id で1件探す
    public Optional<Product> findById(long id) {
        return jdbc.query("SELECT * FROM products WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    // 人気の商品：計画の持ち物として選ばれた回数の多い順（選ばれたことのある商品だけ、最大 limit 件）
    // GROUP BY で商品ごとにまとめ、COUNT で選ばれた回数を数える
    public List<Product> findPopular(int limit) {
        return jdbc.query("SELECT p.id, p.name, p.category, p.price, p.description, p.stock, COUNT(pi.id) AS cnt "
                + "FROM products p JOIN packing_items pi ON pi.product_id = p.id "
                + "GROUP BY p.id, p.name, p.category, p.price, p.description, p.stock ORDER BY cnt DESC, p.id LIMIT ?", MAPPER, limit);
    }

    // 商品を追加する（管理者ページ）。在庫数は空（null）でもよい
    public void insert(String name, String category, int price, String description, Integer stock) {
        jdbc.update("INSERT INTO products (name, category, price, description, stock) VALUES (?, ?, ?, ?, ?)",
                name, category, price, description, stock);
    }

    // 商品を変更する（管理者ページ）。戻り値は変わった行の数
    public int update(long id, String name, String category, int price, String description, Integer stock) {
        return jdbc.update("UPDATE products SET name = ?, category = ?, price = ?, description = ?, stock = ? WHERE id = ?",
                name, category, price, description, stock, id);
    }

    // 種類（キャンプ・旅行・ピクニック）で絞り込んで取り出す
    public List<Product> findByCategory(String category) {
        return jdbc.query("SELECT * FROM products WHERE category = ? ORDER BY id", MAPPER, category);
    }
}
