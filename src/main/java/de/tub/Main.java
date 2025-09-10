package de.tub;

import lombok.extern.java.Log;

import javax.sql.DataSource;

@Log
public class Main {
    public static void main(String[] args) {
        Market market;

        String dbUrl = System.getenv("DB_URL"); // e.g. jdbc:postgresql://localhost:5432/market
        if (dbUrl != null && !dbUrl.isBlank()) {
            try {
                // Init DataSource, run Flyway migrations, and use the JDBC repository
                DataSource ds = Db.dataSourceFromEnv();
                Db.migrate(ds);

                JdbcMarketRepository repo = new JdbcMarketRepository(ds);
                market = new Market(repo);

                log.info("Running with PostgreSQL: " + dbUrl);
            } catch (Exception e) {
                log.severe("Failed to initialize DB. Falling back to in-memory. Reason: " + e.getMessage());
                market = new Market();
            }
        } else {
            log.info("DB_URL is not set. Running in in-memory mode.");
            market = new Market();
        }

        // Start console UI
        Console console = new Console(market, new java.util.Scanner(System.in));
        console.start();
    }
}
