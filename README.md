# おでかけDEPO おでかけ計画アプリ

アウトドア・旅行用品店「おでかけDEPO」のお客さま向けの、おでかけ計画アプリです。
計画をつくる → 持ち物を決める → 足りないものを DEPO の商品で準備する → しおりにまとめて共有する、までを1つのアプリでできます。
案内役のたぬき「でぽんた」が、各画面で次にすることを教えてくれます。

GEクリエイティブ 新人研修 Phase3（チーム開発）の成果物です。

## 使っている技術

| 分類 | 技術 |
|---|---|
| 言語・フレームワーク | Java 17 / Spring Boot 3.3.5 |
| 画面 | Thymeleaf / HTML / CSS / JavaScript（ライブラリなし。持ち物の追加などは fetch で再読み込みなしに保存） |
| DB | MySQL 8（Spring JDBC の JdbcTemplate で SQL を直接書く） |
| ビルド | Maven |
| テスト | JUnit 5 + MockMvc（H2 データベースで実行） |
| 外部から読み込むもの | Google Fonts（Zen Maru Gothic）、flatpickr（カレンダー）、qrcodejs（共有用の QR コード） |

## 起動のしかた

### 1. データベースを作る（最初の1回だけ）
MySQL で次の SQL を実行します。
```sql
CREATE DATABASE odekake_depo DEFAULT CHARACTER SET utf8mb4;
```
テーブルと最初のデータ（商品20件・持ち物テンプレート・特集）は、アプリの起動時に `schema.sql` と `data.sql` から自動で作られます。
どちらも「なければ作る／なければ入れる」書き方なので、何度起動してもデータは消えず、増えもしません。

### 2. MySQL のユーザー名・パスワードを設定する
パスワードを GitHub に上げないため、`application.properties` には書きません。次のどちらかで設定します。

- **A：設定ファイル（おすすめ）**
  `src/main/resources/application-local.properties.example` をコピーして `application-local.properties` という名前にし、ユーザー名とパスワードを書きます。
  このファイルは `.gitignore` に入っているので、GitHub には上がりません。
- **B：環境変数**
  `DB_USERNAME` と `DB_PASSWORD` を設定してから起動します。

### 3. 起動する
- Eclipse / STS：`OdekakeDepoApplication` を右クリック →「Spring Boot アプリケーションとして実行」
- コマンド：`mvn spring-boot:run`

ブラウザで http://localhost:8080 を開きます。

### テストの実行
```
mvn test
```
テストは H2（メモリ上のデータベース）で動くので、MySQL がなくても実行できます（`src/test/resources/application.properties`）。

## 主な機能

| 画面 | できること |
|---|---|
| ホーム | アプリの紹介と、でぽんたのあいさつ |
| 計画一覧 | これからの計画と思い出をカードで表示。種類（キャンプ・旅行・ピクニック）で絞り込み、出発まであと○日、準備の進み具合 |
| 計画をつくる・編集 | タイトル・日付（カレンダー）・行き先・人数・種類・やりたいこと・メモ。種類ごとの持ち物テンプレートから選べる |
| 計画詳細 | 計画情報、予定表、やりたいこと、持ち物リスト（個数・「済」スタンプ・用意のしかた）、おすすめ（同じ種類の商品／ほかの人がよく持っていくもの）、お気に入り |
| お店の商品を選ぶ | まだ準備できていない持ち物ごとに「DEPOで買う／別で準備」を決め、商品をひも付ける。キーワード検索（ひらがな・カタカナ・言いかえにも対応）、在庫確認、DEPOで買うものの合計金額 |
| DEPO商品一覧 | すべて・おすすめ（人気）・特集・お気に入り・買うものリスト・購入履歴 |
| しおり | 予定・持ち物・やりたいことをまとめたしおり。印刷・PDF、URL・QR コードで共有（見るだけ） |
| 思い出 | 日付が過ぎた計画は、しおりを振り返り、写真・動画をアップロードして共有（7日で自動削除） |
| マイページ | ニックネームの設定（Cookie に保存）、使い方ガイド、よくある質問 |
| 管理者ページ | 持ち物ランキング（お店にある／ない）、言いかえの辞書（「すいとう」→「水筒」など）、特集の編集、商品の追加・変更（在庫数） |

スマホでは、上のメニューがアイコンだけの1行になり、左のメニューは左下の「メニュー」ボタンから開きます。

## フォルダの中身

```
src/main/java/com/odekakedepo/
  controller/   画面の URL を受け取り、表示する画面を決める（ApiController は fetch 用の API）
  service/      決まりごと（準備の進み具合、おすすめ、検索、写真の期限など）
  repository/   SQL（JdbcTemplate）
  entity/       DB の1行を入れる record
  form/         入力フォームの値と入力チェック
  config/       定期実行（写真の自動削除）、古い DB に列を足す SchemaUpdater
  util/         名前の書き方をそろえる NameNormalizer（すいとう／スイトウ／ｽｲﾄｳ を同じものとして数える）
  web/          ブラウザごとの ID（お気に入り用の Cookie）
src/main/resources/
  templates/    Thymeleaf の画面
  static/       CSS・JavaScript・画像（でぽんた、ロゴ、背景）
  schema.sql    テーブル作成
  data.sql      最初のデータ
src/test/       結合テスト（AppTest）
```

## テーブル

| テーブル | 中身 |
|---|---|
| outings | 計画（タイトル・日付・行き先・人数・種類・メモ・共有用の文字列） |
| outing_wishes | やりたいこと |
| packing_items | 持ち物（個数・準備済み・用意のしかた・ひも付けた商品） |
| schedules | 当日の予定 |
| outing_photos | 思い出の写真・動画（期限つき） |
| products | 商品（在庫数つき） |
| item_templates | 種類ごとの持ち物テンプレート |
| item_aliases | 言いかえの辞書 |
| favorites | お気に入り（ブラウザごと） |
| themes / theme_products | 特集とその商品 |

計画を消すと、やりたいこと・持ち物・予定・写真の行も一緒に消えます（ON DELETE CASCADE）。
