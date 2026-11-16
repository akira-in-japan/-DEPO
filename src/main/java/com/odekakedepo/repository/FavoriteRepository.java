package com.odekakedepo.repository;

// お気に入り（favorites テーブル）の SQL。ブラウザごと（browser_id）に分けている
// 【repository】DB とのやりとり（SQL）だけを担当するクラス。画面や計算のことは知らない

import com.odekakedepo.entity.Product;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FavoriteRepository {

    // Spring が用意した JdbcTemplate。SQL を実行するための道具。コンストラクタで受け取る（DI）
    private final JdbcTemplate jdbc;

    // コンストラクタ：Spring が JdbcTemplate を渡してくれるので、フィールドに入れておく
    public FavoriteRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // お気に入りの商品を、入れた順に取り出す。JOIN で商品の情報もいっしょに取る
    public List<Product> findProducts(String browserId) {
        return jdbc.query("SELECT p.* FROM favorites f JOIN products p ON p.id = f.product_id "
                        + "WHERE f.browser_id = ? ORDER BY f.id",
                (rs, i) -> new Product(rs.getLong("id"), rs.getString("name"), rs.getString("category"),
                        rs.getInt("price"), rs.getString("description"), rs.getObject("stock", Integer.class)), browserId);
    }

    // お気に入りに入っている商品の ID だけ（ハートを塗るかどうかの判定用）
    public List<Long> findProductIds(String browserId) {
        return jdbc.queryForList("SELECT product_id FROM favorites WHERE browser_id = ?", Long.class, browserId);
    }

    // その商品がすでにお気に入りにあるか
    public boolean exists(String browserId, long productId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM favorites WHERE browser_id = ? AND product_id = ?",
                Integer.class, browserId, productId);
        return n != null && n > 0;
    }

    // お気に入りに入れる
    public void insert(String browserId, long productId) {
        jdbc.update("INSERT INTO favorites (browser_id, product_id, created_at) VALUES (?, ?, ?)",
                browserId, productId, Timestamp.valueOf(LocalDateTime.now()));
    }

    // お気に入りから外す
    public void delete(String browserId, long productId) {
        jdbc.update("DELETE FROM favorites WHERE browser_id = ? AND product_id = ?", browserId, productId);
    }
}
