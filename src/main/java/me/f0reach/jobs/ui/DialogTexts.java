package me.f0reach.jobs.ui;

/**
 * lang key の集約。
 * Dialog / command 系のメッセージ key を定数で持ち、i18n の欠損を静的に検出しやすくする。
 */
public final class DialogTexts {

    private DialogTexts() {}

    // /jobs 共通
    public static final String COMMAND_LOADING = "command.loading";
    public static final String COMMAND_PLAYER_ONLY = "command.player_only";

    // /jobs select
    public static final String COMMAND_SELECT_ALREADY = "command.select.already_selected";
    public static final String COMMAND_SELECT_OPENED = "command.select.opened";

    // /jobs change
    public static final String COMMAND_CHANGE_OPENED = "command.change.opened";
    public static final String COMMAND_CHANGE_NO_SELECTION = "command.change.no_selection";

    // /jobs status (Phase 4 minimum)
    public static final String COMMAND_STATUS_NO_SPECIALTY = "command.status.no_specialty";

    // Dialog: select
    public static final String DIALOG_SELECT_TITLE = "dialog.select.title";
    public static final String DIALOG_SELECT_BODY = "dialog.select.body";
    public static final String DIALOG_SELECT_BUTTON = "dialog.select.button";

    // Dialog: change
    public static final String DIALOG_CHANGE_TITLE = "dialog.change.title";
    public static final String DIALOG_CHANGE_BODY = "dialog.change.body";
    public static final String DIALOG_CHANGE_BUTTON = "dialog.change.button";
    public static final String DIALOG_CHANGE_COOLDOWN_TITLE = "dialog.change.cooldown.title";
    public static final String DIALOG_CHANGE_COOLDOWN_BODY = "dialog.change.cooldown.body";
    public static final String DIALOG_CHANGE_DISMISS = "dialog.change.dismiss";

    // Dialog: status
    public static final String DIALOG_STATUS_TITLE = "dialog.status.title";
    public static final String DIALOG_STATUS_BODY = "dialog.status.body";
    public static final String DIALOG_STATUS_DISMISS = "dialog.status.dismiss";

    // Notifications after selection
    public static final String NOTIFY_SELECTED = "notify.selected";
    public static final String NOTIFY_CHANGED = "notify.changed";
    public static final String NOTIFY_UNKNOWN_JOB = "notify.unknown_job";
    public static final String NOTIFY_COOLDOWN = "notify.cooldown";
}
