# 報酬パイプライン

報酬パイプラインは、Bukkit イベントまたは advancement 達成をトリガとして、報酬の算出と支払いを行う一連の処理である。
Track A と Track B はマッチ確定後に合流し、以降は共通の流れをたどる。

## パイプラインの段階

```
[Bukkit Event] または [PlayerAdvancementDoneEvent]
   ↓
（1）Matcher：YAML rewards を順にスキャンし、first match wins で確定
   ↓
（2）専業判定：プレイヤーの専業 = このジョブか? 否なら完全に return
   ↓
（3）自動化対策：spawner_origin / unplanted_crop / recently_placed_break / auto_fed_processing / villager_repeat_trade / breed_non_player_breeder 該当時は 0 確定
   ↓
（4）基礎報酬：reward 固定値または乱数範囲 × amount（値は double）
   ↓
（5）rare ロール：rare.chance ヒット時、reward を rare.reward に上書きし announce
   ↓
（6）内蔵 Modifier：variety_penalty → daily_cap
   ↓
（7）拡張 Modifier chain：イベント側が登録したものを優先度順
   ↓
（8）Splitter chain：イベント側が登録したものを順序付きで
   ↓
（9）丸め：base_reward / final_reward / net_paid を config の decimals と rounding_mode で丸める
   ↓
（10）Economy へ送金
   ↓
（11）行動ログを非同期書き込み
   ↓
（12）advancement 経路のみ：revokeCriteria()
```

## 各段階の責務

### 1. Matcher

`rewards` 配列を YAML 上の宣言順にスキャンし、最初にマッチしたエントリを採用する。
マッチ条件と派生キーの生成規則は [03-action-detection.md](./03-action-detection.md) を参照。

該当エントリが無ければ、以降の段階に進まず処理を終える（行動ログにも書かない）。

### 2. 専業判定

プレイヤーの専業 ID と、マッチしたエントリが属するジョブ ID を比較する。
一致しない場合、報酬を払わず、行動ログも書かず、戻る（[ADR-0002](./adr/0002-non-specialty-actions-discarded.md)）。

未専業のプレイヤーは、すべてのアクションがこの段階で落ちる。

### 3. 自動化対策

`anti_automation` で有効化されているフラグに応じて、無効化条件を確認する（[ADR-0011](./adr/0011-builtin-anti-automation.md)）。
無効化と判定された場合、報酬を 0 に固定したまま以降の段階に進む。
0 確定でもログには書く（[ADR-0002](./adr/0002-non-specialty-actions-discarded.md) と異なり、専業の正当なアクションを「悪用未遂で 0」として残す情報価値がある）。

判定は次の順で走る。

1. `spawner_origin_kills`：`Entity.getEntitySpawnReason() == SPAWNER` なら 0。
2. `unplanted_crop_harvest`：Block PDC に「植えた」フラグが無ければ 0（作物ブロックのみ対象）。
3. `recently_placed_break`：KVS に `place:*` キーが残っていれば 0（Ageable 以外のブロックのみ対象、[ADR-0016](./adr/0016-recently-placed-break.md)）。
4. `auto_fed_processing`：KVS の `op:*` キーの `operator_uuid` が null または未登録なら 0（`item_smelted` と `item_brewed`、[ADR-0017](./adr/0017-operator-tracking-common.md)）。
5. `villager_repeat_trade`：KVS の `trade:<villager>:<recipe>` に前回取引が残っていれば 0（`villager_traded`）。
6. `breed_non_player_breeder`：`EntityBreedEvent#getBreeder` が Player でなければ 0（`entity_bred`）。
7. TNT 起爆判定：`via_tnt` の指定と一致しない場合はマッチ側で弾かれる（段階 1 で処理済み）。

いずれかで 0 確定した場合、それ以降の判定は評価不要（次段階以降も 0 のまま進む）。

0 判定を起こした reason に対して `anti_automation.notify.action_bar.<reason>` が true なら、
プレイヤーへ ActionBar で `notify.anti_automation.<reason>` の lang メッセージを送る。
ActionBar 送信は main thread から `Player#sendActionBar` を直接叩く。
通知の有効化は config.yml のグローバルフラグのみ（per-job override は無い）。

### 4. 基礎報酬

`reward` フィールドから固定値または範囲乱数で値を取り出す。
値は `double` として保持し、この段階では丸めない（[ADR-0019](./adr/0019-decimal-reward.md)）。
amount 解釈は次の通り。

- `item_smelted`：`FurnaceExtractEvent#getItemAmount` を掛ける。
- `item_crafted`：`CraftItemEvent` で実際に取り出したスタック数を掛ける（シフトクリックの複数取り出しに対応）。
- `villager_traded`：取引成立時の受け取り個数を掛ける。
- `item_brewed`：`BrewEvent` の出力 slot 数（0〜3）を掛ける。
- それ以外の `on` は amount 常に 1。

### 5. rare ロール

`rare.chance` を seed としてヒット判定する。
ヒット時は基礎報酬を `rare.reward` で置き換え、`announce` メッセージをサーバ全体にブロードキャストする。
非ヒット時は基礎報酬のまま次段階へ進む。

### 6. 内蔵 Modifier

#### variety_penalty

直近 `window` 件のアクションキー集合から、最多キーの占有比率を求める。
`curve` の `up_to` の昇順で比較し、初めて `比率 ≤ up_to` を満たすエントリの `multiplier` を採用する。
報酬に倍率を掛けて確定する。

直近 `window` 件は in-memory ring buffer で保持する。
プレイヤーのログイン時に MySQL から最新 `window` 件を読み込んで初期化する。

ring buffer が `window` 件に満たない間は penalty を発動しない（`multiplier = 1.0` として素通し、記録のみ進める）。
サンプルが揃わないうちに ratio が 1.0 にスパイクして誤発動するのを避けるため。
`/jobs status` の表示も同じゲートに従い、未充填のあいだは「なし」として扱う。

`hide_numbers: true` のとき、`disclosed_message` 以外の数値情報を Dialog UI に出さない。

#### daily_cap

その日の累計報酬を取得し、`amount` との差分を確認する。
報酬を支払うと差分を超える場合、超過分だけ報酬を削る。
日次累計が既に上限に達している場合は報酬 0。
`scope: total` は全ジョブ合算、`scope: per_job` は職業別。

### 7. 拡張 Modifier chain

イベント側プラグインが `JobRewardModifier` として登録したものを、優先度の昇順で適用する。
詳細と契約は [06-public-api.md](./06-public-api.md) を参照。

期間ボーナス、挽回イベント倍率、ツール強化などはここで掛かる。

### 8. Splitter chain

イベント側プラグインが `JobRewardSplitter` として登録したものを、宣言順に適用する。
各 Splitter は「最終報酬の一部を別口座へ流す」「外部システムに通知する」といった副作用を持つ。
社員拠出 20%（10% 会社財布、10% 自社株自動付与）や、救済ローン 20% 天引きはここに置く。

Splitter が削った分はプレイヤーに渡らないが、行動ログには `final_reward` として「Modifier 適用後、Splitter 適用前」の値を記録する。
Splitter の削減量を行動ログに反映するかどうかは、運用情報として別系統で記録する。

### 9. 丸め

`config.yml` の `reward.decimals` と `reward.rounding_mode` に従い、`base_reward` / `final_reward` / `net_paid` を同じ桁数・同じ方式で丸める（[ADR-0019](./adr/0019-decimal-reward.md)）。
`decimals: 0` のとき整数へ丸まる。
以降の段階（Economy 送金、行動ログ、`JobActionPaidEvent`）は丸め後の値だけを扱う。

丸めは `java.math.BigDecimal#setScale(decimals, roundingMode)` を経由して行う。
Modifier や Splitter が個別に丸めることは禁止し、丸めはこの段階に一本化する。

### 10. Economy へ送金

`final_reward` から Splitter の削減量を引いた残額（`net_paid`）を、Economy プラグインの `transfer` で送金する。
送金先はプレイヤー口座、Reason は `JobReward`、Tag に派生キーとジョブ ID を載せる。
残額が 0 のときは送金しない。

### 11. 行動ログを書く

`action_log` テーブルに 1 行 INSERT する。
書き込みは非同期キュー経由で行い、`BlockBreakEvent` の同期完了をブロックしない。
スキーマは [05-persistence.md](./05-persistence.md) を参照。

### 12. revokeCriteria

advancement 経路の場合のみ、最後に `AdvancementProgress.revokeCriteria()` を呼ぶ。
これにより同じ advancement が次のイベントでも発火可能になる。

## エラーハンドリング

各段階でエラーが起きた場合の方針は次の通り。

- Matcher：マッチ失敗は正常な経路。
- 専業判定：エラーなし。
- 自動化対策：判定例外時は対策を「無効」とみなして通過させる（保守的）。
- 基礎報酬・rare ロール：YAML 不正時は起動時に弾くため、ここでは起きない想定。
- 内蔵 Modifier：内部例外時はその Modifier を skip し、ログに記録。
- 拡張 Modifier・Splitter：個別の Modifier/Splitter が例外を投げた場合、その 1 件のみ skip。chain 全体は継続。
- 丸め：`rounding_mode: UNNECESSARY` を指定していて実際には端数がある場合、`ArithmeticException` が上がる。当該行動の報酬を 0 として続行し、WARNING をログに残す。
- Economy 送金：送金失敗は致命扱い。リトライ後、`action_log` に `final_reward` を負号で書き込み（または別フラグで）、運営に通知。
- 行動ログ：書き込み失敗時はリトライ。リトライ失敗は致命扱い。

## スレッドモデル

Bukkit イベントは main thread で発火する。
パイプラインの段階 1 から 9 までは main thread で同期実行する。
段階 10 の送金は Economy プラグインの契約に従う（同期前提）。
段階 11 のログ書き込みのみ async に流す。

`PlayerAdvancementDoneEvent` 経由のときも同様、main thread で実行する。
`revokeCriteria` も main thread で行う必要がある。

## 関連 ADR

- [ADR-0001 ハイブリッド検知](./adr/0001-hybrid-action-detection.md)
- [ADR-0002 専業外アクションを無視する](./adr/0002-non-specialty-actions-discarded.md)
- [ADR-0006 単調性ペナルティを内蔵する](./adr/0006-builtin-variety-penalty.md)
- [ADR-0011 自動化対策を内蔵する](./adr/0011-builtin-anti-automation.md)
- [ADR-0012 報酬パイプラインの拡張点](./adr/0012-reward-modifier-extension.md)
- [ADR-0015 追跡ストレージを KVS 抽象化する](./adr/0015-kvs-abstraction.md)
- [ADR-0016 recently_placed_break は placer 非依存](./adr/0016-recently-placed-break.md)
- [ADR-0017 投入者追跡を共通化する](./adr/0017-operator-tracking-common.md)
- [ADR-0019 報酬額を小数値として扱う](./adr/0019-decimal-reward.md)
