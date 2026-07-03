# パーミッション

Job プラグインが宣言するパーミッションノードと、その適用箇所を定義する。
Paper 標準の Bukkit パーミッションを使い、`paper-plugin.yml` の `permissions:` 節で宣言する。

## 設計方針

- **プレイヤー参加を前提**：ジョブ制度はサーバ全員が対象なので、コマンド系は `default: true` にする（[01-overview.md](./01-overview.md)）。
- **管理系は op のみ**：`/jobs reload` などサーバ状態を変える操作は `default: op`。
- **バイパス系は明示付与**：報酬パイプラインの各ゲート（専業判定、自動化対策、日次キャップ、単調性ペナルティ、変更クールダウン）を個別に無視できるノードを用意し、`default: false`。運営イベント時に特定ランクへ付与したり、スタッフのテスト作業に使う。
- **職業別 permission は持たない**：`jobs.job.<id>` のような「この職業を選べる権限」は導入しない（[ADR-0002](./adr/0002-non-specialty-actions-discarded.md) の「全員が 1 職を選ぶ」前提と噛み合わない）。将来必要になれば `SpecialtyListDialog` の候補フィルタとして追加する余地はある。
- **アクション別 permission も持たない**：`jobs.action.<key>` 級の粒度は運用が破綻するため採用しない。ジョブ単位 YAML で足りる。

## ノード一覧

### コマンド系（default: true）

| ノード | 適用箇所 | 説明 |
|---|---|---|
| `jobs.command.use` | Brigadier `Commands.literal("jobs")` の `.requires` | `/jobs` ツリー自体の入口。false ならタブ補完から `/jobs` が消える。 |
| `jobs.command.select` | `/jobs select` サブコマンド | 専業選択・変更・cooldown ダイアログを開く操作。 |
| `jobs.command.info` | `/jobs info [<job>]` サブコマンド | ジョブ条件の閲覧。選択・cooldown 状態に依らない。 |
| `jobs.command.status` | `/jobs status` サブコマンド | 自分のステータス表示（現専業、当日獲得、単調性、次回変更可能時刻）。 |

未権限時は `.requires` によって Brigadier がタブ補完から自動で除外する。
実行を試みると Paper 標準の "Unknown command" が返る。ジョブ制度自体の存在を伏せた運用と噛み合うため、明示的な「権限がありません」メッセージは出さない。

### 管理系（default: op）

| ノード | 適用箇所 | 説明 |
|---|---|---|
| `jobs.admin` | 親ノード | 配下すべてを true にする。上位ランクへの一括付与用。 |
| `jobs.admin.reload` | `/jobs reload` | YAML / lang / tag cache / advancement datapack を再読込。 |
| `jobs.admin.set` | `/jobs admin set <player> <job>` | 他プレイヤーの専業を強制付与する。オフラインでも可（[05-persistence.md](./05-persistence.md) の `player_job` を直接更新し、オンラインなら caches も同期）。`player_job_history` に `actor='admin'` で 1 行 append される。cooldown_base_at は now に更新するため、直後 1 回の cooldown はここから起算される。 |
| `jobs.admin.reset-cooldown` | `/jobs admin reset-cooldown <player>` | 変更クールダウンをクリアする。`player_job.cooldown_base_at` を `Instant.EPOCH` に上書きし、`SpecialtyService#nextAvailableAt` が過去を返す状態にする。履歴 (`player_job_history`) には行を残さない（専業は変わっていないため）。 |
| `jobs.admin.inspect` | `/jobs admin inspect <player>` | 他プレイヤーの `/jobs status` 相当を閲覧する。オフラインでも DB から現状（current job / cooldown_base_at / 当日累計）を出す。variety の ring buffer は memory 常駐なので、オフライン時は「取得不可」を明示する。 |
| `jobs.admin.pay` | `/jobs admin pay <player> <amount> [reason]` | 手動で報酬を支給する。パイプラインは通さず、`VaultEconomyAdapter#deposit` と `ActionLogWriteQueue#enqueue` を直接叩く。`action_key='admin:manual'`。`JobActionPaidEvent` は発火する（Quest / 株プラグイン側で手動支給を認識できるようにするため）。 |
| `jobs.admin.reset-daily-cap` | `/jobs admin reset-daily-cap <player>` | 対象プレイヤーの当日日次キャップ累計をリセットする。`daily_reward_total` の当日 row を削除、オンラインなら `DailyTotalCache` の該当 entry を 0 リセット。scope=`per_job` の場合、次回ログインで `action_log` から再計算されて元に戻るため一時的な効果に留まる（[04-reward-pipeline.md](./04-reward-pipeline.md)）。 |
| `jobs.admin.reset-variety` | `/jobs admin reset-variety <player>` | 対象プレイヤーの単調性 ring buffer をクリアする。`VarietyPenaltyEvaluator#unload(uuid)` を呼ぶだけの operation で memory-only。オフライン相手には「対象がオフラインです」を返す。 |
| `jobs.admin.flush` | `/jobs admin flush` | `BatchFlushWorker` を即時 flush する。事前 restart や書き込み障害の切り分けに使う。 |
| `jobs.admin.actions` | `/jobs admin actions <player> [--since=1h] [--limit=20]` | 対象プレイヤーの直近 `action_log` を chat に出力する。監査、「報酬が入っていない」問い合わせの一次切り分け。async で `ActionLogRepository` を叩く。 |
| `jobs.admin.stats` | `/jobs admin stats [<job>]` | 職業別在籍数、当日総支払、rare 発火率などの集計を chat に出力する。async。 |

管理系サブコマンドを追加する場合は、`Permissions.java` に定数を足し、`jobs.admin` の `children:` にも並べる。追加した永続化 API（例：`PlayerJobRepository#resetCooldownBase`）は [05-persistence.md](./05-persistence.md) 側にも反映する。

### バイパス系（default: false）

報酬パイプラインとクールダウンの各ゲートに一対一対応させる。
プレイヤー本人の `Player#hasPermission` を各段階の入口で参照する。

| ノード | 適用箇所（パイプライン段階） | 効果 |
|---|---|---|
| `jobs.bypass.specialty` | [04-reward-pipeline.md](./04-reward-pipeline.md) 段階 2「専業判定」 | 専業外アクションでも報酬・ログを発生させる。 |
| `jobs.bypass.anti-automation` | 段階 3「自動化対策」 | 6 種すべての 0 判定を通過させる。 |
| `jobs.bypass.daily-cap` | 段階 6「内蔵 Modifier > daily_cap」 | 日次キャップを無視。 |
| `jobs.bypass.variety-penalty` | 段階 6「内蔵 Modifier > variety_penalty」 | 倍率 1.0 固定。ring buffer の更新は継続する。 |
| `jobs.bypass.cooldown` | `SpecialtyService#change` | 専業変更クールダウンを無視。次回変更可能時刻の表示ロジックは通常通り。 |
| `jobs.bypass.*` | 親ノード（`children:` で上記 5 つを一括付与） | すべてのバイパスを許可。 |

**バイパス時の行動ログ**：バイパスが発動した場合も行動ログには通常通り書き込む。無記録にすると業績指標や監査に穴が空くため。
将来 `action_log` に `bypassed_flags` カラムを足して監査経路を分離する余地は残す（現段階では未実装）。

**バイパス通知**：バイパスが発動したことを都度プレイヤーへ通知する `notify.bypass_active` は当面持たない。頻度が高くチャットを埋めるため。監査用途では管理者側のログ集計で追う。

## `paper-plugin.yml` の宣言

```yaml
permissions:
    jobs.command.use:
        description: /jobs コマンドの実行を許可
        default: true
    jobs.command.select:
        description: /jobs select の実行を許可
        default: true
    jobs.command.info:
        description: /jobs info の実行を許可
        default: true
    jobs.command.status:
        description: /jobs status の実行を許可
        default: true

    jobs.admin:
        description: Jobs プラグインの管理系コマンドをすべて許可
        default: op
        children:
            jobs.admin.reload: true
            jobs.admin.set: true
            jobs.admin.reset-cooldown: true
            jobs.admin.inspect: true
            jobs.admin.pay: true
            jobs.admin.reset-daily-cap: true
            jobs.admin.reset-variety: true
            jobs.admin.flush: true
            jobs.admin.actions: true
            jobs.admin.stats: true
    jobs.admin.reload:
        description: /jobs reload の実行を許可
        default: op
    jobs.admin.set:
        description: /jobs admin set の実行を許可
        default: op
    jobs.admin.reset-cooldown:
        description: /jobs admin reset-cooldown の実行を許可
        default: op
    jobs.admin.inspect:
        description: /jobs admin inspect の実行を許可
        default: op
    jobs.admin.pay:
        description: /jobs admin pay の実行を許可
        default: op
    jobs.admin.reset-daily-cap:
        description: /jobs admin reset-daily-cap の実行を許可
        default: op
    jobs.admin.reset-variety:
        description: /jobs admin reset-variety の実行を許可
        default: op
    jobs.admin.flush:
        description: /jobs admin flush の実行を許可
        default: op
    jobs.admin.actions:
        description: /jobs admin actions の実行を許可
        default: op
    jobs.admin.stats:
        description: /jobs admin stats の実行を許可
        default: op

    jobs.bypass.specialty:
        description: 専業外アクションでも報酬を受け取れる
        default: false
    jobs.bypass.anti-automation:
        description: 自動化対策 0 判定を無視する
        default: false
    jobs.bypass.daily-cap:
        description: 日次キャップを無視する
        default: false
    jobs.bypass.variety-penalty:
        description: 単調性ペナルティを無視する
        default: false
    jobs.bypass.cooldown:
        description: 専業変更クールダウンを無視する
        default: false
    jobs.bypass.*:
        description: すべての Jobs バイパス権限を許可
        default: false
        children:
            jobs.bypass.specialty: true
            jobs.bypass.anti-automation: true
            jobs.bypass.daily-cap: true
            jobs.bypass.variety-penalty: true
            jobs.bypass.cooldown: true
```

## クライアント同期

Brigadier の `.requires` はコマンドツリーをクライアントへ配信する時点で評価される。
LuckPerms などの権限管理プラグインで動的にノードを付け外しした場合、Job プラグイン側からは `Player#updateCommands()` を明示的には呼ばない（[modern-commands.md](../paper/modern-commands.md#requires メソッド)）。
権限管理プラグインが付与時に自動で `updateCommands()` を呼ぶ想定に乗る。

バイパス系は毎回の pipeline 実行で `hasPermission` を評価するため、権限変更は次のアクションから即時反映される。

## 拡張プラグインとの関係

- **イベント側プラグイン**：期間ボーナスなどで「特定ランクだけ倍率を上げる」ような場合は、`jobs.bypass.*` ではなく、`JobRewardModifier` 側で `player.hasPermission("<自プラグイン>.event.xxx")` を見る。バイパス系は Job プラグイン内蔵ゲート専用に閉じる。
- **Quest プラグイン**：Quest プラグインは Job プラグインが発火する `JobActionPaidEvent` を購読するのみで、Job プラグイン側のパーミッションを気にしなくてよい。バイパスで発生したアクションも通常イベントとして届く。

## 関連文書

- [04-reward-pipeline.md](./04-reward-pipeline.md)　バイパスが差し込まれるパイプライン各段
- [06-public-api.md](./06-public-api.md)　拡張点との責務分割
- [modern-commands.md](../paper/modern-commands.md)　Paper の Brigadier で `.requires` を使う書式
