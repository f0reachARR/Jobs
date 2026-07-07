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

    // /jobs select (現在は同 job 再選択時の防御メッセージだけ使う)
    public static final String COMMAND_SELECT_ALREADY = "command.select.already_selected";

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
    // 権限チェックは Brigadier .requires で行うため no_permission key は持たない。
    public static final String COMMAND_RELOAD_OK = "command.reload.ok";
    public static final String COMMAND_RELOAD_FAILED = "command.reload.failed";

    // /jobs admin (Phase 13)
    public static final String COMMAND_ADMIN_UNKNOWN_PLAYER = "command.admin.unknown_player";
    public static final String COMMAND_ADMIN_INSPECT_HEADER = "command.admin.inspect.header";
    public static final String COMMAND_ADMIN_INSPECT_NO_SPECIALTY = "command.admin.inspect.no_specialty";
    public static final String COMMAND_ADMIN_INSPECT_CURRENT = "command.admin.inspect.current";
    public static final String COMMAND_ADMIN_INSPECT_COOLDOWN_BASE = "command.admin.inspect.cooldown_base";
    public static final String COMMAND_ADMIN_INSPECT_NEXT_CHANGE = "command.admin.inspect.next_change";
    public static final String COMMAND_ADMIN_INSPECT_DAILY_TOTAL = "command.admin.inspect.daily_total";
    public static final String COMMAND_ADMIN_INSPECT_VARIETY_OFFLINE = "command.admin.inspect.variety_offline";
    public static final String COMMAND_ADMIN_INSPECT_VARIETY_SNAPSHOT = "command.admin.inspect.variety_snapshot";
    public static final String COMMAND_ADMIN_ACTIONS_HEADER = "command.admin.actions.header";
    public static final String COMMAND_ADMIN_ACTIONS_EMPTY = "command.admin.actions.empty";
    public static final String COMMAND_ADMIN_ACTIONS_ROW = "command.admin.actions.row";
    public static final String COMMAND_ADMIN_ACTIONS_ERROR = "command.admin.actions.error";
    public static final String COMMAND_ADMIN_STATS_HEADER = "command.admin.stats.header";
    public static final String COMMAND_ADMIN_STATS_JOB_HEADER = "command.admin.stats.job_header";
    public static final String COMMAND_ADMIN_STATS_JOB_ROW = "command.admin.stats.job_row";
    public static final String COMMAND_ADMIN_STATS_DAILY_TOTAL = "command.admin.stats.daily_total";
    public static final String COMMAND_ADMIN_STATS_ACTIONS_TODAY = "command.admin.stats.actions_today";
    public static final String COMMAND_ADMIN_STATS_RARE_HITS = "command.admin.stats.rare_hits";
    public static final String COMMAND_ADMIN_STATS_ERROR = "command.admin.stats.error";
    public static final String COMMAND_ADMIN_STATS_UNKNOWN_JOB = "command.admin.stats.unknown_job";
    public static final String COMMAND_ADMIN_SET_UNKNOWN_JOB = "command.admin.set.unknown_job";
    public static final String COMMAND_ADMIN_SET_OK_INITIAL = "command.admin.set.ok_initial";
    public static final String COMMAND_ADMIN_SET_OK_CHANGED = "command.admin.set.ok_changed";
    public static final String COMMAND_ADMIN_SET_NO_CHANGE = "command.admin.set.no_change";
    public static final String COMMAND_ADMIN_RESET_COOLDOWN_OK = "command.admin.reset_cooldown.ok";
    public static final String COMMAND_ADMIN_RESET_COOLDOWN_NO_SPECIALTY = "command.admin.reset_cooldown.no_specialty";
    public static final String COMMAND_ADMIN_PAY_OK = "command.admin.pay.ok";
    public static final String COMMAND_ADMIN_PAY_FAILED = "command.admin.pay.failed";
    public static final String COMMAND_ADMIN_PAY_INVALID_AMOUNT = "command.admin.pay.invalid_amount";
    public static final String COMMAND_ADMIN_RESET_DAILY_CAP_OK = "command.admin.reset_daily_cap.ok";
    public static final String COMMAND_ADMIN_RESET_VARIETY_OK = "command.admin.reset_variety.ok";
    public static final String COMMAND_ADMIN_RESET_VARIETY_OFFLINE = "command.admin.reset_variety.offline";
    public static final String COMMAND_ADMIN_FLUSH_OK = "command.admin.flush.ok";
    public static final String COMMAND_ADMIN_FLUSH_ERROR = "command.admin.flush.error";

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

    // Dialog: info (JobConditionsDialog and /jobs info)
    public static final String DIALOG_INFO_TITLE = "dialog.info.title";
    public static final String DIALOG_INFO_DESCRIPTION = "dialog.info.description";
    public static final String DIALOG_INFO_HEADER_REWARDS = "dialog.info.header.rewards";
    public static final String DIALOG_INFO_HEADER_VARIETY = "dialog.info.header.variety";
    public static final String DIALOG_INFO_HEADER_ANTI_AUTOMATION = "dialog.info.header.anti_automation";
    public static final String DIALOG_INFO_REWARD_TEMPLATE = "dialog.info.reward.template";
    /** {@code dialog.info.reward.label.<action_type>} をキー suffix にする。 */
    public static final String DIALOG_INFO_REWARD_LABEL_PREFIX = "dialog.info.reward.label.";
    public static final String DIALOG_INFO_REWARD_AMOUNT_FIXED = "dialog.info.reward.amount.fixed";
    public static final String DIALOG_INFO_REWARD_AMOUNT_RANGE = "dialog.info.reward.amount.range";
    public static final String DIALOG_INFO_REWARD_RARE_HINT = "dialog.info.reward.rare_hint";
    public static final String DIALOG_INFO_REWARD_TRUNCATED = "dialog.info.reward.truncated";
    public static final String DIALOG_INFO_TARGET_LIST_SEPARATOR = "dialog.info.target.list_separator";
    public static final String DIALOG_INFO_TARGET_TAG_SUFFIX = "dialog.info.target.tag_suffix";
    public static final String DIALOG_INFO_TARGET_CROP_MATURE = "dialog.info.target.crop_mature";
    public static final String DIALOG_INFO_TARGET_VIA_TNT = "dialog.info.target.via_tnt";
    public static final String DIALOG_INFO_TARGET_FISH_TREASURE = "dialog.info.target.fish_treasure";
    public static final String DIALOG_INFO_TARGET_FISH_TREASURE_ONLY = "dialog.info.target.fish_treasure_only";
    public static final String DIALOG_INFO_TARGET_ENCHANT_WITH_LEVEL = "dialog.info.target.enchant_with_level";
    public static final String DIALOG_INFO_TARGET_ENCHANT_ONLY = "dialog.info.target.enchant_only";
    public static final String DIALOG_INFO_TARGET_LEVEL_ONLY = "dialog.info.target.level_only";
    public static final String DIALOG_INFO_TARGET_REPAIR_ANVIL = "dialog.info.target.repair_anvil";
    public static final String DIALOG_INFO_TARGET_REPAIR_MENDING = "dialog.info.target.repair_mending";
    public static final String DIALOG_INFO_TARGET_CONSUME_FOOD = "dialog.info.target.consume_food";
    public static final String DIALOG_INFO_TARGET_CONSUME_DRINK = "dialog.info.target.consume_drink";
    public static final String DIALOG_INFO_TARGET_POTION = "dialog.info.target.potion";
    public static final String DIALOG_INFO_VARIETY_NONE = "dialog.info.variety.none";
    public static final String DIALOG_INFO_VARIETY_DISCLOSED = "dialog.info.variety.disclosed";
    public static final String DIALOG_INFO_VARIETY_ACTIVE_NO_MESSAGE = "dialog.info.variety.active_no_message";
    public static final String DIALOG_INFO_ANTI_AUTOMATION_NONE = "dialog.info.anti_automation.none";
    public static final String DIALOG_INFO_ANTI_AUTOMATION_SPAWNER_ORIGIN_KILL = "dialog.info.anti_automation.spawner_origin_kill";
    public static final String DIALOG_INFO_ANTI_AUTOMATION_UNPLANTED_CROP_HARVEST = "dialog.info.anti_automation.unplanted_crop_harvest";
    public static final String DIALOG_INFO_ANTI_AUTOMATION_RECENTLY_PLACED_BREAK = "dialog.info.anti_automation.recently_placed_break";
    public static final String DIALOG_INFO_ANTI_AUTOMATION_AUTO_FED_PROCESSING = "dialog.info.anti_automation.auto_fed_processing";
    public static final String DIALOG_INFO_ANTI_AUTOMATION_VILLAGER_REPEAT_TRADE = "dialog.info.anti_automation.villager_repeat_trade";
    public static final String DIALOG_INFO_ANTI_AUTOMATION_BREED_NON_PLAYER_BREEDER = "dialog.info.anti_automation.breed_non_player_breeder";
    public static final String DIALOG_INFO_BUTTON_SELECT = "dialog.info.button.select";
    public static final String DIALOG_INFO_BUTTON_CHANGE = "dialog.info.button.change";
    public static final String DIALOG_INFO_BUTTON_BACK = "dialog.info.button.back";
    public static final String DIALOG_INFO_BUTTON_CLOSE = "dialog.info.button.close";

    // /jobs info command
    public static final String COMMAND_INFO_UNKNOWN_JOB = "command.info.unknown_job";
    public static final String COMMAND_INFO_LIST_TITLE = "dialog.info.list.title";
    public static final String COMMAND_INFO_LIST_BODY = "dialog.info.list.body";
    public static final String COMMAND_INFO_LIST_BUTTON = "dialog.info.list.button";

    // Anti-automation ActionBar notifications
    // key の suffix は各 AntiAutomationCheck の REASON 定数と 1:1 対応する。
    public static final String NOTIFY_ANTI_AUTOMATION_SPAWNER_ORIGIN_KILL = "notify.anti_automation.spawner_origin_kill";
    public static final String NOTIFY_ANTI_AUTOMATION_UNPLANTED_CROP_HARVEST = "notify.anti_automation.unplanted_crop_harvest";
    public static final String NOTIFY_ANTI_AUTOMATION_RECENTLY_PLACED_BREAK = "notify.anti_automation.recently_placed_break";
    public static final String NOTIFY_ANTI_AUTOMATION_AUTO_FED_PROCESSING = "notify.anti_automation.auto_fed_processing";
    public static final String NOTIFY_ANTI_AUTOMATION_VILLAGER_REPEAT_TRADE = "notify.anti_automation.villager_repeat_trade";
    public static final String NOTIFY_ANTI_AUTOMATION_BREED_NON_PLAYER_BREEDER = "notify.anti_automation.breed_non_player_breeder";
}
