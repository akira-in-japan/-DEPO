package com.odekakedepo.repository;

// やりたいこと（outing_wishes テーブル）の SQL
// 【repository】DB とのやりとり（SQL）だけを担当するクラス。画面や計算のことは知らない
// @Repository を付けると、Spring がこのクラスのインスタンスを1つ作って管理し、必要なクラスに渡してくれる（DI：依存性の注入）

import com.odekakedepo.entity.Wish;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class WishRepository {

    // Spring が用意した JdbcTemplate。SQL を実行するための道具。コンストラクタで受け取る（DI）
    private final JdbcTemplate jdbc;

    // RowMapper：SQL の結果1行（rs＝ResultSet）を、Java のレコードに変換するルール
    // rs.getLong("列名") のように、列の値を型を指定して取り出す。i は何行目か（ここでは使っていない）
    private static final RowMapper<Wish> MAPPER = (rs, i) ->
            new Wish(rs.getLong("id"), rs.getLong("outing_id"), rs.getString("content"));

    // コンストラクタ：Spring が JdbcTemplate を渡してくれるので、フィールドに入れておく
    public WishRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // 計画のやりたいことを、追加した順に取り出す
    public List<Wish> findByOutingId(long outingId) {
        return jdbc.query("SELECT * FROM outing_wishes WHERE outing_id = ? ORDER BY id", MAPPER, outingId);
    }

    // やりたいことを id で1件探す
    public Optional<Wish> findById(long id) {
        return jdbc.query("SELECT * FROM outing_wishes WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    // やりたいことを追加し、自動で付いた id を返す
    public long insert(long outingId, String content) {
        // GeneratedKeyHolder：INSERT したときに DB が自動で付けた番号（AUTO_INCREMENT の id）を受け取る入れ物
        var keyHolder = new GeneratedKeyHolder();
        // con（DB への接続）から PreparedStatement（ps）を作る。SQL の ? の場所に、あとから値を入れる
        // 値を直接 SQL の文字につなげず ? で渡すことで、SQL インジェクション（悪意のある入力で SQL を書きかえる攻撃）を防ぐ
        // RETURN_GENERATED_KEYS で、自動で付いた id を返してもらう
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO outing_wishes (outing_id, content) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, outingId);
            ps.setString(2, content);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    // やりたいことを削除する。戻り値は消えた行の数
    public int delete(long id) {
        return jdbc.update("DELETE FROM outing_wishes WHERE id = ?", id);
    }
}
