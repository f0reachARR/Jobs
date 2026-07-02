# ADR-0013 UI 文言は YAML + MiniMessage で多言語対応する

## ステータス

受け入れ

## 背景

Job プラグインの UI は DialogAPI が主となる（[ADR-0010](./0010-dialog-api-over-inventory-gui.md)）。
UI が扱う文言は Dialog のタイトル、本文、ボタンラベル、`/jobs` 系コマンドのシステムメッセージなど、プレイヤーに直接見せるテキストが中心である。

これらの文言をどこに置き、どう多言語対応するかを決める必要がある。
主な候補は次の三つ。

- **A. データパック JSON**　各 Dialog を Vanilla データパック（`data/jobs/dialog/*.json`）に置き、テキストも同梱する。
- **B. Java コード直書き**　文言を Adventure `Component` として Java コード内に書く。
- **C. 翻訳 YAML + MiniMessage**　`lang/<locale>.yml` にキーごとの MiniMessage 文字列を置き、Dialog 構築時に locale とプレースホルダを解決する。

トレードオフを整理する。

A のデータパック JSON は Vanilla ネイティブで整合するが、locale ごとの切り替えが構造的にできない。
プレイヤー locale に応じて Dialog を差し替える手段が Vanilla 側に存在せず、多言語対応と両立しない。

B の Java 直書きは実装が最短だが、文言変更のたびに再コンパイルが必要で、翻訳者が触れない。
プラグイン運用として現実的でない。

C の翻訳 YAML はプラグインの資源ファイルとして自然で、キー参照によって Java コードと文言を分離できる。
MiniMessage は Paper/Adventure が公式に提供する記法で、装飾タグ・イベントタグ・カスタム TagResolver によるプレースホルダ埋め込みを標準サポートする。

## 決定

UI 文言は `lang/<locale>.yml` に MiniMessage 形式で定義する。

- ファイル名は Minecraft のロケール ID（`ja_jp`、`en_us` 等）に一致させる。
- キーは Dialog 単位で階層化し（`dialog.select.title` など）、Java コードからはキー参照で解決する。
- 動的な情報はプレースホルダ（`<player>`、`<amount>`、`<cooldown>`）として文言に埋め込み、Dialog 構築時に `TagResolver` で解決する。
- プレイヤーの locale は `Player#locale()` で取得し、対応するロケールファイルが無ければ `ja_jp` にフォールバックする。
- 起動時に全ロケールファイルを読み込み、キー集合の差分と欠落キーを警告として出す。

Dialog の構造そのもの（ボタン配置、選択肢の並び、フォーム構成）は Java コード上で `Dialog` API を使って構築し、データパック JSON には置かない。
これは locale 別の差し替えができないという A の制約を回避するための決定であり、DialogAPI の実装形態に対する変更ではない。

ジョブ YAML の `display_name`、`variety_penalty.disclosed_message`、`rewards[].rare.announce` はイベント固有のジョブ定義に強く紐づくため翻訳 YAML に持たせない。
値そのものを MiniMessage として解釈するに留める。
イベント側で多言語化したい場合の扱いは、Job プラグインではなくイベント側プラグインの責務とする。

## 結果

- 文言変更が YAML 編集で完結し、再ビルドが不要になる。
- プラグインの資源ファイルに翻訳を追加するだけで対応言語を増やせる。
- MiniMessage 記法により、装飾・ホバー・クリックイベントも文言側で表現できる。
- データパック JSON の Dialog 定義を持たないぶん、Vanilla の Dialog リロード機構（`/reload`）による差し替えは効かず、プラグイン側で明示的なリロードを提供する必要がある。
- Adventure の MiniMessage API が experimental のまま将来仕様変更した場合、追随コストが発生する。

## 選択しなかった代替案

**A. データパック JSON**　locale 切り替えができず多言語対応と両立しないため不採用。
Vanilla の翻訳キー（`translate` フィールド）でクライアント側 resource pack に寄せる案もあるが、resource pack 配布は運用上の負担が大きく、サーバ運営者が翻訳を追加する体験を悪くする。

**B. Java コード直書き**　翻訳者が触れない、変更で再ビルドが必要になる、という運用上の欠点が大きく不採用。
