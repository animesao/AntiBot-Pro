package com.antibot.velocity.web;

import com.antibot.velocity.AntiBotPlugin;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Простой веб-дашборд для мониторинга AntiBot
 * Поддерживает удалённый доступ и базовую аутентификацию
 */
public class WebDashboard {

    private static final Logger logger = LoggerFactory.getLogger(WebDashboard.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    private final AntiBotPlugin plugin;
    private HttpServer server;
    private final int port;
    private final String bindAddress;
    private final String username;
    private final String password;
    private final boolean corsEnabled;

    public WebDashboard(AntiBotPlugin plugin, int port, String bindAddress) {
        this(plugin, port, bindAddress, null, null, false);
    }

    public WebDashboard(AntiBotPlugin plugin, int port, String bindAddress, 
                       String username, String password, boolean corsEnabled) {
        this.plugin = plugin;
        this.port = port;
        this.bindAddress = bindAddress;
        this.username = username;
        this.password = password;
        this.corsEnabled = corsEnabled;
    }

    /**
     * Запуск веб-сервера
     */
    public void start() {
        try {
            InetSocketAddress address;
            if ("0.0.0.0".equals(bindAddress)) {
                address = new InetSocketAddress(port);
            } else {
                address = new InetSocketAddress(bindAddress, port);
            }
            
            server = HttpServer.create(address, 0);
            
            server.createContext("/", new IndexHandler());
            server.createContext("/api/stats", new StatsHandler());
            server.createContext("/api/status", new StatusHandler());
            server.createContext("/api/blocks", new BlocksHandler());
            
            server.setExecutor(null);
            server.start();
            
            logger.info("╔═══════════════════════════════════════════════════════╗");
            logger.info("║     Web Dashboard запущен!                            ║");
            logger.info("╠═══════════════════════════════════════════════════════╣");
            logger.info("║  Порт: {}                                          ║", port);
            logger.info("║  Bind: {}                                    ║", bindAddress);
            
            if ("0.0.0.0".equals(bindAddress)) {
                logger.info("║                                                       ║");
                logger.info("║  🌐 Удалённый доступ ВКЛЮЧЕН                         ║");
                logger.info("║  Доступ: http://ВАШ_IP:{}                         ║", port);
                logger.info("║                                                       ║");
                logger.warn("║  ⚠️  ВНИМАНИЕ: Настройте firewall и аутентификацию! ║");
            } else if ("127.0.0.1".equals(bindAddress)) {
                logger.info("║                                                       ║");
                logger.info("║  🔒 Локальный доступ (безопасно)                     ║");
                logger.info("║  Доступ: http://localhost:{}                      ║", port);
            } else {
                logger.info("║                                                       ║");
                logger.info("║  Доступ: http://{}:{}                        ║", bindAddress, port);
            }
            
            logger.info("╚═══════════════════════════════════════════════════════╝");
        } catch (IOException e) {
            logger.error("Ошибка запуска Web Dashboard", e);
        }
    }

    /**
     * Остановка веб-сервера
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
            logger.info("Web Dashboard остановлен");
        }
    }

    /**
     * Проверка базовой аутентификации
     */
    private boolean checkAuth(HttpExchange exchange) {
        if (username == null || username.isEmpty()) {
            return true; // Аутентификация отключена
        }
        
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Basic ")) {
            return false;
        }
        
        String base64 = auth.substring(6);
        String decoded = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
        String[] parts = decoded.split(":", 2);
        
        return parts.length == 2 && 
               parts[0].equals(username) && 
               parts[1].equals(password);
    }

    /**
     * Отправка 401 Unauthorized
     */
    private void sendUnauthorized(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"AntiBot Dashboard\"");
        String response = "401 Unauthorized";
        exchange.sendResponseHeaders(401, response.length());
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Главная страница
     */
    private class IndexHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkAuth(exchange)) {
                sendUnauthorized(exchange);
                return;
            }
            
            String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>AntiBot Pro Dashboard</title>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: Arial, sans-serif; margin: 20px; background: #1a1a1a; color: #fff; }
                        h1 { color: #00ff00; }
                        .card { background: #2a2a2a; padding: 20px; margin: 10px 0; border-radius: 8px; }
                        .stat { display: inline-block; margin: 10px 20px; }
                        .stat-value { font-size: 32px; font-weight: bold; color: #00ff00; }
                        .stat-label { font-size: 14px; color: #888; }
                        button { background: #00ff00; color: #000; border: none; padding: 10px 20px; 
                                cursor: pointer; border-radius: 4px; font-weight: bold; }
                        button:hover { background: #00cc00; }
                        #status { margin-top: 20px; }
                        .attack-mode { color: #ff0000; font-weight: bold; }
                        .normal-mode { color: #00ff00; }
                    </style>
                </head>
                <body>
                    <h1>🛡️ AntiBot Pro v2.3.0 Dashboard</h1>
                    
                    <div class="card">
                        <h2>Статистика</h2>
                        <div id="stats">Загрузка...</div>
                        <button onclick="loadStats()">Обновить</button>
                    </div>
                    
                    <div class="card">
                        <h2>Статус системы</h2>
                        <div id="status">Загрузка...</div>
                    </div>
                    
                    <div class="card">
                        <h2>Заблокированные IP</h2>
                        <div id="blocks">Загрузка...</div>
                    </div>
                    
                    <script>
                        function loadStats() {
                            fetch('/api/stats')
                                .then(r => r.json())
                                .then(data => {
                                    document.getElementById('stats').innerHTML = `
                                        <div class="stat">
                                            <div class="stat-value">${data.totalPlayerJoins}</div>
                                            <div class="stat-label">Всего подключений</div>
                                        </div>
                                        <div class="stat">
                                            <div class="stat-value">${data.totalBlockedConnections}</div>
                                            <div class="stat-label">Заблокировано</div>
                                        </div>
                                        <div class="stat">
                                            <div class="stat-value">${data.totalCheatDetections}</div>
                                            <div class="stat-label">Читы обнаружены</div>
                                        </div>
                                        <div class="stat">
                                            <div class="stat-value">${data.totalVPNDetections}</div>
                                            <div class="stat-label">VPN обнаружены</div>
                                        </div>
                                        <div class="stat">
                                            <div class="stat-value">${data.totalBotDetections}</div>
                                            <div class="stat-label">Боты обнаружены</div>
                                        </div>
                                    `;
                                });
                        }
                        
                        function loadStatus() {
                            fetch('/api/status')
                                .then(r => r.json())
                                .then(data => {
                                    const modeClass = data.attackMode ? 'attack-mode' : 'normal-mode';
                                    const modeText = data.attackMode ? '🚨 РЕЖИМ АТАКИ' : '✅ Нормальный режим';
                                    document.getElementById('status').innerHTML = `
                                        <p class="${modeClass}">${modeText}</p>
                                        <p>Подключений/сек: <strong>${data.connectionsPerSecond}</strong></p>
                                        <p>Игроков онлайн: <strong>${data.playersOnline}</strong></p>
                                        <p>Отслеживается игроков: <strong>${data.trackedPlayers}</strong></p>
                                    `;
                                });
                        }
                        
                        function loadBlocks() {
                            fetch('/api/blocks')
                                .then(r => r.json())
                                .then(data => {
                                    if (data.blockedIPs.length === 0) {
                                        document.getElementById('blocks').innerHTML = '<p>Нет заблокированных IP</p>';
                                    } else {
                                        let html = '<ul>';
                                        data.blockedIPs.forEach(ip => {
                                            html += `<li>${ip}</li>`;
                                        });
                                        html += '</ul>';
                                        document.getElementById('blocks').innerHTML = html;
                                    }
                                });
                        }
                        
                        // Автообновление каждые 5 секунд
                        setInterval(() => {
                            loadStats();
                            loadStatus();
                            loadBlocks();
                        }, 5000);
                        
                        // Начальная загрузка
                        loadStats();
                        loadStatus();
                        loadBlocks();
                    </script>
                </body>
                </html>
                """;
            
            byte[] response = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }

    /**
     * API: Статистика
     */
    private class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkAuth(exchange)) {
                sendUnauthorized(exchange);
                return;
            }
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalPlayerJoins", plugin.getTotalPlayerJoins());
            stats.put("totalBlockedConnections", plugin.getTotalBlockedConnections());
            stats.put("totalCheatDetections", plugin.getTotalCheatDetections());
            stats.put("totalVPNDetections", plugin.getTotalVPNDetections());
            stats.put("totalBotDetections", plugin.getTotalBotDetections());
            
            sendJsonResponse(exchange, stats);
        }
    }

    /**
     * API: Статус системы
     */
    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkAuth(exchange)) {
                sendUnauthorized(exchange);
                return;
            }
            
            Map<String, Object> status = new HashMap<>();
            status.put("attackMode", plugin.isAttackMode());
            status.put("connectionsPerSecond", plugin.getGlobalConnectionsPerSecond());
            status.put("playersOnline", plugin.getServer().getPlayerCount());
            status.put("trackedPlayers", plugin.getBehaviorAnalyzer().getTotalTrackedPlayers());
            
            sendJsonResponse(exchange, status);
        }
    }

    /**
     * API: Заблокированные IP
     */
    private class BlocksHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkAuth(exchange)) {
                sendUnauthorized(exchange);
                return;
            }
            
            Map<String, Object> blocks = new HashMap<>();
            blocks.put("blockedIPs", plugin.getBlockedIps().keySet());
            
            sendJsonResponse(exchange, blocks);
        }
    }

    /**
     * Отправка JSON ответа
     */
    private void sendJsonResponse(HttpExchange exchange, Object data) throws IOException {
        String json = gson.toJson(data);
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        
        if (corsEnabled) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        }
        
        exchange.sendResponseHeaders(200, response.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }
}
