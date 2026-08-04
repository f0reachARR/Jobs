package me.f0reach.jobs.antiautomation;

import me.f0reach.jobs.i18n.I18n;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 自動化対策で 0 判定が発火したときに、ActionBar でプレイヤーへ理由を通知する。
 * config.yml の {@code anti_automation.notify.action_bar.<reason>} が true の reason だけ通知する。
 *
 * <p>lang key は {@code notify.anti_automation.<reason>} を引く。
 * MiniMessage は装飾が Bedrock で落ちても文言だけで意味が通る形で書く。
 */
public class AntiAutomationNotifier {

    private static final String LANG_KEY_PREFIX = "notify.anti_automation.";

    private final I18n i18n;
    private final Supplier<Map<String, Boolean>> perReasonEnabled;

    /** 固定の設定で組む。テストなど reload を伴わない用途向け。 */
    public AntiAutomationNotifier(I18n i18n, Map<String, Boolean> perReasonEnabled) {
        this(i18n, fixed(perReasonEnabled));
    }

    private static Supplier<Map<String, Boolean>> fixed(Map<String, Boolean> perReasonEnabled) {
        Map<String, Boolean> copy =
                perReasonEnabled == null ? Map.of() : Map.copyOf(perReasonEnabled);
        return () -> copy;
    }

    /**
     * config を参照して組む。{@code /jobs reload} で action_bar の on/off が差し替わるので、
     * 通知のたびに supplier から読み直す。
     */
    public AntiAutomationNotifier(I18n i18n, Supplier<Map<String, Boolean>> perReasonEnabled) {
        this.i18n = i18n;
        this.perReasonEnabled = perReasonEnabled;
    }

    public void notify(Player player, String reason) {
        if (player == null || reason == null) return;
        Map<String, Boolean> enabled = perReasonEnabled.get();
        if (enabled == null || !enabled.getOrDefault(reason, false)) return;
        Component msg = i18n.format(player, LANG_KEY_PREFIX + reason);
        player.sendActionBar(msg);
    }
}
