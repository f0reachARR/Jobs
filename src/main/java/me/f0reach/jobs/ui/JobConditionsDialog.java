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

import java.util.function.Supplier;

/**
 * 単職の条件を 1 画面にまとめる Dialog。
 *
 * spec/07-ui.md 「職業条件の開示ダイアログ」を参照。
 * ボタン構成は {@link Mode} で切り替える。
 * 「一覧に戻る」は {@code closeDialog} に依存せず、新しい dialog を明示的に開き直す。
 */
public final class JobConditionsDialog {

    public enum Mode {
        /** 一覧から選択フローで開く。 [この職業を選ぶ][一覧に戻る] */
        PREVIEW_FOR_SELECT,
        /** 一覧から変更フローで開く。 [この職業に変更する][一覧に戻る] */
        PREVIEW_FOR_CHANGE,
        /** /jobs info の閲覧モード。 [閉じる] または [一覧に戻る]。 */
        READ_ONLY
    }

    private final I18n i18n;
    private final JobRegistry jobRegistry;
    private final SpecialtyService specialtyService;
    private final DialogService dialogService;
    private final JobConditionsFormatter formatter;
    private final PluginConfig.SpecialtyModeConfig specialtyModeConfig;

    public JobConditionsDialog(
            I18n i18n,
            JobRegistry jobRegistry,
            SpecialtyService specialtyService,
            DialogService dialogService,
            JobConditionsFormatter formatter,
            PluginConfig.SpecialtyModeConfig specialtyModeConfig
    ) {
        this.i18n = i18n;
        this.jobRegistry = jobRegistry;
        this.specialtyService = specialtyService;
        this.dialogService = dialogService;
        this.formatter = formatter;
        this.specialtyModeConfig = specialtyModeConfig;
    }

    /**
     * @param onBack 「一覧に戻る」ボタンで呼ぶハンドラ。null なら「閉じる」ボタンにする。
     */
    public void open(Player player, JobId targetJob, Mode mode, Supplier<Runnable> onBack) {
        JobDefinition job = jobRegistry.get(targetJob).orElse(null);
        if (job == null) {
            player.sendMessage(i18n.format(player, DialogTexts.COMMAND_INFO_UNKNOWN_JOB,
                    Placeholder.parsed("job", targetJob.value())));
            return;
        }
        showDialog(player, job, mode, onBack);
    }

    private void showDialog(Player player, JobDefinition job, Mode mode, Supplier<Runnable> onBack) {
        TagResolver titleResolver = TagResolver.resolver(
                Placeholder.parsed("job", job.id().value()),
                Placeholder.parsed("display_name", job.displayName())
        );
        Component title = i18n.format(player, DialogTexts.DIALOG_INFO_TITLE, titleResolver);
        Component body = formatter.build(player, job, specialtyModeConfig.discloseRewardAmount());

        MultiButtonDialog.Builder builder = MultiButtonDialog.builder()
                .title(title)
                .body(body);

        switch (mode) {
            case PREVIEW_FOR_SELECT -> {
                builder.button(i18n.format(player, DialogTexts.DIALOG_INFO_BUTTON_SELECT),
                        p -> onSelectConfirmed(p, job.id()));
                if (onBack != null) {
                    builder.button(i18n.format(player, DialogTexts.DIALOG_INFO_BUTTON_BACK),
                            p -> runBack(onBack));
                }
            }
            case PREVIEW_FOR_CHANGE -> {
                builder.button(i18n.format(player, DialogTexts.DIALOG_INFO_BUTTON_CHANGE),
                        p -> onChangeConfirmed(p, job.id()));
                if (onBack != null) {
                    builder.button(i18n.format(player, DialogTexts.DIALOG_INFO_BUTTON_BACK),
                            p -> runBack(onBack));
                }
            }
            case READ_ONLY -> {
                if (onBack != null) {
                    builder.button(i18n.format(player, DialogTexts.DIALOG_INFO_BUTTON_BACK),
                            p -> runBack(onBack));
                } else {
                    builder.button(i18n.format(player, DialogTexts.DIALOG_INFO_BUTTON_CLOSE),
                            p -> {});
                }
            }
        }

        dialogService.show(player, builder.build());
    }

    private void runBack(Supplier<Runnable> onBack) {
        Runnable r = onBack.get();
        if (r != null) dialogService.runOnMain(r);
    }

    private void onSelectConfirmed(Player player, JobId jobId) {
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

    private void onChangeConfirmed(Player player, JobId newJobId) {
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
