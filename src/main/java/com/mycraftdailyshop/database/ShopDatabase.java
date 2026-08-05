package com.mycraftdailyshop.database;

import com.mycraftdailyshop.model.Offer;
import com.mycraftdailyshop.model.ShopSnapshot;
import com.mycraftdailyshop.model.EnchantmentRoll;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ShopDatabase implements AutoCloseable {
    private final JavaPlugin plugin;
    private HikariDataSource dataSource;
    private boolean mysql;

    public ShopDatabase(JavaPlugin plugin) { this.plugin = plugin; }

    public void open() throws SQLException {
        FileConfiguration config = plugin.getConfig();
        mysql = config.getString("database.type", "SQLite").equalsIgnoreCase("MySQL");
        HikariConfig hikari = new HikariConfig();
        if (mysql) {
            String root = "database.MySQL.";
            hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
            String url = "jdbc:mysql://" + config.getString(root + "host") + ":" + config.getInt(root + "port", 3306)
                    + "/" + config.getString(root + "database") + "?useSSL=" + config.getBoolean(root + "use-ssl", false)
                    + "&characterEncoding=utf8&serverTimezone=UTC";
            hikari.setJdbcUrl(url);
            hikari.setUsername(config.getString(root + "username"));
            hikari.setPassword(config.getString(root + "password"));
            hikari.setMaximumPoolSize(config.getInt(root + "maximum-pool-size", 10));
            hikari.setMinimumIdle(config.getInt(root + "minimum-idle", 2));
            hikari.setConnectionTimeout(config.getLong(root + "connection-timeout", 5000));
        } else {
            File file = new File(plugin.getDataFolder(), config.getString("database.SQLite.file", "database.db"));
            hikari.setDriverClassName("org.sqlite.JDBC");
            hikari.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
            hikari.setMaximumPoolSize(1);
            hikari.setConnectionInitSql("PRAGMA foreign_keys=ON");
        }
        hikari.setPoolName("MyCraftDailyShop-Pool");
        dataSource = new HikariDataSource(hikari);
        createTables();
    }

    private void createTables() throws SQLException {
        String[] statements = {
                "CREATE TABLE IF NOT EXISTS shop_cycle (cycle_id VARCHAR(36) PRIMARY KEY, shop_id VARCHAR(128) NOT NULL, scope_id VARCHAR(36) NOT NULL, cycle_key VARCHAR(32) NOT NULL, generated_at BIGINT NOT NULL, expires_at BIGINT NOT NULL, UNIQUE(shop_id, scope_id, cycle_key))",
                "CREATE TABLE IF NOT EXISTS shop_offer (cycle_id VARCHAR(36) NOT NULL, offer_index INTEGER NOT NULL, provider VARCHAR(64) NOT NULL, item_id VARCHAR(255) NOT NULL, money DECIMAL(20,2) NOT NULL, total_money DECIMAL(20,2) NOT NULL, amount INTEGER NOT NULL, personal_limit INTEGER NOT NULL, server_limit INTEGER NOT NULL, chance DOUBLE NOT NULL, base_money DECIMAL(20,2), premium DECIMAL(20,8), enchantments TEXT, PRIMARY KEY(cycle_id, offer_index))",
                "CREATE TABLE IF NOT EXISTS shop_usage (cycle_id VARCHAR(36) NOT NULL, offer_index INTEGER NOT NULL, player_uuid VARCHAR(36) NOT NULL, used INTEGER NOT NULL, PRIMARY KEY(cycle_id, offer_index, player_uuid))",
                "CREATE TABLE IF NOT EXISTS transaction_history (transaction_id VARCHAR(36) PRIMARY KEY, player_uuid VARCHAR(36) NOT NULL, player_name VARCHAR(32) NOT NULL, shop_id VARCHAR(128) NOT NULL, cycle_id VARCHAR(36) NOT NULL, offer_index INTEGER NOT NULL, trade_type VARCHAR(8) NOT NULL, provider VARCHAR(64) NOT NULL, item_id VARCHAR(255) NOT NULL, amount INTEGER NOT NULL, unit_money DECIMAL(20,2) NOT NULL, total_money DECIMAL(20,2) NOT NULL, traded_at BIGINT NOT NULL)",
                "CREATE TABLE IF NOT EXISTS player_identity (player_uuid VARCHAR(36) PRIMARY KEY, player_name VARCHAR(32) NOT NULL, last_seen BIGINT NOT NULL)"
        };
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String sql : statements) statement.executeUpdate(sql);
            addColumn(statement, "base_money DECIMAL(20,2)");
            addColumn(statement, "premium DECIMAL(20,8)");
            addColumn(statement, "enchantments TEXT");
        }
    }

    private void addColumn(Statement statement, String definition) {
        try { statement.executeUpdate("ALTER TABLE shop_offer ADD COLUMN " + definition); } catch (SQLException ignored) { }
    }

    public ShopSnapshot findSnapshot(String shopId, String scopeId, String cycleKey) throws SQLException {
        String sql = "SELECT cycle_id, expires_at FROM shop_cycle WHERE shop_id=? AND scope_id=? AND cycle_key=?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, shopId); ps.setString(2, scopeId); ps.setString(3, cycleKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                String id = rs.getString(1);
                return new ShopSnapshot(id, cycleKey, rs.getLong(2), loadOffers(connection, id));
            }
        }
    }

    public ShopSnapshot createSnapshot(String shopId, String scopeId, String cycleKey, long expiresAt, List<Offer> generated) throws SQLException {
        String cycleId = UUID.randomUUID().toString();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement ps = connection.prepareStatement("INSERT INTO shop_cycle(cycle_id,shop_id,scope_id,cycle_key,generated_at,expires_at) VALUES(?,?,?,?,?,?)")) {
                    ps.setString(1, cycleId); ps.setString(2, shopId); ps.setString(3, scopeId); ps.setString(4, cycleKey);
                    ps.setLong(5, System.currentTimeMillis()); ps.setLong(6, expiresAt); ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement("INSERT INTO shop_offer(cycle_id,offer_index,provider,item_id,money,total_money,amount,personal_limit,server_limit,chance,base_money,premium,enchantments) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                    for (Offer offer : generated) {
                        ps.setString(1, cycleId); ps.setInt(2, offer.getIndex()); ps.setString(3, offer.getProvider()); ps.setString(4, offer.getItemId());
                        ps.setBigDecimal(5, offer.getMoney()); ps.setBigDecimal(6, offer.getTotalMoney()); ps.setInt(7, offer.getAmount());
                        ps.setInt(8, offer.getPersonalLimit()); ps.setInt(9, offer.getServerLimit()); ps.setDouble(10, offer.getChance()); ps.setBigDecimal(11, offer.getBaseMoney()); ps.setBigDecimal(12, offer.getPremium()); ps.setString(13, serialize(offer.getEnchantments())); ps.addBatch();
                    }
                    ps.executeBatch();
                }
                connection.commit();
                List<Offer> offers = new ArrayList<>();
                for (Offer offer : generated) offers.add(new Offer(cycleId, offer.getIndex(), offer.getProvider(), offer.getItemId(), offer.getMoney(), offer.getTotalMoney(), offer.getAmount(), offer.getPersonalLimit(), offer.getServerLimit(), offer.getChance(), offer.getBaseMoney(), offer.getPremium(), offer.getEnchantments()));
                return new ShopSnapshot(cycleId, cycleKey, expiresAt, offers);
            } catch (SQLException ex) {
                connection.rollback();
                ShopSnapshot existing = findSnapshot(shopId, scopeId, cycleKey);
                if (existing != null) return existing;
                throw ex;
            } finally { connection.setAutoCommit(true); }
        }
    }

    private List<Offer> loadOffers(Connection connection, String cycleId) throws SQLException {
        List<Offer> offers = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT offer_index,provider,item_id,money,total_money,amount,personal_limit,server_limit,chance,base_money,premium,enchantments FROM shop_offer WHERE cycle_id=? ORDER BY offer_index")) {
            ps.setString(1, cycleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) { BigDecimal money = rs.getBigDecimal(4); BigDecimal base = rs.getBigDecimal(10); offers.add(new Offer(cycleId, rs.getInt(1), rs.getString(2), rs.getString(3), money, rs.getBigDecimal(5), rs.getInt(6), rs.getInt(7), rs.getInt(8), rs.getDouble(9), base == null ? money : base, rs.getBigDecimal(11) == null ? BigDecimal.ZERO : rs.getBigDecimal(11), deserialize(rs.getString(12)))); }
            }
        }
        return offers;
    }

    private String serialize(List<EnchantmentRoll> rolls) { StringBuilder result = new StringBuilder(); for (EnchantmentRoll roll : rolls) { if (result.length() > 0) result.append(';'); result.append(roll.getId()).append(':').append(roll.getLevel()).append(':').append(roll.getPremium().toPlainString()); } return result.toString(); }
    private List<EnchantmentRoll> deserialize(String value) { List<EnchantmentRoll> result = new ArrayList<>(); if (value == null || value.isEmpty()) return result; for (String entry : value.split(";")) { String[] parts = entry.split(":", 3); if (parts.length == 3) try { result.add(new EnchantmentRoll(parts[0], Integer.parseInt(parts[1]), new BigDecimal(parts[2]))); } catch (RuntimeException ignored) { } } return result; }

    public UsageResult reserve(Offer offer, String playerUuid) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement check = connection.prepareStatement("SELECT 1 FROM shop_offer WHERE cycle_id=? AND offer_index=?")) {
                    check.setString(1, offer.getCycleId());
                    check.setInt(2, offer.getIndex());
                    try (ResultSet rs = check.executeQuery()) {
                        if (!rs.next()) {
                            connection.rollback();
                            return new UsageResult(UsageResult.Status.STALE, 0, 0);
                        }
                    }
                }
                boolean trackPersonal = offer.getPersonalLimit() > 0;
                boolean trackServer = offer.getServerLimit() > 0;
                if (trackPersonal) ensureUsage(connection, offer.getCycleId(), offer.getIndex(), playerUuid);
                if (trackServer) ensureUsage(connection, offer.getCycleId(), offer.getIndex(), "*");
                int personal = trackPersonal ? usage(connection, offer.getCycleId(), offer.getIndex(), playerUuid, true) : 0;
                int server = trackServer ? usage(connection, offer.getCycleId(), offer.getIndex(), "*", true) : 0;
                if (offer.getServerLimit() >= 0 && server >= offer.getServerLimit()) { connection.rollback(); return new UsageResult(UsageResult.Status.SERVER_LIMIT, personal, server); }
                if (offer.getPersonalLimit() >= 0 && personal >= offer.getPersonalLimit()) { connection.rollback(); return new UsageResult(UsageResult.Status.PERSONAL_LIMIT, personal, server); }
                if (trackPersonal) updateUsage(connection, offer.getCycleId(), offer.getIndex(), playerUuid, 1);
                if (trackServer) updateUsage(connection, offer.getCycleId(), offer.getIndex(), "*", 1);
                connection.commit();
                return new UsageResult(UsageResult.Status.SUCCESS, personal + 1, server + 1);
            } catch (SQLException ex) { connection.rollback(); throw ex; }
            finally { connection.setAutoCommit(true); }
        }
    }

    private void ensureUsage(Connection connection, String cycle, int offer, String player) throws SQLException {
        String sql = mysql ? "INSERT IGNORE INTO shop_usage(cycle_id,offer_index,player_uuid,used) VALUES(?,?,?,0)" : "INSERT OR IGNORE INTO shop_usage(cycle_id,offer_index,player_uuid,used) VALUES(?,?,?,0)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) { ps.setString(1, cycle); ps.setInt(2, offer); ps.setString(3, player); ps.executeUpdate(); }
    }

    private int usage(Connection connection, String cycle, int offer, String player, boolean lock) throws SQLException {
        String sql = "SELECT used FROM shop_usage WHERE cycle_id=? AND offer_index=? AND player_uuid=?" + (mysql && lock ? " FOR UPDATE" : "");
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, cycle); ps.setInt(2, offer); ps.setString(3, player);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        }
    }

    private void updateUsage(Connection connection, String cycle, int offer, String player, int delta) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE shop_usage SET used=CASE WHEN used + ? < 0 THEN 0 ELSE used + ? END WHERE cycle_id=? AND offer_index=? AND player_uuid=?")) {
            ps.setInt(1, delta); ps.setInt(2, delta); ps.setString(3, cycle); ps.setInt(4, offer); ps.setString(5, player); ps.executeUpdate();
        }
    }

    public void release(Offer offer, String playerUuid) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            if (offer.getPersonalLimit() > 0) updateUsage(connection, offer.getCycleId(), offer.getIndex(), playerUuid, -1);
            if (offer.getServerLimit() > 0) updateUsage(connection, offer.getCycleId(), offer.getIndex(), "*", -1);
            connection.commit();
        }
    }

    public Map<Integer, int[]> getUsage(String cycleId, String playerUuid) throws SQLException {
        Map<Integer, int[]> result = new HashMap<>();
        String sql = "SELECT offer_index,player_uuid,used FROM shop_usage WHERE cycle_id=? AND (player_uuid=? OR player_uuid='*')";
        try (Connection connection = dataSource.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, cycleId);
            ps.setString(2, playerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int offerIndex = rs.getInt(1);
                    int[] usage = result.computeIfAbsent(offerIndex, ignored -> new int[]{0, 0});
                    if ("*".equals(rs.getString(2))) usage[1] = rs.getInt(3);
                    else usage[0] = rs.getInt(3);
                }
            }
        }
        return result;
    }

    public void record(String playerUuid, String playerName, String shopId, String type, Offer offer) throws SQLException {
        String sql = "INSERT INTO transaction_history(transaction_id,player_uuid,player_name,shop_id,cycle_id,offer_index,trade_type,provider,item_id,amount,unit_money,total_money,traded_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString()); ps.setString(2, playerUuid); ps.setString(3, playerName); ps.setString(4, shopId);
            ps.setString(5, offer.getCycleId()); ps.setInt(6, offer.getIndex()); ps.setString(7, type); ps.setString(8, offer.getProvider());
            ps.setString(9, offer.getItemId()); ps.setInt(10, offer.getAmount()); ps.setBigDecimal(11, offer.getMoney()); ps.setBigDecimal(12, offer.getTotalMoney());
            ps.setLong(13, System.currentTimeMillis()); ps.executeUpdate();
        }
    }

    public synchronized void rememberPlayer(String uuid, String name) throws SQLException {
        long now = System.currentTimeMillis();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement update = c.prepareStatement("UPDATE player_identity SET player_name=?,last_seen=? WHERE player_uuid=?")) {
                update.setString(1, name); update.setLong(2, now); update.setString(3, uuid);
                if (update.executeUpdate() == 0) {
                    try (PreparedStatement insert = c.prepareStatement("INSERT INTO player_identity(player_uuid,player_name,last_seen) VALUES(?,?,?)")) {
                        insert.setString(1, uuid); insert.setString(2, name); insert.setLong(3, now);
                        try { insert.executeUpdate(); }
                        catch (SQLException duplicate) {
                            // Another MySQL server may have inserted the UUID between UPDATE and INSERT.
                            try (PreparedStatement retry = c.prepareStatement("UPDATE player_identity SET player_name=?,last_seen=? WHERE player_uuid=?")) {
                                retry.setString(1, name); retry.setLong(2, now); retry.setString(3, uuid); retry.executeUpdate();
                            }
                        }
                    }
                }
            }
            c.commit();
        }
    }

    public String findPlayerUuid(String nameOrUuid) throws SQLException {
        try { return UUID.fromString(nameOrUuid).toString(); } catch (IllegalArgumentException ignored) { }
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT player_uuid FROM player_identity WHERE LOWER(player_name)=LOWER(?) ORDER BY last_seen DESC")) {
            ps.setString(1, nameOrUuid); try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString(1) : null; }
        }
    }

    public void resetPlayerUsage(String playerUuid, String shopId) throws SQLException {
        String sql = "DELETE FROM shop_usage WHERE player_uuid<> '*'" + (playerUuid.equals("*") ? "" : " AND player_uuid=?") + (shopId.equals("*") ? "" : " AND cycle_id IN (SELECT cycle_id FROM shop_cycle WHERE shop_id=?)");
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1; if (!playerUuid.equals("*")) ps.setString(i++, playerUuid); if (!shopId.equals("*")) ps.setString(i, shopId); ps.executeUpdate();
        }
    }

    public void resetServerUsage(String shopId) throws SQLException {
        String sql = "DELETE FROM shop_usage WHERE player_uuid='*'" + (shopId.equals("*") ? "" : " AND cycle_id IN (SELECT cycle_id FROM shop_cycle WHERE shop_id=?)");
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) { if (!shopId.equals("*")) ps.setString(1, shopId); ps.executeUpdate(); }
    }

    public void invalidate(String shopId, String scopeId) throws SQLException {
        String where = (shopId.equals("*") ? "1=1" : "shop_id=?") + (scopeId == null ? "" : " AND scope_id=?");
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            List<String> cycles = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT cycle_id FROM shop_cycle WHERE " + where)) {
                int i=1; if (!shopId.equals("*")) ps.setString(i++, shopId); if (scopeId != null) ps.setString(i, scopeId);
                try (ResultSet rs=ps.executeQuery()) { while(rs.next()) cycles.add(rs.getString(1)); }
            }
            for (String cycle : cycles) {
                try (PreparedStatement ps=c.prepareStatement("DELETE FROM shop_usage WHERE cycle_id=?")) { ps.setString(1,cycle); ps.executeUpdate(); }
                try (PreparedStatement ps=c.prepareStatement("DELETE FROM shop_offer WHERE cycle_id=?")) { ps.setString(1,cycle); ps.executeUpdate(); }
                try (PreparedStatement ps=c.prepareStatement("DELETE FROM shop_cycle WHERE cycle_id=?")) { ps.setString(1,cycle); ps.executeUpdate(); }
            }
            c.commit();
        }
    }

    public void cleanup(int cycleDays, int historyDays) throws SQLException {
        long now = System.currentTimeMillis();
        if (cycleDays >= 0) {
            long cutoff = now - cycleDays * 86400000L;
            try (Connection c=dataSource.getConnection(); PreparedStatement ps=c.prepareStatement("SELECT cycle_id FROM shop_cycle WHERE expires_at<?")) {
                ps.setLong(1,cutoff); List<String> ids=new ArrayList<>(); try(ResultSet rs=ps.executeQuery()){while(rs.next())ids.add(rs.getString(1));}
                for(String id:ids){ try(PreparedStatement d=c.prepareStatement("DELETE FROM shop_usage WHERE cycle_id=?")){d.setString(1,id);d.executeUpdate();} try(PreparedStatement d=c.prepareStatement("DELETE FROM shop_offer WHERE cycle_id=?")){d.setString(1,id);d.executeUpdate();} try(PreparedStatement d=c.prepareStatement("DELETE FROM shop_cycle WHERE cycle_id=?")){d.setString(1,id);d.executeUpdate();} }
            }
        }
        if (historyDays >= 0) try(Connection c=dataSource.getConnection(); PreparedStatement ps=c.prepareStatement("DELETE FROM transaction_history WHERE traded_at<?")){ps.setLong(1,now-historyDays*86400000L);ps.executeUpdate();}
    }

    /** Removes wildcard counters belonging to offers without a finite server quota. */
    public void cleanupUnusedServerUsage() throws SQLException {
        try (Connection connection = dataSource.getConnection(); PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM shop_usage WHERE player_uuid='*' AND NOT EXISTS (SELECT 1 FROM shop_offer WHERE shop_offer.cycle_id=shop_usage.cycle_id AND shop_offer.offer_index=shop_usage.offer_index AND shop_offer.server_limit > 0)")) {
            ps.executeUpdate();
        }
    }

    @Override public void close() { if (dataSource != null) dataSource.close(); }
}
