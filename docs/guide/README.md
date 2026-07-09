# Jobs プラグイン 利用ガイド

このディレクトリは Jobs プラグインの「使い方」を利用者・管理者それぞれの視点でまとめたガイド集である。
仕様の背景や設計判断は [../spec/](../spec/) と [../plan/](../plan/) を参照する。

## 利用者向け

- [player-basic.md](./player-basic.md)　基本機能。専業の選び方、`/jobs` コマンド、報酬とクールダウンの読み方。ふつうに遊ぶ人はまずこれ。
- [player-advanced.md](./player-advanced.md)　発展機能。単調性ペナルティ・自動化対策・日次キャップ・rare 報酬など、仕組みを深く知りたい人向け。

## 管理者向け

- [admin.md](./admin.md)　サーバに導入する管理者向け。インストール、`config.yml` と `jobs/*.yml` の書き方、`/jobs admin` 系コマンド、パーミッション、多言語対応。
