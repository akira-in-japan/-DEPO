package com.odekakedepo.config;

// 起動したときに、足りない列を自動で足す（今あるデータは消さない）
// schema.sql は「テーブルがなければ作る」だけなので、すでにあるテーブルに列を足すことはできない。
// そこで、列があるかを調べて、なければ ALTER TABLE で足す。新しく作ったデータベースでは何もしない
// ApplicationRunner：アプリが起動し終わったあとに1回だけ run が呼ばれる

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SchemaUpdater implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaUpdater.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public SchemaUpdater(DataSource dataSource, JdbcTemplate jdbc) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // packing_items に「用意のしかた」（prep_method）の列がなければ足す
        if (!hasColumn("packing_items", "prep_method")) {
            jdbc.execute("ALTER TABLE packing_items ADD COLUMN prep_method VARCHAR(10)");
            // すでに商品を選んでいた持ち物は「お店で買う」にしておく
            jdbc.update("UPDATE packing_items SET prep_method = 'store' WHERE product_id IS NOT NULL");
            log.info("packing_items に prep_method の列を足しました");
        }
        // products に「在庫数」（stock）の列がなければ足す（空＝在庫情報なし）
        if (!hasColumn("products", "stock")) {
            jdbc.execute("ALTER TABLE products ADD COLUMN stock INT");
            log.info("products に stock の列を足しました");
        }
    }

    // テーブルに列があるか（DB の情報 DatabaseMetaData で調べる。MySQL と H2 で大文字・小文字の扱いが違うので両方試す）
    private boolean hasColumn(String table, String column) throws Exception {
        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData meta = con.getMetaData();
            for (String t : new String[] {table, table.toUpperCase()}) {
                for (String c : new String[] {column, column.toUpperCase()}) {
                    try (ResultSet rs = meta.getColumns(con.getCatalog(), null, t, c)) {
                        if (rs.next()) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }
}
