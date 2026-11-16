package com.odekakedepo.repository;

// 計画（outings テーブル）の SQL
// 【repository】DB とのやりとり（SQL）だけを担当するクラス。画面や計算のことは知らない
// @Repository を付けると、Spring がこのクラスのインスタンスを1つ作って管理し、必要なクラスに渡してくれる（DI：依存性の注入）

import com.odekakedepo.entity.Outing;
import com.odekakedepo.form.OutingForm;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class OutingRepository {

    // Spring が用意した JdbcTemplate。SQL を実行するための道具。コンストラクタで受け取る（DI）
    private final JdbcTemplate jdbc;

    // RowMapper：SQL の結果1行（rs＝ResultSet）を、Java のレコードに変換するルール
    // rs.getLong("列名") のように、列の値を型を指定して取り出す。i は何行目か（ここでは使っていない）
    private static final RowMapper<Outing> MAPPER = (rs, i) -> new Outing(
            rs.getLong("id"), rs.getString("title"), rs.getDate("outing_date").toLocalDate(),
            rs.getString("destination"), rs.getInt("participants"), rs.getString("category"),
            rs.getString("memo"), rs.getString("share_token"));

    // コンストラクタ：Spring が JdbcTemplate を渡してくれるので、フィールドに入れておく
    public OutingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // すべての計画を、日付の早い順に取り出す
    public List<Outing> findAll() {
        return jdbc.query("SELECT * FROM outings ORDER BY outing_date, id", MAPPER);
    }

    // id で計画を1件探す。
    // Optional：「見つからないかもしれない」値を入れる箱。見つからなければ空（Optional.empty()）
    // stream().findFirst() で、結果のリストの最初の1件を Optional にする
    public Optional<Outing> findById(long id) {
        return jdbc.query("SELECT * FROM outings WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    // 共有用の文字列（share_token）で計画を探す（共有されたしおりを開くとき）
    public Optional<Outing> findByShareToken(String token) {
        return jdbc.query("SELECT * FROM outings WHERE share_token = ?", MAPPER, token).stream().findFirst();
    }

    // 計画を登録し、自動で付いた id を返す
    public long insert(OutingForm f, String shareToken) {
        // GeneratedKeyHolder：INSERT したときに DB が自動で付けた番号（AUTO_INCREMENT の id）を受け取る入れ物
        // var は「型を書かずに変数を作る」書き方（右側から型が決まる）
        var keyHolder = new GeneratedKeyHolder();
        // con（DB への接続）から PreparedStatement（ps）を作る。SQL の ? の場所に、あとから値を入れる
        // 値を直接 SQL の文字につなげず ? で渡すことで、SQL インジェクション（悪意のある入力で SQL を書きかえる攻撃）を防ぐ
        // RETURN_GENERATED_KEYS で、自動で付いた id を返してもらう
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO outings (title, outing_date, destination, participants, category, memo, share_token) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, f.getTitle());
            ps.setObject(2, f.getOutingDate());
            ps.setString(3, f.getDestination());
            ps.setInt(4, f.getParticipants());
            ps.setString(5, f.getCategory());
            ps.setString(6, f.getMemo());
            ps.setString(7, shareToken);
            return ps;
        }, keyHolder);
        // DB が付けた id を long にして返す
        return keyHolder.getKey().longValue();
    }

    // 計画を更新する（タイトル・日付・行き先・人数・種類・メモ）
    public void update(long id, OutingForm f) {
        jdbc.update("UPDATE outings SET title = ?, outing_date = ?, destination = ?, participants = ?, "
                        + "category = ?, memo = ? WHERE id = ?",
                f.getTitle(), f.getOutingDate(), f.getDestination(), f.getParticipants(),
                f.getCategory(), f.getMemo(), id);
    }

    // 計画を削除する。やりたいこと・持ち物・予定・写真の行は、テーブルの ON DELETE CASCADE で一緒に消える
    public void delete(long id) {
        jdbc.update("DELETE FROM outings WHERE id = ?", id);
    }
}
