package com.odekakedepo.repository;

// 持ち物テンプレート（item_templates テーブル）の SQL。中身は data.sql で入る
// 【repository】DB とのやりとり（SQL）だけを担当するクラス。画面や計算のことは知らない
// @Repository を付けると、Spring がこのクラスのインスタンスを1つ作って管理し、必要なクラスに渡してくれる（DI：依存性の注入）

import com.odekakedepo.entity.ItemTemplate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ItemTemplateRepository {

    // Spring が用意した JdbcTemplate。SQL を実行するための道具。コンストラクタで受け取る（DI）
    private final JdbcTemplate jdbc;

    // RowMapper：SQL の結果1行（rs＝ResultSet）を、Java のレコードに変換するルール
    // rs.getLong("列名") のように、列の値を型を指定して取り出す。i は何行目か（ここでは使っていない）
    private static final RowMapper<ItemTemplate> MAPPER = (rs, i) ->
            new ItemTemplate(rs.getLong("id"), rs.getString("category"), rs.getString("group_name"),
                    rs.getString("name"));

    // コンストラクタ：Spring が JdbcTemplate を渡してくれるので、フィールドに入れておく
    public ItemTemplateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // テンプレートをすべて、id の順（＝グループの順）に取り出す
    public List<ItemTemplate> findAll() {
        return jdbc.query("SELECT * FROM item_templates ORDER BY id", MAPPER);
    }

    // 種類ごとのテンプレートを取り出す
    public List<ItemTemplate> findByCategory(String category) {
        return jdbc.query("SELECT * FROM item_templates WHERE category = ? ORDER BY id", MAPPER, category);
    }
}
