# ADR-0019 報酬額を小数値として扱う

## ステータス

受け入れ

## 背景

これまで報酬額は整数で扱ってきた（`RewardAmount.Fixed` および `Range` の値、行動ログの `base_reward`/`final_reward`、`JobActionPaidEvent` の各 getter がすべて `int`）。

経済側の運用で、単価が 1 未満のアクションや、倍率をかけた結果が小数になる報酬を扱いたい要求が出た。
たとえば「10 単位で採取したときに 0.5 単位だけ払う」「専業外倍率 0.7 をかけた結果を切り詰めずに保持する」といった扱いである。
整数のままだと、YAML 側で表現できないだけでなく、パイプライン途中の倍率適用（`variety_penalty`、`daily_cap` の超過分削り、拡張 Modifier）が整数除算になり、意図した比率で計算できない。

報酬額を小数化するときの論点は次の三つに整理できる。

- **精度と丸めをどこで決めるか**：Economy 側は最終的にサーバ運営のポリシーで桁数を揃えたい（「小数第 2 位までにしたい」など）。ジョブ定義や Modifier の中に丸めを分散させると、運営が桁数を変更したいときに全体を書き換えることになる。
- **丸めをいつ行うか**：パイプラインの途中で丸めると、Modifier や Splitter の合成順によって結果が変わる。倍率と分配を含めた最終値だけを丸める形が素直である。
- **DB スキーマの型**：`int` から変えるとカラム型が変わる。開発中のため既存データのマイグレーションは考えない。

丸め方については、Java 標準の `RoundingMode` に多くの選択肢があり、経済側の判断で `HALF_UP`（四捨五入）や `FLOOR`（切り捨て）を選び分けたい。
プラグイン側で選択肢を絞る合理的な理由は無く、`RoundingMode` の enum をそのまま受け付ければよい。

## 決定

報酬額を `double` 型に統一し、パイプライン末尾で一度だけ丸める。

- **YAML の `reward` フィールド**：整数と小数の両方を受け付ける。`{ min, max }` の範囲指定も小数を許容する。
- **`RewardAmount.Fixed` および `Range`**：値と `roll()` の戻り値を `double` にする。ここでは丸めない。
- **`PipelineContext` の `baseReward`、`finalReward`、`netPaid`**：`double` にする。
- **丸めのタイミング**：`Splitter chain`（[04-reward-pipeline.md](../04-reward-pipeline.md) の段階 8）と Economy 送金の間に丸めステージを一つ挿入する。丸めステージは新しい段階 9 になり、Economy 送金は段階 10 に繰り下がる。丸めステージは `baseReward`、`finalReward`、`netPaid` をまとめて丸める。以降の段階（Economy 送金、行動ログ）はすべて丸め後の値を使う。
- **丸めの桁数と方式**：`config.yml` の `reward` セクションで指定する。
  - `reward.decimals`：小数点以下の桁数。`0` のとき従来通りの整数のみ。デフォルト `0`。
  - `reward.rounding_mode`：Java の `java.math.RoundingMode` の名称（`HALF_UP`、`HALF_EVEN`、`HALF_DOWN`、`UP`、`DOWN`、`CEILING`、`FLOOR`、`UNNECESSARY`）。デフォルト `HALF_UP`。
- **DB スキーマ**：`action_log.base_reward`、`action_log.final_reward`、`daily_reward_total.total_reward` を `DECIMAL(20, 6)` にする。桁数は `reward.decimals` の設定可能上限（6 桁）を包含する。既存データのマイグレーションは行わず、開発環境で初期化する運用でよい。
- **公開 API**：`JobActionPaidEvent` の `getBaseReward()` / `getFinalReward()` / `getNetPaid()`、`ActionLogQueryService` の `sumReward` / `maxUnitPrice` の戻り値を `double` にする。`JobRewardModifier` と `JobRewardSplitter` の受け渡す報酬値も `double` にする。

丸めは `java.math.BigDecimal` を経由して行う。
`BigDecimal.valueOf(double).setScale(decimals, roundingMode).doubleValue()` で戻す。
浮動小数点誤差の混入を許容範囲に収めるのが目的で、途中の計算まで `BigDecimal` にはしない。

## 結果

- 報酬倍率の適用が、意図した比率で動く。整数除算による切り捨てが混入しない。
- 経済側の桁数ポリシーは、`config.yml` の 2 行の設定変更で切り替えられる。ジョブ定義や Modifier 実装に丸めロジックが分散しない。
- 丸めステージがパイプラインで一箇所に固定されるため、Modifier と Splitter の合成順による結果の食い違いが起きない。
- `JobActionPaidEvent` と `ActionLogQueryService` の戻り値型が変わるため、既存の購読者・照会側（Quest プラグイン、株プラグインなど、実装があれば）はキャストを外して読み直す必要がある。
- DB カラムを `DECIMAL(20, 6)` に固定するため、`reward.decimals` を 7 桁以上に指定すると丸め後の値が保存段階で再度切り詰められる。設定側で `decimals ≤ 6` を強制する。
- `daily_reward_total` の集計は `DECIMAL` の `SUM` で行う。`DOUBLE` の `SUM` に伴う誤差累積は起きない。

## 選択しなかった代替案

- **報酬を `BigDecimal` に統一する案**：計算経路すべてが `BigDecimal` になり、Modifier の掛け算・加算が API として重くなる。パイプライン中の計算誤差は最終丸めで吸収できる範囲であり、この置き換えに見合う利益は無い。
- **丸めを Modifier ごとに任せる案**：拡張 Modifier の実装者ごとに桁数・丸め方式が変わり、経済側のポリシー変更が全 Modifier の実装変更に波及する。丸めは経済側の判断であり、Modifier の関心事ではない。
- **固定精度（常に小数第 2 位で丸める）にする案**：桁数を運営側で変える余地がなくなる。仮想通貨的な単位（小数第 4 位まで扱う）や、整数運用（現状の互換）を同じ設定で切り替えたい要求に応えられない。
- **DB カラムを `DOUBLE` にする案**：`SUM(DOUBLE)` は誤差が累積する。`daily_reward_total` は毎日の累計を上書き加算する用途のため、誤差累積の影響が直接出る。`DECIMAL` の追加コスト（保存サイズと計算コスト）を払うほうが素直である。

## 関連 ADR

- [ADR-0012 報酬パイプラインの拡張点を Modifier と Splitter で公開する](./0012-reward-modifier-extension.md)
- [ADR-0018 リポジトリ層をインタフェースで切る](./0018-repository-interface.md)
