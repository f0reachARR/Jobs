package me.f0reach.jobs.persistence.mysql;

import me.f0reach.jobs.api.query.ActionFilter;
import me.f0reach.jobs.api.query.TimeRange;
import me.f0reach.jobs.config.PluginConfig;
import me.f0reach.jobs.persistence.dto.Actor;
import me.f0reach.jobs.persistence.dto.ActionLogRow;
import me.f0reach.jobs.persistence.dto.DailyRewardDelta;
import me.f0reach.jobs.persistence.dto.PlayerJobHistoryRow;
import me.f0reach.jobs.persistence.dto.PlayerJobRow;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MySQL 実装の統合テスト。実際の MySQL サーバに対して DDL 発行と CRUD を試す。
 *
 * <p>環境変数 {@code JOBS_TEST_MYSQL_HOST} が設定されているときだけ走る。
 * 実行例:
 * <pre>
 *   JOBS_TEST_MYSQL_HOST=localhost JOBS_TEST_MYSQL_PORT=33306 \
 *   JOBS_TEST_MYSQL_USER=root JOBS_TEST_MYSQL_PASSWORD=root \
 *   JOBS_TEST_MYSQL_DATABASE=jobs \
 *   ./gradlew test --tests MySqlPersistenceIntegrationTest
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "JOBS_TEST_MYSQL_HOST", matches = ".+")
class MySqlPersistenceIntegrationTest {

    private static MySqlDataSource dataSource;
    private static MySqlPlayerJobRepository playerJobRepository;
    private static MySqlPlayerJobHistoryRepository playerJobHistoryRepository;
    private static MySqlActionLogRepository actionLogRepository;
    private static MySqlDailyRewardTotalRepository dailyRewardTotalRepository;

    @BeforeAll
    static void setUp() throws Exception {
        PluginConfig.PersistenceConfig config = new PluginConfig.PersistenceConfig(
                PluginConfig.PersistenceConfig.Type.MYSQL,
                env("JOBS_TEST_MYSQL_HOST", "localhost"),
                Integer.parseInt(env("JOBS_TEST_MYSQL_PORT", "3306")),
                env("JOBS_TEST_MYSQL_DATABASE", "jobs"),
                env("JOBS_TEST_MYSQL_USER", "root"),
                env("JOBS_TEST_MYSQL_PASSWORD", "root"),
                4,
                30
        );
        dataSource = new MySqlDataSource(config);
        dataSource.healthCheck();
        // 各テストで一貫した状態にするため、全テーブルを DROP してから DDL を流す。
        try (Connection conn = dataSource.dataSource().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS action_log");
            stmt.execute("DROP TABLE IF EXISTS player_job");
            stmt.execute("DROP TABLE IF EXISTS player_job_history");
            stmt.execute("DROP TABLE IF EXISTS daily_reward_total");
        }
        new SchemaInitializer(dataSource.dataSource()).initialize();

        playerJobRepository = new MySqlPlayerJobRepository(dataSource.dataSource());
        playerJobHistoryRepository = new MySqlPlayerJobHistoryRepository(dataSource.dataSource());
        actionLogRepository = new MySqlActionLogRepository(dataSource.dataSource());
        dailyRewardTotalRepository = new MySqlDailyRewardTotalRepository(dataSource.dataSource());
    }

    @AfterAll
    static void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    @Test
    void schemaInitializerCreatesAllTables() throws SQLException {
        try (Connection conn = dataSource.dataSource().getConnection();
             Statement stmt = conn.createStatement()) {
            for (String table : List.of(
                    "player_job", "player_job_history", "action_log", "daily_reward_total")) {
                var rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table);
                assertTrue(rs.next());
                assertNotNull(rs.getObject(1));
            }
        }
    }

    @Test
    void playerJobUpsertReplacesCurrentRow() {
        UUID player = UUID.randomUUID();
        Instant t1 = Instant.now();
        playerJobRepository.upsert(player, "combat", t1);

        Optional<PlayerJobRow> current = playerJobRepository.find(player);
        assertTrue(current.isPresent());
        assertEquals("combat", current.get().jobId());
        assertEquals(player, current.get().playerUuid());

        // 2 回目は同 player の row を上書き
        Instant t2 = t1.plusSeconds(60);
        playerJobRepository.upsert(player, "mining", t2);

        Optional<PlayerJobRow> after = playerJobRepository.find(player);
        assertTrue(after.isPresent());
        assertEquals("mining", after.get().jobId());
        assertEquals(t2.toEpochMilli(), after.get().cooldownBaseAt().toEpochMilli());
    }

    @Test
    void playerJobResetCooldownBase() {
        UUID player = UUID.randomUUID();
        playerJobRepository.upsert(player, "combat", Instant.now());
        playerJobRepository.resetCooldownBase(player);

        PlayerJobRow after = playerJobRepository.find(player).orElseThrow();
        assertEquals("combat", after.jobId());
        assertEquals(Instant.EPOCH.toEpochMilli(), after.cooldownBaseAt().toEpochMilli());
    }

    @Test
    void playerJobHistoryAppendAndRecent() {
        UUID player = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        Instant t1 = Instant.now().minusSeconds(60);
        Instant t2 = Instant.now();

        playerJobHistoryRepository.append(player, "combat", null, t1, Actor.PLAYER, null);
        playerJobHistoryRepository.append(player, "mining", "combat", t2, Actor.ADMIN, admin);

        List<PlayerJobHistoryRow> recent = playerJobHistoryRepository.recent(player, 10);
        assertEquals(2, recent.size());
        assertEquals("mining", recent.get(0).jobId());
        assertEquals(Actor.ADMIN, recent.get(0).actor());
        assertEquals(admin, recent.get(0).actorUuid());
        assertEquals("combat", recent.get(0).previousJobId());
        assertEquals("combat", recent.get(1).jobId());
        assertEquals(Actor.PLAYER, recent.get(1).actor());

        Optional<Instant> first = playerJobHistoryRepository.firstSelectedAt(player);
        assertTrue(first.isPresent());
        assertEquals(t1.toEpochMilli(), first.get().toEpochMilli());
    }

    @Test
    void actionLogBatchInsertAndAggregates() {
        UUID player = UUID.randomUUID();
        Instant base = Instant.now().minusSeconds(10);
        ActionLogRow r1 = new ActionLogRow(player, "combat", "kill:minecraft:zombie", 5.0, 5.0, false, 1, base);
        ActionLogRow r2 = new ActionLogRow(player, "combat", "kill:minecraft:zombie", 5.0, 4.0, false, 1, base.plusSeconds(1));
        ActionLogRow r3 = new ActionLogRow(player, "combat", "kill:minecraft:blaze", 20.0, 20.0, false, 1, base.plusSeconds(2));
        actionLogRepository.insertBatch(List.of(r1, r2, r3));

        TimeRange range = new TimeRange(base.minusSeconds(1), base.plusSeconds(60));
        assertEquals(3, actionLogRepository.countActions(player, ActionFilter.none(), range));
        assertEquals(5.0 + 4.0 + 20.0, actionLogRepository.sumReward(player, ActionFilter.none(), range));
        var keys = actionLogRepository.distinctKeys(player, ActionFilter.none(), range);
        assertEquals(2, keys.size());
        assertTrue(keys.contains("kill:minecraft:zombie"));
        assertTrue(keys.contains("kill:minecraft:blaze"));

        List<String> recent = actionLogRepository.recentKeys(player, "combat", 10);
        assertEquals(3, recent.size());
        // 最新順（blaze -> zombie -> zombie）
        assertEquals("kill:minecraft:blaze", recent.get(0));

        // filter: prefix
        long zombieCount = actionLogRepository.countActions(
                player,
                new ActionFilter("combat", null, "kill:minecraft:zombie"),
                range
        );
        assertEquals(2, zombieCount);
    }

    @Test
    void dailyRewardTotalUpsert() {
        UUID player = UUID.randomUUID();
        LocalDate today = LocalDate.now();

        assertEquals(0.0, dailyRewardTotalRepository.getTotal(player, today));

        dailyRewardTotalRepository.addBatch(List.of(
                new DailyRewardDelta(player, today, 100.0),
                new DailyRewardDelta(player, today, 50.0)
        ));
        assertEquals(150.0, dailyRewardTotalRepository.getTotal(player, today));

        // 別プレイヤーは影響を受けない
        UUID other = UUID.randomUUID();
        assertEquals(0.0, dailyRewardTotalRepository.getTotal(other, today));
    }
}
