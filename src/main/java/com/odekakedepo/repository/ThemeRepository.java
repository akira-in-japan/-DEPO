package com.odekakedepo.repository;

// 特集・テーマ（themes・theme_products テーブル）の SQL
// 【repository】DB とのやりとり（SQL）だけを担当するクラス。画面や計算のことは知らない

import com.odekakedepo.entity.Theme;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ThemeRepository {

    // Spring が用意した JdbcTemplate。SQL を実行するための道具。コンストラクタで受け取る（DI）
    private final JdbcTemplate jdbc;

    // コンストラクタ：Spring が JdbcTemplate を渡してくれるので、フィールドに入れておく
    public ThemeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // すべての特集を id の順に。商品 ID は特集ごとに並び順で取り出してつける
    public List<Theme> findAll() {
        return jdbc.query("SELECT * FROM themes ORDER BY id",
                (rs, i) -> new Theme(rs.getLong("id"), rs.getString("title"), rs.getString("description"),
                        findProductIds(rs.getLong("id"))));
    }

    // id で特集を1件探す
    public Optional<Theme> findById(long id) {
        return jdbc.query("SELECT * FROM themes WHERE id = ?",
                (rs, i) -> new Theme(rs.getLong("id"), rs.getString("title"), rs.getString("description"),
                        findProductIds(rs.getLong("id"))), id).stream().findFirst();
    }

    // 特集に入っている商品 ID（並び順）
    public List<Long> findProductIds(long themeId) {
        return jdbc.queryForList("SELECT product_id FROM theme_products WHERE theme_id = ? ORDER BY sort_order, id",
                Long.class, themeId);
    }

    // 特集を追加し、自動で付いた id を返す
    public long insert(String title, String description) {
        var keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO themes (title, description) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, title);
            ps.setString(2, description);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    // 特集のタイトルと説明を変える
    public int update(long id, String title, String description) {
        return jdbc.update("UPDATE themes SET title = ?, description = ? WHERE id = ?", title, description, id);
    }

    // 特集の商品を入れかえる：いったん全部消してから、選んだ順に入れ直す
    public void replaceProducts(long themeId, List<Long> productIds) {
        jdbc.update("DELETE FROM theme_products WHERE theme_id = ?", themeId);
        for (int i = 0; i < productIds.size(); i++) {
            jdbc.update("INSERT INTO theme_products (theme_id, product_id, sort_order) VALUES (?, ?, ?)",
                    themeId, productIds.get(i), i + 1);
        }
    }

    // 特集を消す（入っていた商品の行は ON DELETE CASCADE で消える）
    public int delete(long id) {
        return jdbc.update("DELETE FROM themes WHERE id = ?", id);
    }
}
