package com.odekakedepo.repository;

// 言いかえの辞書（item_aliases テーブル）の SQL
// 【repository】DB とのやりとり（SQL）だけを担当するクラス。画面や計算のことは知らない

import com.odekakedepo.entity.ItemAlias;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ItemAliasRepository {

    // Spring が用意した JdbcTemplate。SQL を実行するための道具。コンストラクタで受け取る（DI）
    private final JdbcTemplate jdbc;

    // RowMapper：SQL の結果1行（rs＝ResultSet）を、Java のレコードに変換するルール
    private static final RowMapper<ItemAlias> MAPPER = (rs, i) -> new ItemAlias(
            rs.getLong("id"), rs.getString("alias_key"), rs.getString("alias_name"),
            rs.getString("canonical_name"), rs.getTimestamp("created_at").toLocalDateTime());

    // コンストラクタ：Spring が JdbcTemplate を渡してくれるので、フィールドに入れておく
    public ItemAliasRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // 登録した言いかえをすべて、代表の名前ごと・登録順に取り出す
    public List<ItemAlias> findAll() {
        return jdbc.query("SELECT * FROM item_aliases ORDER BY canonical_name, id", MAPPER);
    }

    // id で1件探す（編集のとき）
    public Optional<ItemAlias> findById(long id) {
        return jdbc.query("SELECT * FROM item_aliases WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    // 言いかえを登録する。同じ alias_key がすでにあれば、先に消してから入れる（代表を付けかえる）
    public void upsert(String aliasKey, String aliasName, String canonicalName, LocalDateTime now) {
        jdbc.update("DELETE FROM item_aliases WHERE alias_key = ?", aliasKey);
        jdbc.update("INSERT INTO item_aliases (alias_key, alias_name, canonical_name, created_at) VALUES (?, ?, ?, ?)",
                aliasKey, aliasName, canonicalName, Timestamp.valueOf(now));
    }

    // 代表の名前を付けかえる（「水筒」の言いかえを、まとめて「ボトル」の言いかえにするときなど）
    public void updateCanonical(long id, String canonicalName) {
        jdbc.update("UPDATE item_aliases SET canonical_name = ? WHERE id = ?", canonicalName, id);
    }

    // 言いかえを解除する。戻り値は消えた行の数
    public int delete(long id) {
        return jdbc.update("DELETE FROM item_aliases WHERE id = ?", id);
    }
}
