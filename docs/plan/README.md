# 実装計画

`docs/spec/` の仕様を、実装単位に落とし込んだドキュメント群を置く。
仕様（What）と設計判断（Why）は `spec/` に、コード分割と実装順序（How）はここに書く。

## ドキュメント

- [class-structure.md](./class-structure.md)　パッケージ構成と主要クラスの責務
- [phases.md](./phases.md)　実装フェーズと着手順序
- [threading.md](./threading.md)　スレッドモデルとライフサイクル

## 対象範囲

Phase 1 の Job プラグイン本体を対象とする。
Quest プラグインおよび株プラグインは範囲外（`spec/06-public-api.md` の API を通じて連携する外部プラグイン）。
