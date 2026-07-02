package me.f0reach.jobs.ui;

import me.f0reach.bedrockdialog.BedrockDialog;
import me.f0reach.bedrockdialog.dialog.UnifiedDialog;
import me.f0reach.jobs.util.AsyncExecutor;
import org.bukkit.entity.Player;

/**
 * BedrockDialog.get() を wrap し、Dialog 表示と main thread への dispatch を提供する。
 *
 * spec/07-ui.md 「Bedrock Edition 対応の注意点」を参照。
 * BedrockDialog callback は Bedrock 側でネットワークスレッドから来る可能性があるため、
 * Bukkit API を触る callback は AsyncExecutor#runOnMain 経由に統一する。
 */
public final class DialogService {

    private final AsyncExecutor asyncExecutor;

    public DialogService(AsyncExecutor asyncExecutor) {
        this.asyncExecutor = asyncExecutor;
    }

    public void show(Player player, UnifiedDialog dialog) {
        BedrockDialog.get().show(player, dialog);
    }

    /** Dialog callback から Bukkit API を叩くときに使うヘルパ。 */
    public void runOnMain(Runnable task) {
        asyncExecutor.runOnMain(task);
    }
}
