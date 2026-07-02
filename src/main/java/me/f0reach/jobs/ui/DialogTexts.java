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
    public static final String COMMAND_STATUS_CURRENT = "command.status.current";

    // /jobs status (Phase 6 - variety penalty / daily cap)
    public static final String COMMAND_STATUS_DAILY_TOTAL = "command.status.daily_total";
    public static final String COMMAND_STATUS_DAILY_CAP_HIT = "command.status.daily_cap_hit";
    public static final String COMMAND_STATUS_DAILY_BAR = "command.status.daily_bar";
    public static final String COMMAND_STATUS_NEXT_CHANGE = "command.status.next_change";
    public static final String COMMAND_STATUS_VARIETY_ACTIVE = "command.status.variety.active";
    public static final String COMMAND_STATUS_VARIETY_DISCLOSED = "command.status.variety.disclosed";
    public static final String COMMAND_STATUS_VARIETY_NONE = "command.status.variety.none";

    // /jobs reload (Phase 10)
    public static final String COMMAND_RELOAD_NO_PERMISSION = "command.reload.no_permission";
    public static final String COMMAND_RELOAD_OK = "command.reload.ok";
    public static final String COMMAND_RELOAD_FAILED = "command.reload.failed";

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

    // Notifications after selection
    public static final String NOTIFY_SELECTED = "notify.selected";
    public static final String NOTIFY_CHANGED = "notify.changed";
    public static final String NOTIFY_UNKNOWN_JOB = "notify.unknown_job";
    public static final String NOTIFY_COOLDOWN = "notify.cooldown";

    // Anti-automation ActionBar notifications
    // key の suffix は各 AntiAutomationCheck の REASON 定数と 1:1 対応する。
    public static final String NOTIFY_ANTI_AUTOMATION_SPAWNER_ORIGIN_KILL = "notify.anti_automation.spawner_origin_kill";
    public static final String NOTIFY_ANTI_AUTOMATION_UNPLANTED_CROP_HARVEST = "notify.anti_automation.unplanted_crop_harvest";
    public static final String NOTIFY_ANTI_AUTOMATION_RECENTLY_PLACED_BREAK = "notify.anti_automation.recently_placed_break";
    public static final String NOTIFY_ANTI_AUTOMATION_AUTO_FED_PROCESSING = "notify.anti_automation.auto_fed_processing";
    public static final String NOTIFY_ANTI_AUTOMATION_VILLAGER_REPEAT_TRADE = "notify.anti_automation.villager_repeat_trade";
    public static final String NOTIFY_ANTI_AUTOMATION_BREED_NON_PLAYER_BREEDER = "notify.anti_automation.breed_non_player_breeder";
}
