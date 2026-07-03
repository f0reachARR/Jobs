package me.f0reach.jobs.economy;

/**
 * Vault Economy の {@code format(double)} を委譲する {@link AmountFormatter} 本番実装。
 *
 * <p>Economy provider が通貨名 / 単数複数形 / 小数桁を自前で管理するため、
 * 通貨表示の一貫性はここに集約する。ジョブ側は数値を渡すだけでよい。
 */
public final class VaultAmountFormatter implements AmountFormatter {

    private final VaultEconomyAdapter economy;

    public VaultAmountFormatter(VaultEconomyAdapter economy) {
        this.economy = economy;
    }

    @Override
    public String format(double amount) {
        return economy.raw().format(amount);
    }
}
