package com.mycraftdailyshop;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqliteDriverTest {
    @Test void supportsSchemaAndUpsertStatements() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite::memory:");
        config.setMaximumPoolSize(1);
        try (HikariDataSource source = new HikariDataSource(config); Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE player_identity(player_uuid VARCHAR(36) PRIMARY KEY, player_name VARCHAR(32), last_seen BIGINT)");
            statement.executeUpdate("INSERT INTO player_identity(player_uuid,player_name,last_seen) VALUES('id','first',1)");
            statement.executeUpdate("UPDATE player_identity SET player_name='second',last_seen=2 WHERE player_uuid='id'");
            try (ResultSet result = statement.executeQuery("SELECT player_name FROM player_identity WHERE player_uuid='id'")) {
                result.next();
                assertEquals("second", result.getString(1));
            }
        }
    }
}
