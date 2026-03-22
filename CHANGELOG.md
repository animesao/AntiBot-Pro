# 📜 AntiBot Pro - Changelog

Все изменения проекта документируются здесь.

---

## [v2.2.0](versions/v2.2.0.md) - 22.03.2026

### 🛠️ Исправления и улучшения

| Улучшение | Описание |
|-----------|----------|
| **Gson JSON** | Заменён кастомный JSON парсер на Gson для надёжности |
| **Shutdown Hooks** | Корректное завершение работы Discord компонентов |
| **Memory Leak Fix** | Исправлена утечка памяти при reload Discord бота |
| **HTTPS** | Все API запросы теперь используют HTTPS |
| **Thread Safety** | Улучшена потокобезопасность ExecutorService |
| **Code Quality** | Удалены дубликаты, улучшено качество кода |

### 📊 Изменения в коде

- `AccountLinkManager.java`: 475 → 260 строк (удалён кастомный парсер)
- `DiscordWebhook.java`: 589 → 320 строк (удалён кастомный парсер)
- Добавлены shutdown hooks для DiscordWebhook и DiscordBot
- Исправлены все HTTP → HTTPS для безопасных соединений

### 🔧 Технические изменения

- Добавлена зависимость `com.google.code.gson:gson:2.10.1`
- Добавлен `ProxyShutdownEvent` для корректного завершения
- Исправлен memory leak при reload Discord бота
- Улучшено управление потоками в `ExecutorService`

---

## [v2.1.0](versions/v2.1.0.md) - 19.03.2026

### 🆕 Новые функции

| Функция | Описание |
|---------|----------|
| **Discord Бот** | Полноценный Discord бот с slash-командами |
| **Привязка аккаунтов** | Система привязки Minecraft ↔ Discord |
| **DM Верификация** | Код верификации через личные сообщения |
| **Админ команды** | Управление из Discord: stats, reload, check |

### 🎯 Модули защиты

- Обнаружение 50+ читерских клиентов
- VPN/Proxy детекция через proxycheck.io
- GeoIP блокировка через ip-api.com
- Анализ поведения игроков
- Проверка никнеймов

### 📦 Структура

```
├── discord/
│   └── DiscordBot.java        # Discord Bot интеграция
├── account/
│   └── AccountLinkManager.java # Привязка аккаунтов
├── webhook/
│   └── DiscordWebhook.java   # Webhook уведомления
└── detection/
    ├── BehaviorAnalyzer.java
    ├── ClientBrandDetector.java
    ├── UsernameAnalyzer.java
    └── VPNProxyDetector.java
```

---

## [v2.0.0](versions/v2.0.0.md) - 14.03.2026

### 🆕 Первый публичный релиз

- Базовая защита от бот-атак
- Rate limiting подключений
- Блокировка по странам
- Webhook уведомления
- Конфигурация через YAML

---

## 📋 Формат changelog

Используется [Keep a Changelog](https://keepachangelog.com/ru-RU/1.0.0/):

- `🆕` - новые функции
- `🔧` - изменения
- `🐛` - исправления багов
- `🛠️` - улучшения
- `⚠️` - предупреждения
- `🔒` -安全问题
- `📦` - зависимости

---

## 📅 Релизы

| Версия | Дата | Статус |
|--------|------|--------|
| [2.2.0](versions/v2.2.0.md) | 22.03.2026 | ✅ Текущая |
| [2.1.0](versions/v2.1.0.md) | 19.03.2026 | 🟡 Предыдущая |
| 2.0.0 | 14.03.2026 | ⚪ Историческая |
