CREATE TABLE IF NOT EXISTS products (
  id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(100) NOT NULL,
  category    VARCHAR(30)  NOT NULL,
  price       INT          NOT NULL,
  description VARCHAR(255) NOT NULL,
  stock       INT
);

CREATE TABLE IF NOT EXISTS outings (
  id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  title        VARCHAR(100) NOT NULL,
  outing_date  DATE         NOT NULL,
  destination  VARCHAR(100) NOT NULL,
  participants INT          NOT NULL,
  category     VARCHAR(30)  NOT NULL,
  memo         VARCHAR(500),
  share_token  VARCHAR(36)  NOT NULL,
  CONSTRAINT uq_outings_share_token UNIQUE (share_token)
);

CREATE TABLE IF NOT EXISTS outing_wishes (
  id        BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  outing_id BIGINT       NOT NULL,
  content   VARCHAR(100) NOT NULL,
  CONSTRAINT fk_wishes_outing FOREIGN KEY (outing_id) REFERENCES outings(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS packing_items (
  id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  outing_id   BIGINT       NOT NULL,
  name        VARCHAR(100) NOT NULL,
  is_prepared BOOLEAN      NOT NULL DEFAULT FALSE,
  quantity    INT          NOT NULL DEFAULT 1,
  prep_method VARCHAR(10),
  product_id  BIGINT,
  CONSTRAINT fk_items_outing  FOREIGN KEY (outing_id)  REFERENCES outings(id) ON DELETE CASCADE,
  CONSTRAINT fk_items_product FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE TABLE IF NOT EXISTS schedules (
  id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  outing_id  BIGINT       NOT NULL,
  start_time TIME,
  content    VARCHAR(100) NOT NULL,
  place      VARCHAR(100),
  CONSTRAINT fk_schedules_outing FOREIGN KEY (outing_id) REFERENCES outings(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS item_templates (
  id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  category   VARCHAR(30)  NOT NULL,
  group_name VARCHAR(50)  NOT NULL,
  name       VARCHAR(100) NOT NULL
);

-- お気に入り（ブラウザごと）。ログインの代わりに、ブラウザの Cookie の ID（browser_id）で分ける
CREATE TABLE IF NOT EXISTS favorites (
  id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
  browser_id VARCHAR(32) NOT NULL,
  product_id BIGINT      NOT NULL,
  created_at DATETIME    NOT NULL,
  CONSTRAINT uq_favorites_browser_product UNIQUE (browser_id, product_id),
  CONSTRAINT fk_favorites_product FOREIGN KEY (product_id) REFERENCES products(id)
);

-- 思い出の写真・動画（1週間で自動的に消える）。ファイル本体はサーバーのフォルダに保存し、ここには情報だけ持つ
CREATE TABLE IF NOT EXISTS outing_photos (
  id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  outing_id     BIGINT       NOT NULL,
  token         VARCHAR(32)  NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  content_type  VARCHAR(100) NOT NULL,
  file_size     BIGINT       NOT NULL,
  uploaded_at   DATETIME     NOT NULL,
  expires_at    DATETIME     NOT NULL,
  CONSTRAINT uq_photos_token UNIQUE (token),
  CONSTRAINT fk_photos_outing FOREIGN KEY (outing_id) REFERENCES outings(id) ON DELETE CASCADE
);

-- 言いかえの辞書（集計で、同じものを指す名前を代表の名前にまとめる）。保守担当とお店のスタッフが登録する
CREATE TABLE IF NOT EXISTS item_aliases (
  id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  alias_key      VARCHAR(100) NOT NULL,
  alias_name     VARCHAR(100) NOT NULL,
  canonical_name VARCHAR(100) NOT NULL,
  created_at     DATETIME     NOT NULL,
  CONSTRAINT uq_aliases_key UNIQUE (alias_key)
);

-- 特集・テーマ（管理者ページで追加・編集する）
CREATE TABLE IF NOT EXISTS themes (
  id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  title       VARCHAR(50)  NOT NULL,
  description VARCHAR(200) NOT NULL
);

-- 特集に入れる商品（並び順つき）
CREATE TABLE IF NOT EXISTS theme_products (
  id         BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  theme_id   BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  sort_order INT    NOT NULL,
  CONSTRAINT uq_theme_products UNIQUE (theme_id, product_id),
  CONSTRAINT fk_tp_theme   FOREIGN KEY (theme_id)   REFERENCES themes(id) ON DELETE CASCADE,
  CONSTRAINT fk_tp_product FOREIGN KEY (product_id) REFERENCES products(id)
);
