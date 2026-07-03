# 公開 API

Job プラグインは他プラグインから利用できる API を提供する。
中心はイベント、サービスインタフェース、拡張点の三種である。

## イベント

### JobActionPaidEvent

報酬パイプラインの最終段（[04-reward-pipeline.md](./04-reward-pipeline.md) の段階 11）で発火する。
報酬が確定し、Economy への送金も完了したあとである。
Splitter 適用後の値も含む。

Quest プラグインはこのイベントを購読し、クエスト進捗を更新する。
業績指標バッチ（株プラグイン）は直接 `ActionLogQueryService` を叩くため、このイベントを購読しなくてもよい。

```java
public class JobActionPaidEvent extends Event {
  public Player getPlayer();
  public String getJobId();
  public String getActionKey();     // 派生キー
  public double getBaseReward();    // rare 適用後、Modifier 適用前 (丸め後)
  public double getFinalReward();   // Modifier 適用後、Splitter 適用前 (丸め後)
  public double getNetPaid();       // Splitter 削減後の実際の入金額 (丸め後)
  public boolean isRareHit();
  public int getAmount();
  public Instant getOccurredAt();
}
```

`getBaseReward` / `getFinalReward` / `getNetPaid` は `config.yml` の `reward.decimals` と `reward.rounding_mode` で丸めた値を返す（[04-reward-pipeline.md](./04-reward-pipeline.md) の段階 9、[ADR-0019](./adr/0019-decimal-reward.md)）。
購読側でさらに丸め直す必要はない。

このイベントは async event として発火する。
Bukkit main thread での即時実行が必要な購読者は、自前で main thread に戻す。

### JobSpecialtyChangedEvent

プレイヤーが専業を選択または変更したときに発火する。
初回選択（`previousJobId` が null）も同じイベントを使う。

```java
public class JobSpecialtyChangedEvent extends Event {
  public Player getPlayer();
  public @Nullable String getPreviousJobId();
  public String getNewJobId();
  public Instant getChangedAt();
}
```

Quest プラグインは `auto_start` クエストの起動契機としてこのイベントを購読する。

## ActionLogQueryService

行動ログの集計クエリ。
Quest プラグイン、株プラグイン、運営ツールが利用する。

```java
public interface ActionLogQueryService {
  long countActions(UUID player, ActionFilter filter, TimeRange range);
  double sumReward(UUID player, ActionFilter filter, TimeRange range);
  Set<String> distinctKeys(UUID player, ActionFilter filter, TimeRange range);
  int continuousStreakSec(UUID player, ActionFilter filter, TimeRange range);
  double maxUnitPrice(UUID player, ActionFilter filter, TimeRange range);
}

public class ActionFilter {
  public @Nullable String jobId;
  public @Nullable String actionKey;          // 完全一致
  public @Nullable String actionKeyPrefix;    // 前方一致（"break:" など）
}

public class TimeRange {
  public Instant from;     // inclusive
  public Instant to;       // exclusive
}
```

呼び出しは非同期前提とする。
Bukkit main thread から直接呼ぶと MySQL クエリで thread を止めるため、`CompletableFuture` で wrap する補助メソッドを別途提供することを検討する。

## 拡張点

イベント側プラグインが Job プラグインの報酬パイプラインに介入するための拡張点。

### JobRewardModifier

報酬パイプラインの段階 7 で適用される。
報酬額に対する倍率、加算、減算を行う。

```java
public interface JobRewardModifier {
  ModifiedReward modify(JobRewardContext ctx);
  int getPriority();    // 小さいほど先に適用
  String getId();
}

public class JobRewardContext {
  public Player getPlayer();
  public String getJobId();
  public String getActionKey();
  public double getCurrentReward();  // ここまでの段階で確定している報酬 (未丸め)
  public boolean isRareHit();
}

public class ModifiedReward {
  public double reward;
  public @Nullable String tag;       // ログ用の Modifier 識別タグ
}
```

`getCurrentReward` と `reward` は丸める前の値である（[ADR-0019](./adr/0019-decimal-reward.md)）。
Modifier 実装側で丸めを行うと合成順で結果が変わるため、丸めはパイプライン末尾の丸めステージに一本化する。

実装の典型例。

- 期間ボーナス：参加日からの経過時間に応じて倍率を掛ける。
- 挽回イベント倍率：イベントカレンダーから当該時間の倍率を取得して掛ける。
- ツール強化アイテム保有：プレイヤーの所有フラグを見て倍率を掛ける。

### JobRewardSplitter

報酬パイプラインの段階 8 で適用される。
最終報酬の一部を別口座へ流すか、外部システムに通知する。

```java
public interface JobRewardSplitter {
  Split split(JobRewardSplitContext ctx);
  int getPriority();
  String getId();
}

public class JobRewardSplitContext extends JobRewardContext {
  public double getRewardAfterModifiers();
}

public class Split {
  public double deductedFromPlayer;  // プレイヤーから差し引く額 (未丸め)
  public List<Transfer> transfers;   // 差し引いた額の振り分け先
}
```

`getRewardAfterModifiers` と `deductedFromPlayer` も丸め前の `double` である。
プラグイン境界の最終丸めは Job プラグイン側で行うため、Splitter は素の比率計算だけを書けばよい。

実装の典型例。

- 社員拠出 20%：10% を会社財布へ、10% を自社株自動付与原資へ。
- 救済ローン天引き：20% をローン返済へ。

Splitter は宣言順に適用する。
2 つ目以降の Splitter は、前の Splitter で差し引いた残額に対して計算する。

## QuestService

Quest プラグインを別プラグインに分離した方針（[ADR-0007](./adr/0007-quest-as-separate-plugin.md)）に従い、Job プラグインは Quest フレームワーク本体を持たない。
ただし将来 Quest プラグインが書き込む `quest_progress` テーブルへの参照や、`auto_start` クエストとの連動は Quest プラグイン側の責務とする。

Job プラグインは Quest プラグインの存在を前提にしない。
Job プラグイン単体でも報酬パイプラインと専業制度は完結する。

## ライフサイクル

拡張点の登録は `LifecycleEvents.JOB_PLUGIN_READY`（Job プラグインが定義する独自ライフサイクル）で受け付ける。
プラグインの enable 順序に依存しないよう、登録 API は何度でも呼べる（同じ ID の Modifier/Splitter を後から差し替えできる）。

unregister は `getId()` で行う。

## パーミッションとの境界

拡張プラグイン側で「特定ランクだけ倍率を上げる」ような判定は `JobRewardModifier` の中で `player.hasPermission("<自プラグイン>.event.xxx")` を見る形で書く。
Job プラグイン内蔵ゲート（専業判定、自動化対策、日次キャップ、単調性ペナルティ、変更クールダウン）を無視するための `jobs.bypass.*` は Job プラグイン専用に閉じており、拡張プラグイン側が新設したり参照したりする対象ではない。
詳細は [08-permissions.md](./08-permissions.md) を参照。

## 関連 ADR

- [ADR-0007 Quest を別プラグインに分離する](./adr/0007-quest-as-separate-plugin.md)
- [ADR-0012 報酬パイプラインの拡張点](./adr/0012-reward-modifier-extension.md)
- [ADR-0019 報酬額を小数値として扱う](./adr/0019-decimal-reward.md)
