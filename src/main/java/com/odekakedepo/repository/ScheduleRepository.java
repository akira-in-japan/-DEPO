package com.odekakedepo.repository;

// 予定（schedules テーブル）の SQL
// 【repository】DB とのやりとり（SQL）だけを担当するクラス。画面や計算のことは知らない
// @Repository を付けると、Spring がこのクラスのインスタンスを1つ作って管理し、必要なクラスに渡してくれる（DI：依存性の注入）

import com.odekakedepo.entity.Schedule;
import java.sql.Time;
import java.time.LocalTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ScheduleRepository {

    // Spring が用意した JdbcTemplate。SQL を実行するための道具。コンストラクタで受け取る（DI）
    private final JdbcTemplate jdbc;

    // RowMapper：SQL の結果1行（rs＝ResultSet）を、Java のレコードに変換するルール
    // rs.getLong("列名") のように、列の値を型を指定して取り出す。i は何行目か（ここでは使っていない）
    // start_time は空のことがあるので、null なら null、あれば LocalTime（時刻）に変換する
    private static final RowMapper<Schedule> MAPPER = (rs, i) -> {
        Time t = rs.getTime("start_time");
        return new Schedule(rs.getLong("id"), rs.getLong("outing_id"),
                t == null ? null : t.toLocalTime(), rs.getString("content"), rs.getString("place"));
    };

    // コンストラクタ：Spring が JdbcTemplate を渡してくれるので、フィールドに入れておく
    public ScheduleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // 予定を取り出す。並べ方：時間ありは早い順、時間なし（NULL）はその下に追加した順
    // CASE WHEN … で、時間が空なら 1、あれば 0 として先に並べかえている
    public List<Schedule> findByOutingId(long outingId) {
        return jdbc.query("SELECT * FROM schedules WHERE outing_id = ? "
                + "ORDER BY CASE WHEN start_time IS NULL THEN 1 ELSE 0 END, start_time, id", MAPPER, outingId);
    }

    // 予定を追加する。時間が空なら null のまま入れる（Time.valueOf で LocalTime を DB の時刻型に変換）
    public void insert(long outingId, LocalTime startTime, String content, String place) {
        jdbc.update("INSERT INTO schedules (outing_id, start_time, content, place) VALUES (?, ?, ?, ?)",
                outingId, startTime == null ? null : Time.valueOf(startTime), content, place);
    }

    // 予定を削除する。ほかの計画の予定を消さないよう、計画 ID も条件に入れている
    public int delete(long outingId, long id) {
        return jdbc.update("DELETE FROM schedules WHERE id = ? AND outing_id = ?", id, outingId);
    }
}
