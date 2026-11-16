package com.odekakedepo.repository;

// 思い出の写真・動画（outing_photos テーブル）の SQL
// 【repository】DB とのやりとり（SQL）だけを担当するクラス。画面や計算のことは知らない
// @Repository を付けると、Spring がこのクラスのインスタンスを1つ作って管理し、必要なクラスに渡してくれる（DI：依存性の注入）

import com.odekakedepo.entity.Photo;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PhotoRepository {

    // Spring が用意した JdbcTemplate。SQL を実行するための道具。コンストラクタで受け取る（DI）
    private final JdbcTemplate jdbc;

    // RowMapper：SQL の結果1行（rs＝ResultSet）を、Java のレコードに変換するルール
    // rs.getLong("列名") のように、列の値を型を指定して取り出す。i は何行目か（ここでは使っていない）
    // DATETIME の列は getTimestamp で取り出し、toLocalDateTime() で Java の日時の型にする
    private static final RowMapper<Photo> MAPPER = (rs, i) -> new Photo(
            rs.getLong("id"), rs.getLong("outing_id"), rs.getString("token"), rs.getString("original_name"),
            rs.getString("content_type"), rs.getLong("file_size"),
            rs.getTimestamp("uploaded_at").toLocalDateTime(), rs.getTimestamp("expires_at").toLocalDateTime());

    // コンストラクタ：Spring が JdbcTemplate を渡してくれるので、フィールドに入れておく
    public PhotoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // 期限が切れていない写真・動画だけを、新しい順に取り出す
    // Timestamp.valueOf で Java の日時を DB の日時の型に変換して ? に渡す
    public List<Photo> findActiveByOutingId(long outingId, LocalDateTime now) {
        return jdbc.query("SELECT * FROM outing_photos WHERE outing_id = ? AND expires_at > ? ORDER BY id DESC",
                MAPPER, outingId, Timestamp.valueOf(now));
    }

    // 計画の写真・動画をすべて（計画を消すときにファイルも消すため）
    public List<Photo> findByOutingId(long outingId) {
        return jdbc.query("SELECT * FROM outing_photos WHERE outing_id = ?", MAPPER, outingId);
    }

    // 表示用：token で探す。期限切れは見つからない扱い
    public Optional<Photo> findActiveByToken(String token, LocalDateTime now) {
        return jdbc.query("SELECT * FROM outing_photos WHERE token = ? AND expires_at > ?", MAPPER,
                token, Timestamp.valueOf(now)).stream().findFirst();
    }

    // id で1件探す（削除のとき）
    public Optional<Photo> findById(long id) {
        return jdbc.query("SELECT * FROM outing_photos WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    // 期限が切れたものをすべて（定期的な掃除のとき）
    public List<Photo> findExpired(LocalDateTime now) {
        return jdbc.query("SELECT * FROM outing_photos WHERE expires_at <= ?", MAPPER, Timestamp.valueOf(now));
    }

    // 計画ごとの、期限内の写真・動画の件数と、いちばん早く消える日時（計画一覧の狸のセリフ用）
    // 戻り値：{計画ID: [件数, いちばん早く消える日時]}
    public Map<Long, Object[]> summaryByOuting(LocalDateTime now) {
        Map<Long, Object[]> map = new HashMap<>();
        jdbc.query("SELECT outing_id, COUNT(*) AS cnt, MIN(expires_at) AS soonest FROM outing_photos "
                + "WHERE expires_at > ? GROUP BY outing_id", rs -> {
                    map.put(rs.getLong("outing_id"),
                            new Object[] {rs.getInt("cnt"), rs.getTimestamp("soonest").toLocalDateTime()});
                }, Timestamp.valueOf(now));
        return map;
    }

    // 期限内の件数（1つの計画30件までの上限チェック用）
    public int countActive(long outingId, LocalDateTime now) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM outing_photos WHERE outing_id = ? AND expires_at > ?",
                Integer.class, outingId, Timestamp.valueOf(now));
        return n == null ? 0 : n;
    }

    // 写真・動画の情報を登録する（ファイル本体はフォルダに保存し、DB には名前や期限だけ入れる）
    public void insert(long outingId, String token, String originalName, String contentType, long size,
                       LocalDateTime uploadedAt, LocalDateTime expiresAt) {
        jdbc.update("INSERT INTO outing_photos (outing_id, token, original_name, content_type, file_size, uploaded_at, expires_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)", outingId, token, originalName, contentType, size,
                Timestamp.valueOf(uploadedAt), Timestamp.valueOf(expiresAt));
    }

    // 写真・動画の情報を削除する
    public void delete(long id) {
        jdbc.update("DELETE FROM outing_photos WHERE id = ?", id);
    }
}
