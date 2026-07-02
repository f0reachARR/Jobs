package me.f0reach.jobs.detection.advancement;

import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * 同梱 advancement データパック (resources/data/jobs/advancement/*.json) を
 * サーバの datapack 領域に展開する。
 *
 * <p>class-structure.md 「detection.advancement.AdvancementDatapackInstaller」を参照。
 * ここでは shaded jar 内から /data/jobs/advancement/**  を、
 * サーバ側の {@code world/datapacks/Jobs/} へコピーする最小実装を提供する。
 * 空でも動く (何もコピーしない) ように、resource が無ければ warn だけ残して return する。
 */
public final class AdvancementDatapackInstaller {

    private static final String RESOURCE_ROOT = "data/jobs/advancement";

    private final Plugin plugin;

    public AdvancementDatapackInstaller(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * ワールド (デフォルトワールド) の datapack 領域に展開する。
     * 既存ファイルは常に上書きする。
     */
    public int install() {
        var worlds = plugin.getServer().getWorlds();
        if (worlds.isEmpty()) return 0;
        Path worldFolder = worlds.get(0).getWorldFolder().toPath();
        Path targetRoot = worldFolder.resolve("datapacks").resolve("Jobs")
                .resolve("data").resolve("jobs").resolve("advancement");
        try {
            Files.createDirectories(targetRoot);
            Path packMcmeta = targetRoot.getParent().getParent().getParent().resolve("pack.mcmeta");
            if (!Files.exists(packMcmeta)) {
                Files.writeString(packMcmeta, """
                        {
                          "pack": {
                            "pack_format": 41,
                            "description": "Jobs plugin custom advancements"
                          }
                        }
                        """);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to prepare datapack directory", e);
            return 0;
        }

        List<String> names = listBundledAdvancements();
        int copied = 0;
        for (String name : names) {
            String resource = RESOURCE_ROOT + "/" + name;
            try (InputStream in = plugin.getResource(resource)) {
                if (in == null) continue;
                Path dest = targetRoot.resolve(name);
                try (OutputStream out = Files.newOutputStream(dest, StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING)) {
                    in.transferTo(out);
                }
                copied++;
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to copy advancement resource " + resource, e);
            }
        }
        if (copied > 0) {
            plugin.getLogger().info("Installed " + copied + " advancements to " + targetRoot);
        }
        return copied;
    }

    /**
     * shaded jar の resources から advancement ファイル名リストを取り出す。
     * URI が file:// (開発時) or jar:file:// (実運用) のどちらでも扱える。
     */
    private List<String> listBundledAdvancements() {
        URL url = plugin.getClass().getClassLoader().getResource(RESOURCE_ROOT);
        if (url == null) return List.of();
        try {
            URI uri = url.toURI();
            if ("jar".equals(uri.getScheme())) {
                try (FileSystem fs = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
                    return collectJsonFiles(fs.getPath(RESOURCE_ROOT));
                }
            } else {
                return collectJsonFiles(Path.of(uri));
            }
        } catch (URISyntaxException | IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to enumerate advancement resources", e);
            return List.of();
        }
    }

    private static List<String> collectJsonFiles(Path root) throws IOException {
        try (Stream<Path> stream = Files.list(root)) {
            return stream
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .map(p -> p.getFileName().toString())
                    .toList();
        }
    }
}
