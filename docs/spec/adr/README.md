# ADR 索引

Job プラグインの設計判断記録（Architecture Decision Record）。
各 ADR は、判断したこと、その背景、選択しなかった代替案を簡潔に残す。

## 一覧

- [ADR-0001 Bukkit イベントと advancement のハイブリッド検知](./0001-hybrid-action-detection.md)
- [ADR-0002 専業外アクションは報酬と記録の双方を行わない](./0002-non-specialty-actions-discarded.md)
- [ADR-0003 match に明示 ID を持たせず派生キーで識別する](./0003-match-without-explicit-id.md)
- [ADR-0004 報酬定義は first match wins で評価する](./0004-first-match-wins.md)
- [ADR-0005 OR 条件はリスト、タグ、データパックで分担する](./0005-or-expression-strategy.md)
- [ADR-0006 単調性ペナルティを Job プラグインに内蔵する](./0006-builtin-variety-penalty.md)
- [ADR-0007 Quest を別プラグインに分離する](./0007-quest-as-separate-plugin.md)
- [ADR-0008 Paper 1.21+ 専用とする](./0008-paper-1-21-only.md)
- [ADR-0009 永続化を MySQL とする](./0009-mysql-persistence.md)
- [ADR-0010 UI は DialogAPI を主とし Inventory GUI は Job 内で使わない](./0010-dialog-api-over-inventory-gui.md)
- [ADR-0011 自動化対策を Job プラグインに内蔵する](./0011-builtin-anti-automation.md)
- [ADR-0012 報酬パイプラインの拡張点を Modifier と Splitter で公開する](./0012-reward-modifier-extension.md)
- [ADR-0013 UI 文言は YAML + MiniMessage で多言語対応する](./0013-i18n-yaml-minimessage.md)
- [ADR-0014 Dialog 実装に BedrockDialog を使う](./0014-bedrock-dialog.md)
- [ADR-0015 自動化対策の追跡ストレージを KVS 抽象化する](./0015-kvs-abstraction.md)
- [ADR-0016 recently_placed_break を placer 非依存の位置追跡にする](./0016-recently-placed-break.md)
- [ADR-0017 投入者追跡を Furnace 系と BrewingStand で共通化する](./0017-operator-tracking-common.md)
- [ADR-0018 リポジトリ層をインタフェースで切る](./0018-repository-interface.md)

## ステータスの読み方

- **受け入れ**：採用された判断。実装または運用の根拠となる。
- **置き換え**：別の ADR で覆された判断。元の ADR は履歴として残す。
- **保留**：判断を先送りにしている。後続 ADR で確定させる。
