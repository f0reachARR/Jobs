package me.f0reach.jobs.ui;

import me.f0reach.bedrockdialog.dialog.MultiButtonDialog;
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
 * 初回選択 Dialog。全 job を列挙し、ボタン押下で SpecialtyService#select を呼ぶ。
 * spec/07-ui.md 「初回ログイン時の専業選択」を参照。
 */
public final class SpecialtySelectDialog {

    private final I18n i18n;
    private final JobRegistry jobRegistry;
    private final SpecialtyService specialtyService;
    private final DialogService dialogService;

    public SpecialtySelectDialog(
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
        MultiButtonDialog.Builder builder = MultiButtonDialog.builder()
                .title(i18n.format(player, DialogTexts.DIALOG_SELECT_TITLE))
                .body(i18n.format(player, DialogTexts.DIALOG_SELECT_BODY));

        for (JobDefinition job : jobRegistry.all()) {
            String jobIdValue = job.id().value();
            TagResolver resolver = TagResolver.resolver(
                    Placeholder.parsed("job", jobIdValue),
                    Placeholder.parsed("display_name", job.displayName())
            );
            Component label = i18n.format(player, DialogTexts.DIALOG_SELECT_BUTTON, resolver);
            builder.button(label, p -> onSelected(p, job.id()));
        }

        dialogService.show(player, builder.build());
    }

    private void onSelected(Player player, JobId jobId) {
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
                case SpecialtyChangeResult.NoChange n -> {
                    // 既に選択済み。無視。
                }
                case SpecialtyChangeResult.CooldownRemaining c -> {
                    // select 経路では発生しないが switch の網羅性のため。
                }
            }
        });
    }
}
