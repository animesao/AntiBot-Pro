package com.antibot.velocity.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Управление базой данных SQLite для хранения истории и статистики
 */
public class DatabaseManager {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private final Path dataDirectory;
    private Connection connection;
    private final ExecutorService executor;

    public DatabaseManager(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AntiBot-Database");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Инициализация базы данных
     */
    public void initialize() {
        try {
            Class.forName("org.sqlite.JDBC");
            String url = "jdbc:sqlite:" + dataDirectory.resolve("antibot.db").toString();
            connection = DriverManager.getConnection(url);
            
            createTables();
            logger.info("База данных инициализирована");
        } catch (Exception e) {
            logger.error("Ошибка инициализации базы данных", e);
        }
    }

    /**
     * Создание таблиц
     */
    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Таблица подключений
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS connections (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_name TEXT NOT NULL,
                    ip_address TEXT NOT NULL,
                    country TEXT,
                    client_brand TEXT,
                    timestamp INTEGER NOT NULL,
                    blocked BOOLEAN DEFAULT 0,
                    reason TEXT
                )
            """);

            // Таблица обнаружений
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS detections (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    detection_type TEXT NOT NULL,
                    player_name TEXT NOT NULL,
                    ip_address TEXT NOT NULL,
                    details TEXT,
                    risk_score INTEGER,
                    timestamp INTEGER NOT NULL
                )
            """);

            // Таблица блокировок
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS blocks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ip_address TEXT NOT NULL UNIQUE,
                    reason TEXT NOT NULL,
                    blocked_at INTEGER NOT NULL,
                    expires_at INTEGER NOT NULL,
                    unblocked BOOLEAN DEFAULT 0
                )
            """);

            // Индексы для производительности
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_connections_ip ON connections(ip_address)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_connections_player ON connections(player_name)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_connections_timestamp ON connections(timestamp)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_detections_type ON detections(detection_type)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_blocks_ip ON blocks(ip_address)");
        }
    }

    /**
     * Записывает подключение игрока
     */
    public CompletableFuture<Void> logConnection(String playerName, String ip, String country, 
                                                   String clientBrand, boolean blocked, String reason) {
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO connections (player_name, ip_address, country, client_brand, timestamp, blocked, reason) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)"
            )) {
                stmt.setString(1, playerName);
                stmt.setString(2, ip);
                stmt.setString(3, country);
                stmt.setString(4, clientBrand);
                stmt.setLong(5, System.currentTimeMillis());
                stmt.setBoolean(6, blocked);
                stmt.setString(7, reason);
                stmt.executeUpdate();
            } catch (SQLException e) {
                logger.error("Ошибка записи подключения", e);
            }
        }, executor);
    }

    /**
     * Записывает обнаружение
     */
    public CompletableFuture<Void> logDetection(String type, String playerName, String ip, 
                                                  String details, int riskScore) {
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO detections (detection_type, player_name, ip_address, details, risk_score, timestamp) " +
                "VALUES (?, ?, ?, ?, ?, ?)"
            )) {
                stmt.setString(1, type);
                stmt.setString(2, playerName);
                stmt.setString(3, ip);
                stmt.setString(4, details);
                stmt.setInt(5, riskScore);
                stmt.setLong(6, System.currentTimeMillis());
                stmt.executeUpdate();
            } catch (SQLException e) {
                logger.error("Ошибка записи обнаружения", e);
            }
        }, executor);
    }

    /**
     * Записывает блокировку IP
     */
    public CompletableFuture<Void> logBlock(String ip, String reason, long expiresAt) {
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT OR REPLACE INTO blocks (ip_address, reason, blocked_at, expires_at, unblocked) " +
                "VALUES (?, ?, ?, ?, 0)"
            )) {
                stmt.setString(1, ip);
                stmt.setString(2, reason);
                stmt.setLong(3, System.currentTimeMillis());
                stmt.setLong(4, expiresAt);
                stmt.executeUpdate();
            } catch (SQLException e) {
                logger.error("Ошибка записи блокировки", e);
            }
        }, executor);
    }

    /**
     * Получает статистику по игроку
     */
    public CompletableFuture<PlayerStats> getPlayerStats(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT COUNT(*) as total, " +
                "SUM(CASE WHEN blocked = 1 THEN 1 ELSE 0 END) as blocked_count, " +
                "MIN(timestamp) as first_seen, " +
                "MAX(timestamp) as last_seen " +
                "FROM connections WHERE player_name = ?"
            )) {
                stmt.setString(1, playerName);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    return new PlayerStats(
                        rs.getInt("total"),
                        rs.getInt("blocked_count"),
                        rs.getLong("first_seen"),
                        rs.getLong("last_seen")
                    );
                }
            } catch (SQLException e) {
                logger.error("Ошибка получения статистики игрока", e);
            }
            return new PlayerStats(0, 0, 0, 0);
        }, executor);
    }

    /**
     * Получает последние подключения
     */
    public CompletableFuture<List<ConnectionRecord>> getRecentConnections(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<ConnectionRecord> records = new ArrayList<>();
            try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT * FROM connections ORDER BY timestamp DESC LIMIT ?"
            )) {
                stmt.setInt(1, limit);
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    records.add(new ConnectionRecord(
                        rs.getString("player_name"),
                        rs.getString("ip_address"),
                        rs.getString("country"),
                        rs.getString("client_brand"),
                        rs.getLong("timestamp"),
                        rs.getBoolean("blocked"),
                        rs.getString("reason")
                    ));
                }
            } catch (SQLException e) {
                logger.error("Ошибка получения последних подключений", e);
            }
            return records;
        }, executor);
    }

    /**
     * Очистка старых записей
     */
    public CompletableFuture<Void> cleanupOldRecords(int daysToKeep) {
        return CompletableFuture.runAsync(() -> {
            long cutoff = System.currentTimeMillis() - (daysToKeep * 24L * 60 * 60 * 1000);
            try (PreparedStatement stmt = connection.prepareStatement(
                "DELETE FROM connections WHERE timestamp < ?"
            )) {
                stmt.setLong(1, cutoff);
                int deleted = stmt.executeUpdate();
                logger.info("Удалено {} старых записей подключений", deleted);
            } catch (SQLException e) {
                logger.error("Ошибка очистки старых записей", e);
            }
        }, executor);
    }

    /**
     * Закрытие базы данных
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
            logger.info("База данных закрыта");
        } catch (Exception e) {
            logger.error("Ошибка закрытия базы данных", e);
        }
    }

    // Вспомогательные классы
    public static class PlayerStats {
        public final int totalConnections;
        public final int blockedConnections;
        public final long firstSeen;
        public final long lastSeen;

        public PlayerStats(int total, int blocked, long firstSeen, long lastSeen) {
            this.totalConnections = total;
            this.blockedConnections = blocked;
            this.firstSeen = firstSeen;
            this.lastSeen = lastSeen;
        }
    }

    public static class ConnectionRecord {
        public final String playerName;
        public final String ipAddress;
        public final String country;
        public final String clientBrand;
        public final long timestamp;
        public final boolean blocked;
        public final String reason;

        public ConnectionRecord(String playerName, String ipAddress, String country, 
                               String clientBrand, long timestamp, boolean blocked, String reason) {
            this.playerName = playerName;
            this.ipAddress = ipAddress;
            this.country = country;
            this.clientBrand = clientBrand;
            this.timestamp = timestamp;
            this.blocked = blocked;
            this.reason = reason;
        }
    }
}
