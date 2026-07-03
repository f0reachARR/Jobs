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
