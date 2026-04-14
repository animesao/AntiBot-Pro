# ⚡ AntiBot Pro - Руководство по производительности

Это руководство поможет оптимизировать производительность AntiBot Pro для вашего сервера.

---

## 📊 Базовые метрики

### Рекомендуемые характеристики сервера

| Игроков | CPU | RAM | Рекомендации |
|---------|-----|-----|--------------|
| < 100 | 2 ядра | 512 MB | Стандартные настройки |
| 100-500 | 4 ядра | 1 GB | Увеличить thread pool |
| 500-1000 | 6 ядер | 2 GB | Включить кэширование |
| > 1000 | 8+ ядер | 4 GB | Оптимизация всех параметров |

---

## 🔧 Оптимизация конфигурации

### Для малых серверов (< 100 игроков)

```yaml
performance:
  async-thread-pool-size: 2
  max-concurrent-api-requests: 5
  api-timeout-seconds: 5
  geoip-cache-size: 1000
  vpn-cache-size: 500
  cleanup-interval: 120

api-rate-limiting:
  geoip-requests-per-minute: 20
  vpn-requests-per-minute: 50
```

### Для средних серверов (100-500 игроков)

```yaml
performance:
  async-thread-pool-size: 4
  max-concurrent-api-requests: 10
  api-timeout-seconds: 5
  geoip-cache-size: 5000
  vpn-cache-size: 2500
  cleanup-interval: 60

api-rate-limiting:
  geoip-requests-per-minute: 40
  vpn-requests-per-minute: 100
```

### Для больших серверов (> 500 игроков)

```yaml
performance:
  async-thread-pool-size: 8
  max-concurrent-api-requests: 20
  api-timeout-seconds: 3
  geoip-cache-size: 10000
  vpn-cache-size: 5000
  cleanup-interval: 30

api-rate-limiting:
  geoip-requests-per-minute: 60
  vpn-requests-per-minute: 150

database:
  cleanup-days: 7  # Чаще очищать БД
  cleanup-interval-hours: 12
```

---

## 🚀 Оптимизация API запросов

### Rate Limiting

Rate limiting защищает от превышения лимитов внешних API:

- **ip-api.com**: 45 запросов/минуту (бесплатно)
- **proxycheck.io**: зависит от плана

**Рекомендации:**
- Оставляйте запас 10-20% от лимита
- Используйте платные планы для больших серверов
- Включайте кэширование

### Кэширование

```yaml
performance:
  # GeoIP кэш (24 часа)
  geoip-cache-size: 10000
  
  # VPN кэш (6 часов)
  vpn-cache-size: 5000
```

**Расчёт размера кэша:**
- 1 запись ≈ 200 байт
- 10000 записей ≈ 2 MB RAM

### Async операции

Все внешние API запросы выполняются асинхронно:

```java
// Async GeoIP lookup
CompletableFuture<GeoData> future = geoIPChecker.lookupAsync(ip);

// Async VPN check
CompletableFuture<DetectionResult> future = vpnDetector.checkIPAsync(ip, apiKey);
```

---

## 💾 Оптимизация базы данных

### Автоочистка

```yaml
database:
  # Удалять записи старше N дней
  cleanup-days: 30
  
  # Интервал очистки
  cleanup-interval-hours: 24
```

### Индексы

База данных автоматически создаёт индексы для:
- IP адресов
- Имён игроков
- Временных меток
- Типов обнаружений

### Размер БД

| Записей | Размер БД | Рекомендации |
|---------|-----------|--------------|
| 10,000 | ~5 MB | Норма |
| 100,000 | ~50 MB | Увеличить cleanup |
| 1,000,000 | ~500 MB | Агрессивная очистка |

---

## 📈 Мониторинг производительности

### Метрики

Включите сбор метрик:

```yaml
performance:
  metrics-enabled: true
```

### Доступные метрики

| Метрика | Тип | Описание |
|---------|-----|----------|
| `antibot.connections.total` | Counter | Всего подключений |
| `antibot.blocks.total` | Counter | Всего блокировок |
| `antibot.detections.cheats` | Counter | Обнаружено читов |
| `antibot.detections.vpn` | Counter | Обнаружено VPN |
| `antibot.detections.bots` | Counter | Обнаружено ботов |
| `antibot.api.geoip.duration` | Histogram | Время GeoIP запросов |
| `antibot.api.vpn.duration` | Histogram | Время VPN запросов |
| `antibot.cache.geoip.size` | Gauge | Размер GeoIP кэша |
| `antibot.cache.vpn.size` | Gauge | Размер VPN кэша |

### Web Dashboard

Доступ к метрикам через Web Dashboard:

```
http://localhost:8080
```

---

## 🔍 Диагностика проблем

### Высокая нагрузка CPU

**Причины:**
- Слишком много API запросов
- Недостаточное кэширование
- Малый thread pool

**Решения:**
```yaml
performance:
  async-thread-pool-size: 8  # Увеличить
  geoip-cache-size: 20000    # Увеличить кэш
  
api-rate-limiting:
  geoip-requests-per-minute: 30  # Уменьшить
```

### Высокое использование RAM

**Причины:**
- Большие кэши
- Много отслеживаемых игроков
- Большая БД

**Решения:**
```yaml
performance:
  geoip-cache-size: 5000     # Уменьшить
  vpn-cache-size: 2500       # Уменьшить
  cleanup-interval: 30       # Чаще очищать

database:
  cleanup-days: 7            # Агрессивная очистка
```

### Медленные API запросы

**Причины:**
- Медленный интернет
- Перегруженные API
- Большие таймауты

**Решения:**
```yaml
performance:
  api-timeout-seconds: 3     # Уменьшить таймаут
  max-concurrent-api-requests: 5  # Ограничить
```

---

## 🎯 Best Practices

### 1. Используйте whitelist

Добавляйте проверенных игроков в whitelist:

```yaml
whitelist:
  players:
    - "TrustedPlayer1"
    - "TrustedPlayer2"
```

### 2. Настройте лимиты

Адаптируйте лимиты под ваш сервер:

```yaml
limits:
  max-connections-per-ip: 3      # Для малых серверов
  max-connections-per-second: 20 # Для малых серверов
```

### 3. Включите strict mode только при атаке

```yaml
protection:
  strict-mode: false  # Включать только при атаке
```

### 4. Используйте платные API

Для больших серверов используйте платные планы:
- **proxycheck.io**: от $5/месяц
- **ip-api.com**: от $13/месяц

### 5. Мониторинг

Регулярно проверяйте:
- Web Dashboard
- Логи плагина
- Метрики производительности

---

## 📊 Бенчмарки

### Тесты производительности

| Операция | Время | Примечание |
|----------|-------|------------|
| GeoIP lookup (cache hit) | < 1 ms | Из кэша |
| GeoIP lookup (cache miss) | 50-200 ms | API запрос |
| VPN check (cache hit) | < 1 ms | Из кэша |
| VPN check (cache miss) | 100-500 ms | API запрос |
| Username analysis | < 1 ms | Локально |
| Behavior analysis | < 5 ms | Локально |
| Database write | 1-10 ms | Async |
| Database read | 1-5 ms | С индексами |

### Нагрузочное тестирование

| Сценарий | Результат |
|----------|-----------|
| 100 подключений/сек | ✅ Норма |
| 500 подключений/сек | ⚠️ Высокая нагрузка |
| 1000 подключений/сек | 🚨 Режим атаки |

---

## 🛠️ Инструменты мониторинга

### 1. Web Dashboard

Встроенный дашборд для мониторинга

### 2. Prometheus + Grafana

```yaml
monitoring:
  prometheus-enabled: true
  prometheus-port: 9090
```

### 3. Логи

```yaml
logging:
  level: INFO
  file-logging: true
  debug-mode: false  # Только для отладки
```

---

## 📞 Поддержка

Нужна помощь с оптимизацией?
- Discord: [присоединиться](https://dsc.gg/alfheimguide)
- GitHub Issues: [создать issue](#)

---

<p align="center">
  <b>Оптимизированный AntiBot Pro = Защищённый сервер! 🚀</b>
</p>
