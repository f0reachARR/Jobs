# ADR-0014 Dialog 実装に BedrockDialog を使う

## ステータス

受け入れ

## 背景

Job プラグインの UI は DialogAPI 中心と決めた（[ADR-0010](./0010-dialog-api-over-inventory-gui.md)）。
一方、対象サーバは Geyser 経由で Bedrock Edition プレイヤーの参加を想定する。
Paper の `Dialog` は Java Edition クライアント固有の描画機能で、Bedrock クライアントには届かない。

Bedrock 側は Geyser のフォーム機能（Simple / Modal / Custom Form）を使えば同等の UI を提供できるが、次の課題がある。

- API が別体系で、Java 用 Dialog と Bedrock 用フォームを Job プラグイン内で二重管理することになる。
- プレイヤーごとの分岐（Floodgate 判定、`GeyserApi#isBedrockPlayer` 相当）を全 UI 呼び出しに散らばらせる必要がある。
- Bedrock フォームには MiniMessage 相当の装飾がなく、書き分けが必要になる。

[BedrockDialog](https://github.com/f0reachARR/BedrockDialog) は Java/Bedrock の描画を単一 API（`UnifiedDialog`）で抽象し、プレイヤー種別を自動判定して振り分ける Paper プラグインである。
`ConfirmDialog` / `NoticeDialog` / `MultiButtonDialog` / `InputDialog` の 4 型で Job プラグインが必要とする UI を過不足なく表現できる。

トレードオフを整理する。

BedrockDialog を採用する場合。

- Job プラグインのコードは Java/Bedrock を意識せず 1 系統で書ける。
- Bedrock 対応が「BedrockDialog を server dependency に足す」で完結する。
- 追加の外部プラグイン依存が 1 つ増える。
- BedrockDialog 側の互換性維持に依存する（API 変更の追随コスト）。
- Bedrock 側では MiniMessage 装飾が strip される、コールバックがメインスレッドでないなどのプラットフォーム差異がそのまま制約になる。

BedrockDialog を採用しない場合の代替。

- **Java 専用**：Bedrock プレイヤーには Dialog が出ず、専業選択・変更・ステータス確認ができない。イベント参加を実質 Java 限定にすることになり、要件と合わない。
- **自前で二系統実装**：Paper Dialog と Geyser フォームの両方を Job プラグイン側で持ち、プレイヤー種別で分岐する。実装コスト、テスト面、文言の同期に負荷が大きい。BedrockDialog が提供する抽象を自作するのに等しい。

## 決定

Job プラグインの Dialog 実装には BedrockDialog を使う。
Job プラグインからは常に `UnifiedDialog`（`ConfirmDialog` / `NoticeDialog` / `MultiButtonDialog` / `InputDialog`）を組み立て、`BedrockDialog.get().show(player, dialog)` で表示する。
Paper `Dialog` API を Job プラグインから直接呼ぶことは避ける（BedrockDialog を経由する）。

依存宣言は `paper-plugin.yml` の `dependencies.server` に `BedrockDialog: { load: BEFORE, required: true }` を置く。
BedrockDialog がサーバに導入されていない環境では Job プラグインを起動しない。

Bedrock プラットフォームの制約（[07-ui.md](../07-ui.md) の「Bedrock Edition 対応の注意点」）は Job プラグイン側のコードでそのまま前提として扱う。
主要なもの。

- MiniMessage 装飾は Bedrock 側で strip される。装飾に依存した情報表現を作らない。
- コールバックはネットワークスレッドから呼ばれる可能性がある。Bukkit API 呼び出しは `Bukkit.getScheduler().runTask` にディスパッチする。
- `closeDialog` の programmatic close は Bedrock 側では Floodgate が必須。Job プラグイン内では明示 close に依存した動線を作らない。
- `InputDialog` のスライダ step は Bedrock 側で整数に丸められる。Job プラグインで使う場合は整数 step を前提にする。
- on-close コールバックは Bedrock 側で発火しない。close 契機でのステート遷移を組まない。

## 結果

- Java/Bedrock 双方のプレイヤーが Job プラグインの Dialog を同じフローで扱える。
- Job プラグインの UI コードは単一系統で保守され、プラットフォーム分岐が散らばらない。
- BedrockDialog の API 変更に追随するコストが発生する。BedrockDialog のバージョン更新は Job プラグインの動作確認とあわせて行う。
- 装飾に依存する UI 案は設計段階で採用しない（要件として抜ける）。

## 選択しなかった代替案

**Java 専用**　Bedrock プレイヤーを排除するのは運用要件に反するため不採用。

**自前で二系統実装**　BedrockDialog が提供する抽象を Job プラグインが自作することになり、二重コストと不整合リスクが大きい。BedrockDialog の未来のメンテナンスに強く懸念が出た時点で再検討する。
