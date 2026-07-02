package me.f0reach.jobs.yaml;

import java.util.ArrayList;
import java.util.List;

/**
 * YAML ロード中に発生したエラーを蓄積するコレクタ。
 * 起動時に一括ログ出力し、当該 job だけスキップする用途。
 */
public final class YamlErrors {

    public record Entry(String file, String path, String message) {}

    private final List<Entry> entries = new ArrayList<>();

    public void add(String file, String path, String message) {
        entries.add(new Entry(file, path, message));
    }

    public boolean hasErrors() {
        return !entries.isEmpty();
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }
}
