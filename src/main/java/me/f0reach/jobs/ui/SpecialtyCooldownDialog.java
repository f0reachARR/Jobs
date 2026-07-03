package me.f0reach.jobs.ui;

import me.f0reach.bedrockdialog.dialog.NoticeDialog;
import me.f0reach.jobs.domain.job.JobId;
import me.f0reach.jobs.i18n.I18n;
import me.f0reach.jobs.specialty.SpecialtyService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 変更 cooldown 中の Notice Dialog。
 *
 * spec/07-ui.md 「変更クールダウンダイアログ」を参照。
 * ここからは職業選択の導線を出さず、閉じるボタンだけを提供する。
 */
public final class SpecialtyCooldownDialog {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final I18n i18n;
    private final SpecialtyService specialtyService;
    private final DialogService dialogService;

    public SpecialtyCooldownDialog(
            I18n i18n,
            SpecialtyService specialtyService,
            DialogService dialogService
    ) {
        this.i18n = i18n;
        this.specialtyService = specialtyService;
        this.dialogService = dialogService;
    }

    /** cooldown 中でなければ何もしないので、呼び出し側は事前に nextAvailableAt を判定する。 */
    public void open(Player player, JobId current, Instant nextAvailable) {
        Duration remaining = Duration.between(Instant.now(), nextAvailable);
        TagResolver resolver = TagResolver.resolver(
                Placeholder.parsed("current_job", current.value()),
                Placeholder.parsed("next_available", TS.format(nextAvailable)),
                Placeholder.parsed("remaining", formatDuration(remaining))
        );
        Component body = i18n.format(player, DialogTexts.DIALOG_CHANGE_COOLDOWN_BODY, resolver);
        NoticeDialog dialog = NoticeDialog.builder()
                .title(i18n.format(player, DialogTexts.DIALOG_CHANGE_COOLDOWN_TITLE))
                .body(body)
                .dismissLabel(i18n.format(player, DialogTexts.DIALOG_CHANGE_DISMISS))
                .build();
        dialogService.show(player, dialog);
    }

    private static String formatDuration(Duration d) {
        long totalSec = Math.max(0, d.toSeconds());
        long days = totalSec / 86400;
        long hours = (totalSec % 86400) / 3600;
        long minutes = (totalSec % 3600) / 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }
}
