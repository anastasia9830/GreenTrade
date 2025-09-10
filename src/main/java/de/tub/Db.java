package de.tub;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;

public final class Db {
    private Db() {}

    public static DataSource dataSourceFromEnv() {
        String url  = getenvOr("DB_URL",  "jdbc:postgresql://localhost:5432/market");
        String user = getenvOr("DB_USER", "market");
        String pass = getenvOr("DB_PASS", "market");

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        cfg.setUsername(user);
        cfg.setPassword(pass);
        cfg.setMaximumPoolSize(5);
        cfg.setMinimumIdle(1);
        cfg.setAutoCommit(true);
        return new HikariDataSource(cfg);
        }

    public static void migrate(DataSource ds) {
        Flyway.configure()
        .dataSource(ds)
        .locations("classpath:db/migration")
        .validateMigrationNaming(true) // чтобы сразу увидеть, какой файл назван неверно
        .load()
        .migrate();
    }

    private static String getenvOr(String k, String def) {
        String v = System.getenv(k);
        return (v == null || v.isBlank()) ? def : v;
    }
}
