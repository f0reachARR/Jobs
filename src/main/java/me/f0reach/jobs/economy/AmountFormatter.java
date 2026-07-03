package me.f0reach.jobs.economy;

/**
 * 報酬額を表示用文字列にフォーマットする。
 *
 * <p>本番配線は {@link VaultAmountFormatter} で Vault の {@code Economy#format(double)} に委譲する。
 * Economy provider は通貨単位や小数桁を自前で持つため、通貨表現の実体はそちらに集約する。
 * テスト用途では簡易実装を差し込む。
 */
@FunctionalInterface
public interface AmountFormatter {

    String format(double amount);
}
