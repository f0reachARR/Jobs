package me.f0reach.jobs.persistence.mysql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.f0reach.jobs.config.PluginConfig;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * HikariCP のセットアップと管理を担当する。
 * spec/05-persistence.md 「接続管理」を参照。
 */
public final class MySqlDataSource implements AutoCloseable {

    private final HikariDataSource dataSource;

    public MySqlDataSource(PluginConfig.PersistenceConfig config) {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(buildUrl(config));
        hikari.setUsername(config.user());
        hikari.setPassword(config.password());
        hikari.setMaximumPoolSize(config.poolSize());
        hikari.setPoolName("JobsHikariPool");
        hikari.setConnectionTestQuery("SELECT 1");
        this.dataSource = new HikariDataSource(hikari);
    }

    private static String buildUrl(PluginConfig.PersistenceConfig config) {
        // MySQL 8 系のデフォルト認証 (caching_sha2_password) は SSL または公開鍵取得を要求する。
        // プラグインが MC サーバと同一ホスト / VLAN 内で稼働する典型ケースを想定し、
        // allowPublicKeyRetrieval=true を付ける。TLS を使いたい環境では useSSL を上書きする余地を残す。
        return "jdbc:mysql://" + config.host() + ":" + config.port() + "/" + config.database()
                + "?useSSL=false&allowPublicKeyRetrieval=true"
                + "&serverTimezone=UTC&useUnicode=true&characterEncoding=utf8";
    }

    public DataSource dataSource() {
        return dataSource;
    }

    /** SELECT 1 を実行し、接続が使えるか確認する。 */
    public void healthCheck() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT 1");
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
