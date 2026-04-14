# 📚 AntiBot Pro - Документация

Добро пожаловать в документацию AntiBot Pro v2.4.0!

---

## 📖 Содержание

### Для пользователей

- [Установка и настройка](../README.md#-установка)
- [Конфигурация](../README.md#-настройка-discord-бота)
- [Команды](../README.md#-команды-в-игре)
- [Web Dashboard](../README.md#-web-dashboard)
- [База данных](../README.md#-база-данных)

### Для разработчиков

- [API Documentation](API.md) - Java API для интеграции
- [Performance Guide](PERFORMANCE.md) - Оптимизация производительности

### Дополнительно

- [Changelog](../CHANGELOG.md) - История изменений
- [Версии](../versions/) - Детальные релизы

---

## 🚀 Быстрый старт

### 1. Установка

```bash
# Скачайте AntiBot-Pro-2.4.0.jar
# Поместите в plugins/ вашего Velocity сервера
# Перезапустите сервер
```

### 2. Базовая настройка

```yaml
# plugins/antibot/config.yml

limits:
  max-connections-per-ip: 5
  max-connections-per-second: 30

protection:
  strict-mode: true
  name-check: true

discord:
  webhook-url: "YOUR_WEBHOOK_URL"
  enabled: true
```

### 3. Проверка работы

```bash
# В игре
/antibot status

# В браузере
http://localhost:8080
```

---

## 🔧 Основные функции

### Защита от ботов

- Rate limiting подключений
- Анализ поведения
- Детекция паттернов атак
- Автоматическая блокировка

### Детекция читов

- 50+ известных читерских клиентов
- Паттерн-анализ
- Автоматический кик
- Discord уведомления

### VPN/Proxy защита

- Интеграция с proxycheck.io
- Кэширование результатов
- Async проверка
- Rate limiting

### GeoIP

- Блокировка по странам
- Whitelist/Blacklist режимы
- Async lookup
- Кэширование

---

## 📊 Мониторинг

### Web Dashboard

Доступ: `http://localhost:8080`

**Возможности:**
- Статистика в реальном времени
- Статус системы
- Заблокированные IP
- REST API

### Метрики

```yaml
performance:
  metrics-enabled: true
```

**Доступные метрики:**
- Подключения
- Блокировки
- Детекции
- API производительность

### Логи

```yaml
logging:
  level: INFO
  file-logging: true
  debug-mode: false
```

---

## 🗄️ База данных

### SQLite

Автоматически создаётся в `plugins/antibot/antibot.db`

**Таблицы:**
- `connections` - История подключений
- `detections` - Обнаружения
- `blocks` - Блокировки IP

### Очистка

```yaml
database:
  cleanup-days: 30
  cleanup-interval-hours: 24
```

---

## 🔌 API для разработчиков

### Java API

```java
// Получение плагина
AntiBotPlugin antiBot = ...;

// Анализ поведения
BehaviorAnalyzer analyzer = antiBot.getBehaviorAnalyzer();
AnalysisResult result = analyzer.analyze("PlayerName", "192.168.1.1");

// Async GeoIP
CompletableFuture<GeoData> future = antiBot.getGeoIPChecker()
    .lookupAsync("1.2.3.4");
```

Подробнее: [API Documentation](API.md)

### REST API

```bash
# Статистика
curl http://localhost:8080/api/stats

# Статус
curl http://localhost:8080/api/status

# Блокировки
curl http://localhost:8080/api/blocks
```

---

## ⚡ Производительность

### Рекомендации

| Игроков | CPU | RAM | Thread Pool |
|---------|-----|-----|-------------|
| < 100 | 2 ядра | 512 MB | 2 |
| 100-500 | 4 ядра | 1 GB | 4 |
| > 500 | 6+ ядер | 2 GB | 8 |

Подробнее: [Performance Guide](PERFORMANCE.md)

### Оптимизация

```yaml
performance:
  async-thread-pool-size: 4
  max-concurrent-api-requests: 10
  api-timeout-seconds: 5
  
api-rate-limiting:
  geoip-requests-per-minute: 40
  vpn-requests-per-minute: 100
```

---

## 🧪 Тестирование

### Запуск тестов

```bash
# Все тесты
mvn test

# Конкретный тест
mvn test -Dtest=RateLimiterTest

# С покрытием
mvn test jacoco:report
```

### Доступные тесты

- `RateLimiterTest` - Rate limiting
- `BehaviorAnalyzerTest` - Анализ поведения
- `UsernameAnalyzerTest` - Анализ никнеймов

---

## 🔐 Безопасность

### Best Practices

1. **Не храните API ключи в коде**
2. **Используйте HTTPS для API**
3. **Регулярно обновляйте плагин**
4. **Мониторьте логи**
5. **Настройте whitelist**

### Рекомендуемые настройки

```yaml
protection:
  strict-mode: true
  name-check: true

client-check:
  enabled: true
  kick-cheat-clients: true

vpn-check:
  enabled: true
  block-vpn: true
```

---

## 📞 Поддержка

### Сообщество

- **Discord**: [присоединиться](https://dsc.gg/alfheimguide)
- **GitHub Issues**: [создать issue](#)

### FAQ

**Q: Как включить Web Dashboard?**
```yaml
web-dashboard:
  enabled: true
  port: 8080
```

**Q: Как оптимизировать для большого сервера?**
См. [Performance Guide](PERFORMANCE.md)

**Q: Как интегрироваться с другими плагинами?**
См. [API Documentation](API.md)

---

## 🎯 Roadmap

### v2.5.0 (планируется)

- 🤖 Machine Learning детекция
- 📱 Telegram Bot
- 🌍 Расширенная GeoIP база
- 📊 Grafana дашборды
- 🔄 Автообновление

---

## 📜 Лицензия

MIT License - свободное использование и модификация

---

<p align="center">
  <b>Спасибо за использование AntiBot Pro! 🛡️</b><br>
  <i>Версия 2.4.0 - Максимальная защита вашего сервера</i>
</p>
