package me.f0reach.jobs;

/**
 * パーミッションノード名の集約。
 * spec/08-permissions.md および paper-plugin.yml の permissions セクションと 1:1 対応する。
 *
 * <p>コード側からは常にこの定数を参照すること。paper-plugin.yml との対応は
 * {@code PermissionsIntegrityTest} が静的に検証する。
 */
public final class Permissions {

    private Permissions() {}

    // コマンド系 (default: true)
    public static final String COMMAND_USE = "jobs.command.use";
    public static final String COMMAND_SELECT = "jobs.command.select";
    public static final String COMMAND_INFO = "jobs.command.info";
    public static final String COMMAND_STATUS = "jobs.command.status";

    // 管理系 (default: op)
    public static final String ADMIN_RELOAD = "jobs.admin.reload";
    public static final String ADMIN_INSPECT = "jobs.admin.inspect";
    public static final String ADMIN_STATS = "jobs.admin.stats";
    public static final String ADMIN_ACTIONS = "jobs.admin.actions";
    public static final String ADMIN_SET = "jobs.admin.set";
    public static final String ADMIN_RESET_COOLDOWN = "jobs.admin.reset-cooldown";
    public static final String ADMIN_PAY = "jobs.admin.pay";
    public static final String ADMIN_RESET_DAILY_CAP = "jobs.admin.reset-daily-cap";
    public static final String ADMIN_RESET_VARIETY = "jobs.admin.reset-variety";
    public static final String ADMIN_FLUSH = "jobs.admin.flush";

    // バイパス系 (default: false)
    public static final String BYPASS_SPECIALTY = "jobs.bypass.specialty";
    public static final String BYPASS_ANTI_AUTOMATION = "jobs.bypass.anti-automation";
    public static final String BYPASS_DAILY_CAP = "jobs.bypass.daily-cap";
    public static final String BYPASS_VARIETY_PENALTY = "jobs.bypass.variety-penalty";
    public static final String BYPASS_COOLDOWN = "jobs.bypass.cooldown";
}
