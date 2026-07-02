package me.f0reach.jobs.ui;

import me.f0reach.bedrockdialog.dialog.NoticeDialog;
import me.f0reach.jobs.domain.job.JobId;
import me.f0reach.jobs.i18n.I18n;
import me.f0reach.jobs.specialty.SpecialtyService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;

/**
 * ステータス表示 Dialog。
 * Phase 4 では現在の専業のみを表示する。
 * Phase 6/10 で「本日の獲得額」「単調性ペナルティ」「次回変更可能時刻」を補う。
 */
public final class StatusDialog {

    private final I18n i18n;
    private final SpecialtyService specialtyService;
    private final DialogService dialogService;

    public StatusDialog(I18n i18n, SpecialtyService specialtyService, DialogService dialogService) {
        this.i18n = i18n;
        this.specialtyService = specialtyService;
        this.dialogService = dialogService;
    }

    public void open(Player player) {
        JobId current = specialtyService.currentJob(player.getUniqueId()).orElse(null);
        TagResolver resolver = TagResolver.resolver(
                Placeholder.parsed("current_job", current == null ? "-" : current.value())
        );
        NoticeDialog dialog = NoticeDialog.builder()
                .title(i18n.format(player, DialogTexts.DIALOG_STATUS_TITLE))
                .body(i18n.format(player, DialogTexts.DIALOG_STATUS_BODY, resolver))
                .dismissLabel(i18n.format(player, DialogTexts.DIALOG_STATUS_DISMISS))
                .build();
        dialogService.show(player, dialog);
    }
}
