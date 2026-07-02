package me.f0reach.jobs.i18n;

import me.f0reach.jobs.util.MiniMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * lang key + TagResolver -> Component。
 *
 * MiniMessage を通して装飾を解決するため、Bedrock 側で装飾が落ちても文言だけで
 * 意味が通るように lang ファイルを書く。
 */
public final class I18n {

    private final LocaleRegistry registry;

    public I18n(LocaleRegistry registry) {
        this.registry = registry;
    }

    public Component format(String locale, String key, TagResolver... resolvers) {
        String raw = registry.get(locale, key);
        return MiniMessages.get().deserialize(raw, resolvers);
    }

    public Component format(Player player, String key, TagResolver... resolvers) {
        return format(player == null ? LocaleRegistry.DEFAULT_LOCALE : player.locale().toString(), key, resolvers);
    }

    public Component format(CommandSender sender, String key, TagResolver... resolvers) {
        if (sender instanceof Player player) {
            return format(player, key, resolvers);
        }
        return format(LocaleRegistry.DEFAULT_LOCALE, key, resolvers);
    }

    public LocaleRegistry registry() {
        return registry;
    }
}
