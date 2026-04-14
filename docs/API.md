# 📚 AntiBot Pro - API Documentation

Документация для разработчиков, желающих интегрироваться с AntiBot Pro.

---

## 🔌 Java API

### Получение экземпляра плагина

```java
import com.antibot.velocity.AntiBotPlugin;
import com.velocitypowered.api.proxy.ProxyServer;

// Через Velocity API
ProxyServer server = ...;
Optional<PluginContainer> container = server.getPluginManager()
    .getPlugin("antibot");

if (container.isPresent()) {
    Object instance = container.get().getInstance().orElse(null);
    if (instance instanceof AntiBotPlugin) {
        AntiBotPlugin antiBot = (AntiBotPlugin) instance;
        // Используйте API
    }
}
```

---

## 🛡️ Detection API

### BehaviorAnalyzer

Анализ поведения игроков:

```java
BehaviorAnalyzer analyzer = antiBot.getBehaviorAnalyzer();

// Записать подключение
analyzer.recordConnection("PlayerName", "192.168.1.1");

// Анализ поведения
BehaviorAnalyzer.AnalysisResult result = analyzer.analyze("PlayerName", "192.168.1.1");

if (result.isSuspicious()) {
    int riskScore = result.getRiskScore();
    BehaviorType type = result.getBehaviorType();
    List<String> flags = result.getFlags();
    
    // Обработка подозрительного поведения
}

// Получить поведение игрока
PlayerBehavior behavior = analyzer.getOrCreateBehavior("PlayerName", "192.168.1.1");
int totalConnections = behavior.getTotalConnections();
int fastReconnects = behavior.getFastReconnects();
```

### ClientBrandDetector

Детекция читерских клиентов:

```java
ClientBrandDetector detector = antiBot.getClientBrandDetector();

// Анализ клиента
ClientBrandDetector.DetectionResult result = detector.analyze("vanilla");

if (result.isDetected()) {
    String clientName = result.getClientName();
    DetectionType type = result.getType();
    String reason = result.getReason();
    
    // Обработка читерского клиента
}
```

### UsernameAnalyzer

Анализ никнеймов:

```java
UsernameAnalyzer analyzer = new UsernameAnalyzer();

// Анализ никнейма
UsernameAnalyzer.AnalysisResult result = analyzer.analyze("Player123");

if (result.isSuspicious()) {
    int riskScore = result.getRiskScore();
    RiskLevel level = result.getRiskLevel();
    List<String> reasons = result.getReasons();
    
    // Обработка подозрительного никнейма
}
```

### VPNProxyDetector

Детекция VPN/Proxy:

```java
VPNProxyDetector detector = antiBot.getVpnProxyDetector();

// Синхронная проверка
DetectionResult result = detector.checkIP("1.2.3.4", "api-key");

// Асинхронная проверка
CompletableFuture<DetectionResult> future = detector.checkIPAsync("1.2.3.4", "api-key");
future.thenAccept(result -> {
    if (result.isVPN()) {
        // Обработка VPN
    }
});

// Проверка результата
if (result.isSuspicious()) {
    boolean isVPN = result.isVPN();
    boolean isProxy = result.isProxy();
    boolean isTor = result.isTor();
    int riskScore = result.getRiskScore();
}
```

### GeoIPChecker

Проверка геолокации:

```java
GeoIPChecker checker = antiBot.getGeoIPChecker();

// Синхронный lookup
GeoData data = checker.lookup("1.2.3.4");

// Асинхронный lookup
CompletableFuture<GeoData> future = checker.lookupAsync("1.2.3.4");
future.thenAccept(data -> {
    String country = data.getCountryCode();
    String city = data.getCity();
    String isp = data.getIsp();
});

// Проверка разрешённой страны
boolean allowed = checker.isCountryAllowed("RU");
```

---

## 🗄️ Database API

### DatabaseManager

Работа с базой данных:

```java
DatabaseManager db = new DatabaseManager(dataDirectory);
db.initialize();

// Логирование подключения
db.logConnection("PlayerName", "192.168.1.1", "Russia", "vanilla", false, null);

// Логирование обнаружения
db.logDetection("CHEAT", "PlayerName", "192.168.1.1", "Wurst client", 90);

// Логирование блокировки
long expiresAt = System.currentTimeMillis() + 600000; // 10 минут
db.logBlock("192.168.1.1", "Rate limit exceeded", expiresAt);

// Получение статистики игрока
CompletableFuture<PlayerStats> future = db.getPlayerStats("PlayerName");
future.thenAccept(stats -> {
    int total = stats.totalConnections;
    int blocked = stats.blockedConnections;
    long firstSeen = stats.firstSeen;
    long lastSeen = stats.lastSeen;
});

// Получение последних подключений
CompletableFuture<List<ConnectionRecord>> future = db.getRecentConnections(100);
future.thenAccept(records -> {
    for (ConnectionRecord record : records) {
        String player = record.playerName;
        String ip = record.ipAddress;
        long timestamp = record.timestamp;
    }
});

// Очистка старых записей
db.cleanupOldRecords(30); // Удалить записи старше 30 дней

// Закрытие
db.shutdown();
```

---

## 📊 Metrics API

### MetricsCollector

Сборщик метрик:

```java
MetricsCollector metrics = new MetricsCollector();

// Счетчики
metrics.incrementCounter("connections.total");
metrics.incrementCounter("blocks.total", 5);

// Gauge
metrics.setGauge("players.online", 100);

// Гистограмма
metrics.recordHistogram("api.geoip.duration", 150); // ms

// Получение значений
long connections = metrics.getCounter("connections.total");
long playersOnline = metrics.getGauge("players.online");

// Статистика гистограммы
HistogramSnapshot snapshot = metrics.getHistogramSnapshot("api.geoip.duration");
long count = snapshot.getCount();
double average = snapshot.getAverage();
long min = snapshot.getMin();
long max = snapshot.getMax();

// Сброс метрик
metrics.reset();
```

---

## ⚡ Utility API

### RateLimiter

Контроль частоты запросов:

```java
RateLimiter limiter = new RateLimiter(60); // 60 запросов/минуту

// Попытка получить разрешение
if (limiter.tryAcquire("api-key")) {
    // Выполнить запрос
} else {
    // Лимит превышен
}

// С таймаутом
if (limiter.tryAcquire("api-key", 5000)) { // 5 секунд
    // Выполнить запрос
}

// Текущее количество запросов
int current = limiter.getCurrentCount("api-key");

// Очистка
limiter.cleanup();
```

### AsyncExecutor

Управление асинхронными задачами:

```java
AsyncExecutor executor = new AsyncExecutor(4, 10); // 4 потока, макс 10 задач

// Выполнить задачу
CompletableFuture<String> future = executor.submit(() -> {
    // Долгая операция
    return "result";
});

// С таймаутом
CompletableFuture<String> future = executor.submitWithTimeout(() -> {
    // Долгая операция
    return "result";
}, 5, TimeUnit.SECONDS);

// Обработка результата
future.thenAccept(result -> {
    // Использовать result
}).exceptionally(error -> {
    // Обработка ошибки
    return null;
});

// Количество активных задач
int active = executor.getActiveTaskCount();

// Завершение
executor.shutdown();
```

---

## 🌐 Web API

### REST Endpoints

#### GET /api/stats

Получить статистику:

```bash
curl http://localhost:8080/api/stats
```

Ответ:
```json
{
  "totalPlayerJoins": 1234,
  "totalBlockedConnections": 56,
  "totalCheatDetections": 12,
  "totalVPNDetections": 8,
  "totalBotDetections": 34
}
```

#### GET /api/status

Получить статус системы:

```bash
curl http://localhost:8080/api/status
```

Ответ:
```json
{
  "attackMode": false,
  "connectionsPerSecond": 5,
  "playersOnline": 42,
  "trackedPlayers": 150
}
```

#### GET /api/blocks

Получить заблокированные IP:

```bash
curl http://localhost:8080/api/blocks
```

Ответ:
```json
{
  "blockedIPs": [
    "1.2.3.4",
    "5.6.7.8"
  ]
}
```

---

## 🔔 Events API

### Подписка на события

```java
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;

public class MyPlugin {
    
    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        String ip = event.getConnection()
            .getRemoteAddress()
            .getAddress()
            .getHostAddress();
        
        // Проверка через AntiBot API
        BehaviorAnalyzer analyzer = antiBot.getBehaviorAnalyzer();
        AnalysisResult result = analyzer.analyze(event.getUsername(), ip);
        
        if (result.isSuspicious()) {
            // Дополнительная обработка
        }
    }
}
```

---

## 📝 Примеры использования

### Пример 1: Кастомная проверка игрока

```java
public boolean checkPlayer(String playerName, String ip) {
    // Проверка никнейма
    UsernameAnalyzer usernameAnalyzer = new UsernameAnalyzer();
    if (usernameAnalyzer.analyze(playerName).isSuspicious()) {
        return false;
    }
    
    // Проверка поведения
    BehaviorAnalyzer behaviorAnalyzer = antiBot.getBehaviorAnalyzer();
    if (behaviorAnalyzer.analyze(playerName, ip).isSuspicious()) {
        return false;
    }
    
    // Проверка VPN
    VPNProxyDetector vpnDetector = antiBot.getVpnProxyDetector();
    if (vpnDetector.checkIP(ip, apiKey).isVPN()) {
        return false;
    }
    
    return true;
}
```

### Пример 2: Async проверка с метриками

```java
public CompletableFuture<Boolean> checkPlayerAsync(String playerName, String ip) {
    MetricsCollector metrics = new MetricsCollector();
    long startTime = System.currentTimeMillis();
    
    return CompletableFuture.supplyAsync(() -> {
        // GeoIP проверка
        GeoIPChecker geoChecker = antiBot.getGeoIPChecker();
        return geoChecker.lookupAsync(ip);
    }).thenCompose(geoData -> {
        // VPN проверка
        VPNProxyDetector vpnDetector = antiBot.getVpnProxyDetector();
        return vpnDetector.checkIPAsync(ip, apiKey);
    }).thenApply(vpnResult -> {
        // Метрики
        long duration = System.currentTimeMillis() - startTime;
        metrics.recordHistogram("player.check.duration", duration);
        
        return !vpnResult.isVPN();
    });
}
```

### Пример 3: Интеграция с базой данных

```java
public void logPlayerActivity(String playerName, String ip, String action) {
    DatabaseManager db = new DatabaseManager(dataDirectory);
    db.initialize();
    
    // Логирование
    db.logConnection(playerName, ip, "Unknown", "vanilla", false, null);
    
    // Получение статистики
    db.getPlayerStats(playerName).thenAccept(stats -> {
        if (stats.blockedConnections > 5) {
            // Игрок часто блокируется
            logger.warn("Player {} has {} blocked connections", 
                playerName, stats.blockedConnections);
        }
    });
}
```

---

## 🔐 Безопасность

### Best Practices

1. **Не храните API ключи в коде**
```java
// ❌ Плохо
String apiKey = "my-secret-key";

// ✅ Хорошо
String apiKey = config.getString("vpn-check.api-key");
```

2. **Используйте async операции**
```java
// ❌ Плохо (блокирует поток)
GeoData data = checker.lookup(ip);

// ✅ Хорошо (не блокирует)
checker.lookupAsync(ip).thenAccept(data -> {
    // Обработка
});
```

3. **Обрабатывайте ошибки**
```java
future.exceptionally(error -> {
    logger.error("API error", error);
    return defaultValue;
});
```

---

## 📞 Поддержка

Вопросы по API?
- Discord: [присоединиться](https://dsc.gg/alfheimguide)
- GitHub Issues: [создать issue](#)

---

<p align="center">
  <b>Happy coding! 🚀</b>
</p>
