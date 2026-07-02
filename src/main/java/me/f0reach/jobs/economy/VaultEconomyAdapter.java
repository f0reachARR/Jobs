package me.f0reach.jobs.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Vault Economy へのアダプタ。
 *
 * <p>spec/04-reward-pipeline.md 「Economy へ送金」および docs/plan/class-structure.md 「economy」を参照。
 * paper-plugin.yml で Vault は required=true にしているので、起動時に取れなければ致命エラー。
 */
public final class VaultEconomyAdapter {

    private final Economy economy;

    private VaultEconomyAdapter(Economy economy) {
        this.economy = economy;
    }

    public static VaultEconomyAdapter setup() {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager()
                .getRegistration(Economy.class);
        if (rsp == null) {
            throw new IllegalStateException(
                    "No Vault Economy provider registered. Install one (e.g. EssentialsX, CMI)."
            );
        }
        return new VaultEconomyAdapter(rsp.getProvider());
    }

    /**
     * プレイヤーに amount を入金する。amount <= 0 のときは何もせず true を返す。
     */
    public boolean deposit(OfflinePlayer player, double amount) {
        if (amount <= 0) return true;
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    public Economy raw() { return economy; }
}
