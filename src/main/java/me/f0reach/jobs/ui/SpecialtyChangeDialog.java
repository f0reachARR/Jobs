package me.f0reach.jobs.ui;

import me.f0reach.bedrockdialog.dialog.MultiButtonDialog;
import me.f0reach.bedrockdialog.dialog.NoticeDialog;
import me.f0reach.jobs.domain.job.JobDefinition;
import me.f0reach.jobs.domain.job.JobId;
import me.f0reach.jobs.i18n.I18n;
import me.f0reach.jobs.registry.JobRegistry;
import me.f0reach.jobs.specialty.SpecialtyChangeResult;
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
 * 専業変更 Dialog。cooldown 内なら NoticeDialog、変更可能なら MultiButtonDialog。
 * spec/07-ui.md 「/jobs change の確認ダイアログ」を参照。
 */
public final class SpecialtyChangeDialog {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final I18n i18n;
    private final JobRegistry jobRegistry;
    private final SpecialtyService specialtyService;
    private final DialogService dialogService;

    public SpecialtyChangeDialog(
            I18n i18n,
            JobRegistry jobRegistry,
            SpecialtyService specialtyService,
            DialogService dialogService
    ) {
        this.i18n = i18n;
        this.jobRegistry = jobRegistry;
        this.specialtyService = specialtyService;
        this.dialogService = dialogService;
    }

    public void open(Player player) {
        JobId current = specialtyService.currentJob(player.getUniqueId()).orElse(null);
        if (current == null) {
            // まだ選択していないなら select 経路。
            new SpecialtySelectDialog(i18n, jobRegistry, specialtyService, dialogService).open(player);
            return;
        }

        Instant nextAvailable = specialtyService.nextAvailableAt(player.getUniqueId()).orElse(Instant.EPOCH);
        Instant now = Instant.now();
        if (nextAvailable.isAfter(now)) {
            showCooldown(player, current, nextAvailable, Duration.between(now, nextAvailable));
        } else {
            showChangeButtons(player, current);
        }
    }

    private void showCooldown(Player player, JobId current, Instant nextAvailable, Duration remaining) {
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

    private void showChangeButtons(Player player, JobId current) {
        TagResolver bodyResolver = TagResolver.resolver(
                Placeholder.parsed("current_job", current.value())
        );
        MultiButtonDialog.Builder builder = MultiButtonDialog.builder()
                .title(i18n.format(player, DialogTexts.DIALOG_CHANGE_TITLE))
                .body(i18n.format(player, DialogTexts.DIALOG_CHANGE_BODY, bodyResolver));

        for (JobDefinition job : jobRegistry.all()) {
            if (job.id().equals(current)) continue; // 現在の職業以外を選ばせる
            TagResolver btnResolver = TagResolver.resolver(
                    Placeholder.parsed("job", job.id().value()),
                    Placeholder.parsed("display_name", job.displayName())
            );
            Component label = i18n.format(player, DialogTexts.DIALOG_CHANGE_BUTTON, btnResolver);
            builder.button(label, p -> onChangeSelected(p, job.id()));
        }
        dialogService.show(player, builder.build());
    }

    private void onChangeSelected(Player player, JobId newJobId) {
        dialogService.runOnMain(() -> {
            SpecialtyChangeResult result = specialtyService.change(player, newJobId);
            switch (result) {
                case SpecialtyChangeResult.Success s -> player.sendMessage(
                        i18n.format(player, DialogTexts.NOTIFY_CHANGED,
                                TagResolver.resolver(
                                        Placeholder.parsed("previous", s.previous() == null ? "-" : s.previous().value()),
                                        Placeholder.parsed("next", s.next().value())
                                ))
                );
                case SpecialtyChangeResult.CooldownRemaining c -> player.sendMessage(
                        i18n.format(player, DialogTexts.NOTIFY_COOLDOWN,
                                Placeholder.parsed("remaining", formatDuration(c.remaining())))
                );
                case SpecialtyChangeResult.UnknownJob u -> player.sendMessage(
                        i18n.format(player, DialogTexts.NOTIFY_UNKNOWN_JOB,
                                Placeholder.parsed("job", u.requested().value()))
                );
                case SpecialtyChangeResult.NoChange n -> {
                    // 同 job 再選択。無視。
                }
            }
        });
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
