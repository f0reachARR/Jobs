# Job プラグイン仕様

このディレクトリは Job プラグインの設計仕様と「設計判断記録」（ADR）を格納する。

イベント固有の数値（21 日の経済設計、職業の名前、報酬額）はここに含めない。
Job プラグインは「アクション検知、報酬計算、行動ログ、専業制度、報酬パイプラインの拡張点」を担う汎用基盤として定義する。
イベント側プラグインは YAML 設定と拡張点経由で個別のルールを差し込む。

## ドキュメント

- [01-overview.md](./01-overview.md)　Job プラグインのスコープと依存関係
- [02-yaml-schema.md](./02-yaml-schema.md)　ジョブ定義 YAML の仕様
- [03-action-detection.md](./03-action-detection.md)　アクションの検知方式
- [04-reward-pipeline.md](./04-reward-pipeline.md)　報酬計算の流れと拡張点
- [05-persistence.md](./05-persistence.md)　MySQL スキーマと KVS 追跡ストレージ
- [06-public-api.md](./06-public-api.md)　他プラグインに公開する API とイベント
- [07-ui.md](./07-ui.md)　DialogAPI を使う場面と仕様
- [08-permissions.md](./08-permissions.md)　パーミッションノードと適用箇所

## ADR

[adr/README.md](./adr/README.md) に索引を置く。
