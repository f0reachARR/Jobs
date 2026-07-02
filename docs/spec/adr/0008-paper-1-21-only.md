# ADR-0008 Paper 1.21+ 専用とする

## ステータス

受け入れ

## 背景

実行環境の選択肢として Spigot、Paper、Folia がある。
さらに、対象 Minecraft バージョンの選定がある。

Job プラグインの実装方針として、次の機能を活用したい場合がある。

- DialogAPI（Paper 1.21.6 以降の安定化を見越して）
- `LifecycleEventManager`（データパックとコマンド登録の標準的な hook）
- `Registry.tags()` 経由のタグ解決（データパック側のカスタムタグも扱える）
- `BrigadierCommand` API

これらは Paper が独自に提供する API である。
Spigot だけでは実装が大幅に複雑化し、NMS への依存が増える。

Folia 対応はリージョン thread モデルに合わせる必要があり、in-memory ring buffer や非同期書き込みのスレッド契約を作り直す。
Folia は当面サポートしないと割り切る。

## 決定

実行環境は Paper 1.21 以降を専用ターゲットとする。
Spigot や Bukkit 互換性は維持しない。
Folia 対応は当面行わない。

## 結果

- Paper の独自 API（DialogAPI、Lifecycle、Registry、Brigadier）を前提として実装できる。
  NMS 直接アクセスは原則として避ける。
- サーバ運営は Paper 1.21 以降の使用が前提となる。
- 将来 Folia 対応を行う場合、別 ADR で再検討する。
  スレッドモデルの大幅な書き換えが必要になる前提とする。

## 選択しなかった代替案

Spigot 互換を維持する案は、DialogAPI を諦めるか NMS で再実装するかの 2 択になる。
NMS 実装は Minecraft 本体の更新に追従するコストが高い。
Folia 対応案は当面の運用想定（プロキシ配下の単一 Paper インスタンス）と要件が合わない。
