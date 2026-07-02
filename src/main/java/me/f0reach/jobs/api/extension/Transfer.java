package me.f0reach.jobs.api.extension;

import org.jetbrains.annotations.Nullable;

/**
 * Splitter が「削った額を別口座に流す」個々の振り分け先。
 * spec/06-public-api.md 「JobRewardSplitter」を参照。
 *
 * <p>各 Transfer の実際の口座操作は Splitter 実装内で行い (Job プラグインは口座種別を知らない)、
 * この record はログや監査目的の情報を運ぶだけの器。
 *
 * @param destination 振り分け先の識別子 (プラグイン間で共有する任意文字列)。
 * @param amount      振り分け額 (未丸め)。
 * @param tag         監査タグ (nullable)。
 */
public record Transfer(String destination, double amount, @Nullable String tag) {}
