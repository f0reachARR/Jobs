package me.f0reach.jobs.i18n;

import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.TreeSet;

/**
 * 起動時に ja_jp の全 key に対して他ロケールの欠損を報告する。
 * 差分は WARNING で出力し、拒否はしない。
 */
public final class MissingKeyReporter {

    private final Plugin plugin;
    private final LocaleRegistry registry;

    public MissingKeyReporter(Plugin plugin, LocaleRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public void report() {
        Set<String> reference = registry.keys(LocaleRegistry.DEFAULT_LOCALE);
        for (String locale : registry.locales()) {
            if (locale.equals(LocaleRegistry.DEFAULT_LOCALE)) continue;
            Set<String> missing = new TreeSet<>();
            for (String key : reference) {
                if (!registry.hasKey(locale, key)) {
                    missing.add(key);
                }
            }
            if (!missing.isEmpty()) {
                plugin.getLogger().warning(
                        "Locale '" + locale + "' is missing " + missing.size()
                                + " keys: " + missing
                );
            }
        }
    }
}
