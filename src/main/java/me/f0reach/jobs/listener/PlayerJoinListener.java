package me.f0reach.jobs.listener;

import me.f0reach.jobs.specialty.SpecialtyService;
import me.f0reach.jobs.ui.SpecialtySelectDialog;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * プレイヤーログイン時に SpecialtyService の cache を warm し、
 * 未選択なら SpecialtySelectDialog を開く。
 *
 * spec/07-ui.md 「初回ログイン時の専業選択」を参照。
 */
public final class PlayerJoinListener implements Listener {

    private final SpecialtyService specialtyService;
    private final SpecialtySelectDialog selectDialog;
    private final boolean showSelectDialogOnJoin;

    public PlayerJoinListener(
            SpecialtyService specialtyService,
            SpecialtySelectDialog selectDialog,
            boolean showSelectDialogOnJoin
    ) {
        this.specialtyService = specialtyService;
        this.selectDialog = selectDialog;
        this.showSelectDialogOnJoin = showSelectDialogOnJoin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        specialtyService.loadPlayer(player.getUniqueId());
        if (showSelectDialogOnJoin && specialtyService.isFirstTime(player.getUniqueId())) {
            // 少し遅延させないと Dialog がまだ届かない場合がある。
            selectDialog.open(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        specialtyService.unloadPlayer(event.getPlayer().getUniqueId());
    }
}
