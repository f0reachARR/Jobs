# ADR-0016 recently_placed_break を placer 非依存の位置追跡にする

## ステータス

受け入れ

## 背景

BlockPlaceEvent と BlockBreakEvent の対を悪用した自動化には 2 種類のパターンがある。

第一のパターンは単独プレイヤーによる place-then-break マクロである。
プレイヤーが同じ位置に置いて壊してを繰り返し、`block_broken` の報酬を稼ぐ。

第二のパターンは 2 人以上で組む形である。
プレイヤー A がブロックを大量に置き、プレイヤー B が壊す。
A と B の職業は互いに違っていてもよく、B は `block_broken` の専業ジョブから報酬を得る。

初期の検討では **self_placed_break** として「自分で置いた block を壊した場合 0」という判定を採用する案があった。
placer UUID を追跡し、破壊者と一致するなら報酬 0 にする形である。

しかし、この判定では第二のパターンを塞げない。
A が置いて B が壊す構図では、破壊者から見て placer は他人であり、self 判定は素通りする。

一方、「直近に置かれた block の破壊は placer を問わず報酬 0」とすれば、両パターンを同時に塞げる。
判定に必要な情報は位置と配置時刻のみで、placer UUID は不要になる。

## 決定

`anti_automation.recently_placed_break` を採用する。
placer は問わず、`window_sec` 以内に置かれた block を壊した場合、報酬を 0 に固定する。

```yaml
anti_automation:
  recently_placed_break:
    window_sec: 3600
```

追跡データは以下の形で KVS（[ADR-0015](./0015-kvs-abstraction.md)）に持つ。

- key：`place:<world-uuid>:<x>:<y>:<z>`
- value：`placed_at`（epoch millis）
- ttl：`window_sec`

BlockPlaceEvent で `put`、BlockBreakEvent で報酬パイプラインの段階 3 に入る直前に `get` する。
残っていれば `via_recently_placed=true` を立てて 0 確定する。

作物ブロック（`Ageable`）は追跡対象外とする。
作物は成長時間の関係で 1 時間窓に自然に該当してしまい、正当な収穫を巻き込む頻度が高い。
作物側は既存の `unplanted_crop_harvest`（[ADR-0011](./0011-builtin-anti-automation.md)）で判定されるため、二重の対策は不要である。

`window_sec` のデフォルトは 3600 とする。
1 時間の窓は、置きミスの即時撤去のような正当行動が復活するまでの間隔として妥当で、多人数連携の place-store-break を抑える強さの折衷点でもある。

## 結果

- 単独マクロと多人数連携の両パターンが同じ判定で塞がれる。
- 正当な即時撤去（設置ミスの撤去、飾りの張り替え）は `window_sec` の間だけ報酬 0 になる。
- placer UUID を追跡しないため、KVS のエントリは配置時刻のみで済み、書き込みコストが軽い。
- 作物の追跡は既存の `unplanted_crop_harvest` に一任する。
  BlockPlaceEvent listener 側で「Ageable なら KVS に書かない」分岐を持つ。
- 起動時の in-memory 追跡は空から始まる。
  再起動直後のプレイヤーは配置直後の破壊が正当に払われるため、追跡窓に穴が空く。
  窓が 1 時間と短いため実害は限定的である。

## 選択しなかった代替案

self_placed_break は 2 人組の抜け穴を残す。
単独プレイヤーの悪用しか塞げず、対策としての強度が足りない。

「破壊者と placer が一致した場合のみ 0」の判定は、実装が重い割に効果が弱い。
placer UUID を保持する必要が生じる一方、抜け穴は残る。

「Player が置いた block の破壊は全て 0」の全面禁止は、他プレイヤーの建築物の解体を含めて `block_broken` の稼働自体を封じてしまう。
正当な建築行為を巻き込むため採らない。
