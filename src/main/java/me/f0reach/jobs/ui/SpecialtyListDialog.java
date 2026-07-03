package me.f0reach.jobs.ui;

import me.f0reach.bedrockdialog.dialog.MultiButtonDialog;
import me.f0reach.jobs.config.PluginConfig;
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

/**
 * 職業一覧 Dialog。SELECT / CHANGE / INFO の 3 モードで再利用する。
 *
 * spec/07-ui.md 「専業一覧ダイアログ」を参照。
 * disclose_before_select=true のとき、ボタン押下で即確定せず {@link JobConditionsDialog} を挟む。
 */
public final class SpecialtyListDialog {

    public enum Mode {
        /** 初回選択。ボタン押下でこの職業を選ぶ (または詳細を挟む)。 */
        SELECT,
        /** 変更。現在の専業は除外して並べる。 */
        CHANGE,
        /** /jobs info の閲覧。詳細を READ_ONLY で開く。 */
        INFO
    }

    private final I18n i18n;
    private final JobRegistry jobRegistry;
    private final SpecialtyService specialtyService;
    private final DialogService dialogService;
    private final JobConditionsDialog conditionsDialog;
    private final PluginConfig.SpecialtyModeConfig specialtyModeConfig;

    public SpecialtyListDialog(
            I18n i18n,
            JobRegistry jobRegistry,
            SpecialtyService specialtyService,
            DialogService dialogService,
            JobConditionsDialog conditionsDialog,
            PluginConfig.SpecialtyModeConfig specialtyModeConfig
    ) {
        this.i18n = i18n;
        this.jobRegistry = jobRegistry;
        this.specialtyService = specialtyService;
        this.dialogService = dialogService;
        this.conditionsDialog = conditionsDialog;
        this.specialtyModeConfig = specialtyModeConfig;
    }

    public void open(Player player, Mode mode) {
        JobId current = specialtyService.currentJob(player.getUniqueId()).orElse(null);
        MultiButtonDialog.Builder builder = MultiButtonDialog.builder()
                .title(titleFor(player, mode, current))
                .body(bodyFor(player, mode, current));

        for (JobDefinition job : jobRegistry.all()) {
            if (mode == Mode.CHANGE && current != null && job.id().equals(current)) {
                continue; // 現専業は除外
            }
            TagResolver btnResolver = TagResolver.resolver(
                    Placeholder.parsed("job", job.id().value()),
                    Placeholder.parsed("display_name", job.displayName())
            );
            Component label = i18n.format(player, buttonKeyFor(mode), btnResolver);
            JobId targetJob = job.id();
            builder.button(label, p -> onJobButton(p, targetJob, mode));
        }

        dialogService.show(player, builder.build());
    }

    private Component titleFor(Player player, Mode mode, JobId current) {
        return switch (mode) {
            case SELECT -> i18n.format(player, DialogTexts.DIALOG_SELECT_TITLE);
            case CHANGE -> i18n.format(player, DialogTexts.DIALOG_CHANGE_TITLE);
            case INFO -> i18n.format(player, DialogTexts.COMMAND_INFO_LIST_TITLE);
        };
    }

    private Component bodyFor(Player player, Mode mode, JobId current) {
        return switch (mode) {
            case SELECT -> i18n.format(player, DialogTexts.DIALOG_SELECT_BODY);
            case CHANGE -> i18n.format(player, DialogTexts.DIALOG_CHANGE_BODY,
                    Placeholder.parsed("current_job", current == null ? "-" : current.value()));
            case INFO -> i18n.format(player, DialogTexts.COMMAND_INFO_LIST_BODY);
        };
    }

    private String buttonKeyFor(Mode mode) {
        return switch (mode) {
            case SELECT -> DialogTexts.DIALOG_SELECT_BUTTON;
            case CHANGE -> DialogTexts.DIALOG_CHANGE_BUTTON;
            case INFO -> DialogTexts.COMMAND_INFO_LIST_BUTTON;
        };
    }

    private void onJobButton(Player player, JobId jobId, Mode mode) {
        switch (mode) {
            case SELECT -> {
                if (specialtyModeConfig.discloseBeforeSelect()) {
                    conditionsDialog.open(player, jobId, JobConditionsDialog.Mode.PREVIEW_FOR_SELECT,
                            () -> () -> open(player, Mode.SELECT));
                } else {
                    commitSelect(player, jobId);
                }
            }
            case CHANGE -> {
                if (specialtyModeConfig.discloseBeforeSelect()) {
                    conditionsDialog.open(player, jobId, JobConditionsDialog.Mode.PREVIEW_FOR_CHANGE,
                            () -> () -> open(player, Mode.CHANGE));
                } else {
                    commitChange(player, jobId);
                }
            }
            case INFO -> conditionsDialog.open(player, jobId, JobConditionsDialog.Mode.READ_ONLY,
                    () -> () -> open(player, Mode.INFO));
        }
    }

    private void commitSelect(Player player, JobId jobId) {
        dialogService.runOnMain(() -> {
            SpecialtyChangeResult result = specialtyService.select(player, jobId);
            switch (result) {
                case SpecialtyChangeResult.Success s -> player.sendMessage(
                        i18n.format(player, DialogTexts.NOTIFY_SELECTED,
                                Placeholder.parsed("job", s.next().value()))
                );
                case SpecialtyChangeResult.UnknownJob u -> player.sendMessage(
                        i18n.format(player, DialogTexts.NOTIFY_UNKNOWN_JOB,
                                Placeholder.parsed("job", u.requested().value()))
                );
                case SpecialtyChangeResult.NoChange n -> player.sendMessage(
                        i18n.format(player, DialogTexts.COMMAND_SELECT_ALREADY)
                );
                case SpecialtyChangeResult.CooldownRemaining c -> {
                    // select 経路では発生しない
                }
            }
        });
    }

    private void commitChange(Player player, JobId newJobId) {
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

    private static String formatDuration(java.time.Duration d) {
        long totalSec = Math.max(0, d.toSeconds());
        long days = totalSec / 86400;
        long hours = (totalSec % 86400) / 3600;
        long minutes = (totalSec % 3600) / 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }
}
