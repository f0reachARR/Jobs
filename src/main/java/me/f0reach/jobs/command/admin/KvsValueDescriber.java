package me.f0reach.jobs.command.admin;

import me.f0reach.jobs.antiautomation.OperatorTracker;
import me.f0reach.jobs.kvs.KvsKeys;
import me.f0reach.jobs.ui.DialogTexts;

import java.util.UUID;
import java.util.function.Function;

/**
 * KVS の不透明な {@code byte[]} を、key の prefix から用途を推定して人間向けに読み下す。
 *
 * <p>lang のキーと 1 個の {@code <value>} placeholder に落とすところまでを担い、
 * MiniMessage 化は呼び出し側 ({@link KvsCommands}) に任せる。
 * こうしておくと Bukkit に触れずに単体テストできる。
 */
final class KvsValueDescriber {

    /**
     * @param textKey lang のキー
     * @param value   {@code <value>} placeholder に流す文字列 (不要なキーでは空文字)
     */
    record Described(String textKey, String value) {}

    private KvsValueDescriber() {}

    /**
     * @param nameResolver operator UUID を表示名に変換する。解決できなければ null を返してよい
     */
    static Described describe(String key, byte[] raw, Function<UUID, String> nameResolver) {
        if (key.startsWith(KvsKeys.PREFIX_PLACE)) {
            return new Described(DialogTexts.COMMAND_ADMIN_KVS_VALUE_PLACED, "");
        }
        if (key.startsWith(KvsKeys.PREFIX_TRADE)) {
            return new Described(DialogTexts.COMMAND_ADMIN_KVS_VALUE_TRADED, "");
        }
        if (key.startsWith(KvsKeys.PREFIX_OP)) {
            // operator が null (= hopper 等による投入) と Player 投入とで意味が正反対なので必ず出し分ける。
            UUID operator = OperatorTracker.decodeOperator(raw);
            if (operator == null) {
                return new Described(DialogTexts.COMMAND_ADMIN_KVS_VALUE_OPERATOR_AUTOMATION, "");
            }
            String name = nameResolver.apply(operator);
            return new Described(DialogTexts.COMMAND_ADMIN_KVS_VALUE_OPERATOR_PLAYER,
                    name == null ? operator.toString() : name);
        }
        return new Described(DialogTexts.COMMAND_ADMIN_KVS_VALUE_UNKNOWN, toHex(raw));
    }

    /** 未知の prefix 用のフォールバック表示。長い値は頭だけ出す。 */
    static String toHex(byte[] raw) {
        if (raw == null || raw.length == 0) return "(empty)";
        int shown = Math.min(raw.length, 16);
        StringBuilder sb = new StringBuilder(shown * 2 + 8);
        for (int i = 0; i < shown; i++) {
            sb.append(String.format("%02x", raw[i]));
        }
        if (raw.length > shown) sb.append("... (").append(raw.length).append(" bytes)");
        return sb.toString();
    }
}
