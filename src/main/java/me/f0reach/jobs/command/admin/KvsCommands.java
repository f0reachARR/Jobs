package me.f0reach.jobs.command.admin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.math.BlockPosition;
import me.f0reach.jobs.JobsServices;
import me.f0reach.jobs.Permissions;
import me.f0reach.jobs.antiautomation.ContainerKind;
import me.f0reach.jobs.kvs.JobsKVStoreAdmin;
import me.f0reach.jobs.kvs.KvsKeys;
import me.f0reach.jobs.ui.DialogTexts;
import me.f0reach.jobs.util.AsyncExecutor;
import me.f0reach.jobs.util.MiniMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * /jobs admin kvs サブコマンド。自動化対策の追跡データ (KVS) を覗く / 掃除する。
 *
 * <p>典型的な用途は「壊したのに報酬が入らない」の一次切り分けで、
 * {@code kvs block} で対象ブロックの追跡エントリを見て、誤検知なら {@code kvs remove} で消す。
 *
 * <p>KVS へのアクセスは in-memory 実装では軽いが、将来の Redis 実装で I/O になるため
 * すべて {@link AsyncExecutor} 経由にし、出力だけ main thread に戻す。
 * Bukkit API に触れる部分 (Block 解決・プレイヤー名解決) は main thread 側に置く。
 *
 * <p>任意の値を書き込む口は用意しない。追跡データの捏造は「本来払う報酬を 0 にする」
 * 方向にも使えてしまい、運用上のメリットに見合わないため。
 */
final class KvsCommands {

    private static final int DEFAULT_LIST_LIMIT = 20;
    private static final int MAX_LIST_LIMIT = 100;
    /** kvs block で視線の先を拾う距離。 */
    private static final int TARGET_BLOCK_RANGE = 6;

    /** list / clear が受け付ける種別リテラルと、対応する key prefix。 */
    private static final Map<String, String> KIND_PREFIXES = Map.of(
            "place", KvsKeys.PREFIX_PLACE,
            "op", KvsKeys.PREFIX_OP,
            "trade", KvsKeys.PREFIX_TRADE);

    private final AtomicReference<JobsServices> services;

    KvsCommands(AtomicReference<JobsServices> services) {
        this.services = services;
    }

    LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> clear = Commands.literal("clear")
                .requires(s -> s.getSender().hasPermission(Permissions.ADMIN_KVS_MODIFY));
        for (String kind : List.of("place", "op", "trade")) {
            clear = clear.then(Commands.literal(kind).executes(ctx -> executeClear(ctx, kind)));
        }
        // 全削除は追跡データを丸ごと落とす (= 一時的に自動化検知が抜ける) ので confirm を必須にする。
        clear = clear.then(Commands.literal("all")
                .then(Commands.literal("confirm").executes(this::executeClearAll)));

        LiteralArgumentBuilder<CommandSourceStack> list = Commands.literal("list")
                .requires(s -> s.getSender().hasPermission(Permissions.ADMIN_KVS_INSPECT))
                .executes(ctx -> executeList(ctx, "", DEFAULT_LIST_LIMIT));
        for (String kind : List.of("place", "op", "trade")) {
            String prefix = KIND_PREFIXES.get(kind);
            list = list.then(Commands.literal(kind)
                    .executes(ctx -> executeList(ctx, prefix, DEFAULT_LIST_LIMIT))
                    .then(Commands.argument("limit", IntegerArgumentType.integer(1, MAX_LIST_LIMIT))
                            .executes(ctx -> executeList(ctx, prefix,
                                    IntegerArgumentType.getInteger(ctx, "limit")))));
        }

        return Commands.literal("kvs")
                .requires(s -> s.getSender().hasPermission(Permissions.ADMIN_KVS_INSPECT)
                        || s.getSender().hasPermission(Permissions.ADMIN_KVS_MODIFY))
                .then(Commands.literal("stats")
                        .requires(s -> s.getSender().hasPermission(Permissions.ADMIN_KVS_INSPECT))
                        .executes(this::executeStats))
                .then(list)
                .then(Commands.literal("get")
                        .requires(s -> s.getSender().hasPermission(Permissions.ADMIN_KVS_INSPECT))
                        // key に ':' が入るため word()/string() では読めない。greedyString で行末まで取る。
                        .then(Commands.argument("key", StringArgumentType.greedyString())
                                .executes(this::executeGet)))
                .then(Commands.literal("block")
                        .requires(s -> s.getSender().hasPermission(Permissions.ADMIN_KVS_INSPECT))
                        .executes(ctx -> executeBlock(ctx, null))
                        .then(Commands.argument("pos", ArgumentTypes.blockPosition())
                                .executes(ctx -> executeBlock(ctx,
                                        ctx.getArgument("pos", BlockPositionResolver.class)))))
                .then(Commands.literal("remove")
                        .requires(s -> s.getSender().hasPermission(Permissions.ADMIN_KVS_MODIFY))
                        .then(Commands.argument("key", StringArgumentType.greedyString())
                                .executes(this::executeRemove)))
                .then(clear);
    }

    // --- executors ---

    private int executeStats(CommandContext<CommandSourceStack> ctx) {
        Bound bound = require(ctx);
        if (bound == null) return Command.SINGLE_SUCCESS;

        run(bound, () -> {
            List<KindCount> counts = new ArrayList<>();
            for (String kind : List.of("place", "op", "trade")) {
                counts.add(new KindCount(kind, bound.admin().count(KIND_PREFIXES.get(kind))));
            }
            return new StatsSnapshot(bound.admin().backendName(), bound.admin().size(),
                    bound.admin().maxEntries(), counts);
        }, stats -> {
            send(bound, DialogTexts.COMMAND_ADMIN_KVS_STATS_HEADER);
            send(bound, DialogTexts.COMMAND_ADMIN_KVS_STATS_BACKEND,
                    Placeholder.parsed("backend", stats.backend()));
            send(bound, DialogTexts.COMMAND_ADMIN_KVS_STATS_SIZE,
                    Placeholder.parsed("size", Long.toString(stats.size())),
                    Placeholder.parsed("max", stats.maxEntries() < 0
                            ? "-" : Long.toString(stats.maxEntries())));
            for (KindCount c : stats.counts()) {
                send(bound, DialogTexts.COMMAND_ADMIN_KVS_STATS_KIND_ROW,
                        Placeholder.parsed("kind", c.kind()),
                        Placeholder.parsed("count", Long.toString(c.count())));
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    private int executeList(CommandContext<CommandSourceStack> ctx, String prefix, int limit) {
        Bound bound = require(ctx);
        if (bound == null) return Command.SINGLE_SUCCESS;

        // limit+1 件取って、溢れているかどうかを判定する。
        run(bound, () -> bound.admin().scan(prefix, limit + 1), entries -> {
            String shownPrefix = prefix.isEmpty() ? "*" : prefix;
            send(bound, DialogTexts.COMMAND_ADMIN_KVS_LIST_HEADER,
                    Placeholder.parsed("prefix", shownPrefix),
                    Placeholder.parsed("limit", Integer.toString(limit)));
            if (entries.isEmpty()) {
                send(bound, DialogTexts.COMMAND_ADMIN_KVS_LIST_EMPTY);
                return;
            }
            Function<UUID, String> names = cachedNameResolver();
            int shown = Math.min(entries.size(), limit);
            for (int i = 0; i < shown; i++) {
                sendEntryRow(bound, entries.get(i), names);
            }
            if (entries.size() > limit) {
                send(bound, DialogTexts.COMMAND_ADMIN_KVS_LIST_TRUNCATED);
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    private int executeGet(CommandContext<CommandSourceStack> ctx) {
        Bound bound = require(ctx);
        if (bound == null) return Command.SINGLE_SUCCESS;
        String key = StringArgumentType.getString(ctx, "key").trim();

        run(bound, () -> bound.admin().inspect(key), found -> {
            send(bound, DialogTexts.COMMAND_ADMIN_KVS_GET_HEADER, Placeholder.parsed("key", key));
            if (found.isEmpty()) {
                send(bound, DialogTexts.COMMAND_ADMIN_KVS_GET_NOT_FOUND);
                return;
            }
            JobsKVStoreAdmin.Entry entry = found.get();
            KvsValueDescriber.Described described =
                    KvsValueDescriber.describe(entry.key(), entry.value(), cachedNameResolver());
            send(bound, DialogTexts.COMMAND_ADMIN_KVS_GET_VALUE,
                    Placeholder.component("described",
                            bound.services().i18n().format(bound.sender(), described.textKey(),
                                    Placeholder.parsed("value", described.value()))));
            send(bound, DialogTexts.COMMAND_ADMIN_KVS_GET_TTL,
                    Placeholder.parsed("ttl", formatTtl(entry.remainingTtl())));
            send(bound, DialogTexts.COMMAND_ADMIN_KVS_GET_RAW,
                    Placeholder.parsed("raw", KvsValueDescriber.toHex(entry.value())));
        });
        return Command.SINGLE_SUCCESS;
    }

    private int executeBlock(CommandContext<CommandSourceStack> ctx, BlockPositionResolver resolver) {
        Bound bound = require(ctx);
        if (bound == null) return Command.SINGLE_SUCCESS;
        CommandSender sender = bound.sender();

        // Block / World の解決は main thread でしかできないので、key の組み立てまでを先に済ませる。
        World world;
        int x;
        int y;
        int z;
        if (resolver != null) {
            Location origin = ctx.getSource().getLocation();
            world = origin.getWorld();
            if (world == null) {
                send(bound, DialogTexts.COMMAND_ADMIN_KVS_BLOCK_CONSOLE);
                return Command.SINGLE_SUCCESS;
            }
            BlockPosition pos;
            try {
                pos = resolver.resolve(ctx.getSource());
            } catch (Exception e) {
                send(bound, DialogTexts.COMMAND_ADMIN_KVS_ERROR,
                        Placeholder.parsed("error", String.valueOf(e.getMessage())));
                return Command.SINGLE_SUCCESS;
            }
            x = pos.blockX();
            y = pos.blockY();
            z = pos.blockZ();
        } else {
            if (!(sender instanceof Player player)) {
                send(bound, DialogTexts.COMMAND_ADMIN_KVS_BLOCK_CONSOLE);
                return Command.SINGLE_SUCCESS;
            }
            Block target = player.getTargetBlockExact(TARGET_BLOCK_RANGE);
            if (target == null) {
                send(bound, DialogTexts.COMMAND_ADMIN_KVS_BLOCK_NO_TARGET);
                return Command.SINGLE_SUCCESS;
            }
            world = target.getWorld();
            x = target.getX();
            y = target.getY();
            z = target.getZ();
        }

        UUID worldUuid = world.getUID();
        List<String> keys = new ArrayList<>();
        keys.add(KvsKeys.place(worldUuid, x, y, z));
        for (ContainerKind kind : ContainerKind.values()) {
            keys.add(KvsKeys.op(kind.tag(), worldUuid, x, y, z));
        }
        String worldName = world.getName();

        run(bound, () -> {
            List<JobsKVStoreAdmin.Entry> found = new ArrayList<>();
            for (String key : keys) {
                bound.admin().inspect(key).ifPresent(found::add);
            }
            return found;
        }, found -> {
            send(bound, DialogTexts.COMMAND_ADMIN_KVS_BLOCK_HEADER,
                    Placeholder.parsed("world", worldName),
                    Placeholder.parsed("x", Integer.toString(x)),
                    Placeholder.parsed("y", Integer.toString(y)),
                    Placeholder.parsed("z", Integer.toString(z)));
            if (found.isEmpty()) {
                send(bound, DialogTexts.COMMAND_ADMIN_KVS_BLOCK_EMPTY);
                return;
            }
            Function<UUID, String> names = cachedNameResolver();
            for (JobsKVStoreAdmin.Entry entry : found) {
                sendEntryRow(bound, entry, names);
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    private int executeRemove(CommandContext<CommandSourceStack> ctx) {
        Bound bound = require(ctx);
        if (bound == null) return Command.SINGLE_SUCCESS;
        String key = StringArgumentType.getString(ctx, "key").trim();

        run(bound, () -> {
            boolean existed = bound.admin().inspect(key).isPresent();
            if (existed) bound.services().kvStore().remove(key);
            return existed;
        }, existed -> {
            if (!existed) {
                send(bound, DialogTexts.COMMAND_ADMIN_KVS_REMOVE_NOT_FOUND,
                        Placeholder.parsed("key", key));
                return;
            }
            audit(bound, "remove key=" + key);
            send(bound, DialogTexts.COMMAND_ADMIN_KVS_REMOVE_OK, Placeholder.parsed("key", key));
        });
        return Command.SINGLE_SUCCESS;
    }

    private int executeClear(CommandContext<CommandSourceStack> ctx, String kind) {
        Bound bound = require(ctx);
        if (bound == null) return Command.SINGLE_SUCCESS;
        String prefix = KIND_PREFIXES.get(kind);

        run(bound, () -> bound.admin().removeByPrefix(prefix), removed -> {
            audit(bound, "clear kind=" + kind + " removed=" + removed);
            send(bound, DialogTexts.COMMAND_ADMIN_KVS_CLEAR_OK,
                    Placeholder.parsed("kind", kind),
                    Placeholder.parsed("count", Integer.toString(removed)));
        });
        return Command.SINGLE_SUCCESS;
    }

    private int executeClearAll(CommandContext<CommandSourceStack> ctx) {
        Bound bound = require(ctx);
        if (bound == null) return Command.SINGLE_SUCCESS;

        run(bound, () -> bound.admin().removeByPrefix(""), removed -> {
            audit(bound, "clear-all removed=" + removed);
            send(bound, DialogTexts.COMMAND_ADMIN_KVS_CLEAR_ALL_OK,
                    Placeholder.parsed("count", Integer.toString(removed)));
        });
        return Command.SINGLE_SUCCESS;
    }

    // --- helpers ---

    private void sendEntryRow(Bound bound, JobsKVStoreAdmin.Entry entry, Function<UUID, String> names) {
        KvsValueDescriber.Described described =
                KvsValueDescriber.describe(entry.key(), entry.value(), names);
        Component value = bound.services().i18n().format(bound.sender(), described.textKey(),
                Placeholder.parsed("value", described.value()));
        send(bound, DialogTexts.COMMAND_ADMIN_KVS_LIST_ROW,
                Placeholder.parsed("key", entry.key()),
                Placeholder.component("described", value),
                Placeholder.parsed("ttl", formatTtl(entry.remainingTtl())));
    }

    /**
     * KVS 操作を async に投げ、結果を main thread で描画する。
     * 例外は共通の error メッセージに落とす。
     */
    private <T> void run(Bound bound, Supplier<T> work, Consumer<T> render) {
        AsyncExecutor executor = bound.services().asyncExecutor();
        executor.supplyAsync(work).whenComplete((result, err) -> executor.runOnMain(() -> {
            if (err != null) {
                send(bound, DialogTexts.COMMAND_ADMIN_KVS_ERROR,
                        Placeholder.parsed("error", String.valueOf(err.getMessage())));
                return;
            }
            render.accept(result);
        }));
    }

    private void send(Bound bound, String key, TagResolver... placeholders) {
        bound.sender().sendMessage(bound.services().i18n().format(bound.sender(), key, placeholders));
    }

    /** 削除系はサーバログに残す (admin pay と同じ方針)。 */
    private void audit(Bound bound, String what) {
        bound.services().plugin().getLogger().info(
                "admin kvs " + what + " by " + bound.sender().getName());
    }

    /**
     * UUID → 表示名。cache 済みのプレイヤーだけを見る (Mojang API を叩かせない)。
     * 1 コマンド分の描画で使い回す前提で map を 1 回だけ組む。
     */
    private Function<UUID, String> cachedNameResolver() {
        Map<UUID, String> cache = new HashMap<>();
        boolean[] loaded = {false};
        return uuid -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) return online.getName();
            if (!loaded[0]) {
                for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                    if (op.getName() != null) cache.put(op.getUniqueId(), op.getName());
                }
                loaded[0] = true;
            }
            return cache.get(uuid);
        };
    }

    private static String formatTtl(Duration ttl) {
        long seconds = Math.max(0, ttl.getSeconds());
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return String.format(Locale.ROOT, "%dm%02ds", seconds / 60, seconds % 60);
        return String.format(Locale.ROOT, "%dh%02dm", seconds / 3600, (seconds % 3600) / 60);
    }

    /**
     * services 束縛と admin 対応 backend の 2 段チェックをまとめる。
     * 満たさない場合はここでメッセージを出し、null を返す。
     */
    private Bound require(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        JobsServices bound = services.get();
        if (bound == null) {
            sender.sendMessage(MiniMessages.get().deserialize("<gray>Jobs plugin is loading.</gray>"));
            return null;
        }
        Optional<JobsKVStoreAdmin> admin = bound.kvStore().admin();
        if (admin.isEmpty()) {
            sender.sendMessage(bound.i18n().format(sender, DialogTexts.COMMAND_ADMIN_KVS_UNSUPPORTED));
            return null;
        }
        return new Bound(bound, admin.get(), sender);
    }

    private record Bound(JobsServices services, JobsKVStoreAdmin admin, CommandSender sender) {}

    private record KindCount(String kind, long count) {}

    private record StatsSnapshot(String backend, long size, long maxEntries, List<KindCount> counts) {}
}
