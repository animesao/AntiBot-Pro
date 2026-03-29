# 🛡️ AntiBot Pro v2.3.0

[![Java](https://img.shields.io/badge/Java-17+-orange.svg?style=for-the-badge&logo=openjdk)](https://adoptium.net/)
[![Velocity](https://img.shields.io/badge/Velocity-3.3.0+-green.svg?style=for-the-badge&logo=minecraft)](https://papermc.io/velocity)
[![License](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)](LICENSE)
[![Build](https://img.shields.io/badge/Build-Maven-red.svg?style=for-the-badge&logo=apache-maven)](https://maven.apache.org/)
[![Discord](https://img.shields.io/badge/Discord-Бот-5865F2.svg?style=for-the-badge&logo=discord)](https://dsc.gg/alfheimguide)

**Продвинутый плагин защиты для Velocity Proxy серверов Minecraft**
Обеспечивает комплексную защиту от **бот-атак**, **читерских клиентов**, **VPN/Proxy** и **подозрительного поведения игроков**.

🆕 **Версия 2.3.0:** 2FA верификация + Проверка обновлений через GitHub + Ре-верификация при смене IP

---

## 📋 Содержание

- [Возможности](#-возможности)
- [Что нового в v2.2.0](#-что-нового-в-v220)
- [Структура проекта](#-структура-проекта)
- [Установка](#-установка)
- [Настройка Discord бота](#-настройка-discord-бота)
- [Система привязки аккаунтов](#-система-привязки-аккаунтов)
- [Команды Discord бота](#-команды-discord-бота)
- [Команды в игре](#-команды-в-игре)
- [Сборка](#-сборка)
- [Требования](#-требования)
- [Changelog](CHANGELOG.md)
- [История версий](versions/)

---

## 🚀 Возможности

### 🧠 Защита от читов (Detection)
- Обнаружение **50+ известных читерских клиентов**: *BeerClient, Wurst, Impact, Aristois и др.*
- Паттерн-анализ подозрительных client brand
- Автоматический кик игроков с читами
- Логирование с Discord уведомлениями

### 🌐 VPN / Proxy детекция
- Интеграция с **proxycheck.io API**
- Обнаружение: VPN, Proxy, Tor, Датацентровых IP
- Кэширование результатов для высокой производительности
- Настраиваемая блокировка

### 📊 Анализ поведения
- Отслеживание паттернов подключений
- Детекция бот-подобного поведения
- Анализ быстрых переподключений
- Выявление атакующих паттернов

### 🧑‍💻 Защита никнеймов
- Продвинутый анализ никнеймов
- Детекция автогенерированных имён
- Расчёт риска на основе энтропии
- Чёрный список подозрительных паттернов

### 🌍 GeoIP защита
- Блокировка / разрешение по странам
- Режим **белого** и **чёрного** списка
- Интеграция с **ip-api.com**
- Отображение страны игрока в Discord

### 💬 Discord интеграция
- **Webhook-уведомления** о всех событиях
- **Discord Бот** с slash-командами
- **Привязка аккаунтов** Minecraft ↔ Discord
- Логирование входов / выходов игроков с IP
- Настраиваемые **цвета embed-сообщений**
- Маскирование IP (опционально)
- DM верификация игроков

### 🔒 Дополнительные функции
- Лимит подключений с одного IP
- Защита от бот-атак (Anti-Bot)
- Режим усиленной защиты (Strict Mode)
- CAPTCHA верификация
- Белые списки IP и игроков

---

## 🆕 Что нового в v2.3.0

### 🔐 Система повторной верификации
- Верификация при смене IP адреса
- Периодическая верификация (по умолчанию каждые 24 часа)
- Настраиваемый период верификации

### 🌐 GitHub Update Checker
- Автоматическая проверка обновлений при запуске
- Команды `/antibot update` и `/update` в Discord

### 📱 Новые команды
- `/antibot verify <код>` - Верификация в игре
- `/antibot reverify` - Запросить новый код
- `/antibot update` - Проверить обновления
- `/update` - Проверить обновления (Discord)

### 🛠️ Исправления и улучшения

| Улучшение | Описание |
|-----------|----------|
| **Gson JSON** | Заменён кастомный JSON парсер на Gson для надёжности |
| **Shutdown Hooks** | Корректное завершение работы Discord компонентов |
| **Memory Leak Fix** | Исправлена утечка памяти при reload Discord бота |
| **HTTPS** | Все API запросы теперь используют HTTPS |
| **Thread Safety** | Улучшена потокобезопасность ExecutorService |
| **Code Quality** | Удалены дубликаты, улучшено качество кода |

### 📈 Изменения в коде

- `AccountLinkManager.java`: 475 → 260 строк (удалён кастомный парсер)
- `DiscordWebhook.java`: 589 → 320 строк (удалён кастомный парсер)
- Добавлены shutdown hooks для DiscordWebhook и DiscordBot
- Исправлены все HTTP → HTTPS для безопасных соединений

---

## 🗂️ Структура проекта

```
src/main/java/com/antibot/velocity/
├── detection/
│   ├── BehaviorAnalyzer.java      # Анализ поведения игроков
│   ├── ClientBrandDetector.java   # Детекция читерских клиентов
│   ├── UsernameAnalyzer.java      # Анализ никнеймов
│   └── VPNProxyDetector.java      # VPN / Proxy детекция
├── verification/
│   └── CaptchaVerification.java   # CAPTCHA верификация
├── webhook/
│   └── DiscordWebhook.java        # Discord Webhook уведомления
├── discord/
│   └── DiscordBot.java            # Discord Bot интеграция
├── account/
│   └── AccountLinkManager.java    # Привязка аккаунтов
├── AntiBotCommand.java           # Команды администратора
├── AntiBotPlugin.java            # Главный класс плагина
├── ConfigManager.java            # Управление конфигурацией
├── ConnectionData.java           # Данные подключений
└── GeoIPChecker.java             # GeoIP проверка
```

---

## 📥 Установка

1. Скачайте готовый файл плагина: `AntiBot-Pro-2.3.0.jar`
2. Скопируйте его в папку `plugins/` вашего Velocity сервера
3. Перезапустите сервер
4. Отредактируйте конфигурацию в `plugins/antibot/config.yml`
5. Выполните команду для применения настроек:
   ```bash
   /antibot reload
   ```

---

## ⚙️ Настройка Discord Бота

### 1. Создание Discord приложения

1. Перейдите на [Discord Developer Portal](https://discord.com/developers/applications)
2. Нажмите **New Application** и введите имя
3. Перейдите в раздел **Bot** в левом меню
4. Нажмите **Add Bot** → **Yes, do it!**

### 2. Получение токена

1. В разделе **Bot** найдите **Token**
2. Нажмите **Copy** или **Reset Token**
3. Вставьте токен в `config.yml`:
   ```yaml
   discord-bot:
     enabled: true
     bot-token: "YOUR_BOT_TOKEN_HERE"
   ```

### 3. Настройка прав бота

1. Перейдите в **OAuth2** → **URL Generator**
2. Выберите scopes: `bot`, `applications.commands`
3. Выберите permissions:
   - **Send Messages**
   - **Embed Links**
   - **Use Slash Commands**
   - **Read Message History**
   - **Manage Roles** (опционально)
4. Скопируйте сгенерированную ссылку
5. Откройте ссылку в браузере и пригласите бота на сервер

### 4. Получение ID сервера и ролей

1. В Discord включите **Режим разработчика**:
   - Настройки → Дополнительные → Режим разработчика
2. ПКМ по серверу → **Копировать ID**
3. ПКМ по роли → **Копировать ID**
4. Вставьте ID в `config.yml`:
   ```yaml
   discord-bot:
     allowed-guilds:
       - "123456789012345678"  # ID вашего сервера
     admin-roles:
       - "987654321098765432"  # ID роли администратора
   ```

---

## 🔗 Система привязки аккаунтов

### Настройка

```yaml
account-linking:
  # Автоматически добавлять привязанные аккаунты в whitelist
  auto-whitelist: false
  
  # Отправлять уведомления в ЛС Discord при входе
  dm-notification: true
  
  # Максимум аккаунтов на один Discord
  max-per-discord: 3
  
  # Требовать верификацию через DM
  require-verification: false
  
  # Доверенные роли (упрощенная проверка)
  trusted-roles: []
```

### Как использовать

1. Игрок заходит на сервер
2. В Discord использует команду `/link <никнейм>`
3. Бот проверяет наличие игрока онлайн
4. Аккаунты привязываются

### Верификация через DM

Если включена `require-verification: true`:

1. Игрок использует `/verify` в Discord
2. Бот отправляет код в ЛС
3. Игрок вводит код в ЛС бота
4. После верификации использует `/link <ник>`

---

## 💬 Команды Discord бота

### Основные команды

| Команда | Описание |
|---------|----------|
| `/link <ник>` | Привязать Minecraft аккаунт к Discord |
| `/unlink` | Отвязать Minecraft аккаунт |
| `/verify` | Получить код верификации в ЛС |
| `/status` | Показать статус сервера |
| `/online` | Показать игроков онлайн |
| `/help` | Показать список команд |

### Админ команды

| Команда | Описание | Права |
|---------|----------|--------|
| `/stats` | Статистика AntiBot | Admin Role |
| `/reload` | Перезагрузить конфигурацию | Admin Role |
| `/check <игрок>` | Проверить игрока | Admin Role |
| `/whitelist <action> <type> <value>` | Управление whitelist | Admin Role |

---

## 🎮 Команды в игре

| Команда | Описание |
|---------|----------|
| `/antibot reload` | Перезагрузить конфигурацию |
| `/antibot status` | Статус защиты и модулей |
| `/antibot stats` | Статистика обнаружений |
| `/antibot blocked` | Список заблокированных IP |
| `/antibot unblock <ip>` | Разблокировать IP |
| `/antibot whitelist add/remove <ip/player> <value>` | Добавить/удалить из вайтлиста |
| `/antibot behavior [player]` | Анализ поведения игрока |
| `/antibot check <player/ip>` | Проверка игрока или IP |
| `/antibot linked <player/discord> <value>` | Проверить привязку аккаунтов |
| `/antibot discord status` | Статус Discord бота |
| `/antibot accounts` | Статистика привязанных аккаунтов |

### Права доступа

| Permission | Описание |
|------------|----------|
| `antibot.admin` | Полный доступ ко всем командам |

---

## 🛠️ Сборка

### Требования
- **Java 17+**
- **Maven 3.6+**

### Команды сборки

```bash
# Очистка и сборка
mvn clean package

# Сборка без тестов
mvn clean package -DskipTests

# Установка зависимостей
mvn install
```

Готовый файл плагина будет находиться в папке `target/`:
```
target/AntiBot-Pro-2.3.0.jar
```

---

## 📋 Требования

| Компонент | Версия |
|-----------|--------|
| **Velocity** | 3.3.0+ |
| **Java** | 17+ |
| **Maven** | 3.6+ (для сборки) |
| **Discord Bot** | Требуется для Discord интеграции |

---

## 🔗 Ссылки

- **Velocity Proxy**: [https://papermc.io/velocity](https://papermc.io/velocity)
- **ProxyCheck API**: [https://proxycheck.io/](https://proxycheck.io/)
- **ip-api.com**: [https://ip-api.com/](https://ip-api.com/)
- **Discord Developer Portal**: [https://discord.com/developers/applications](https://discord.com/developers/applications)

---

## 📜 Changelog

См. полную историю изменений в [CHANGELOG.md](CHANGELOG.md) и [версии](versions/)

---

<p align="center">
  <b>🔥 AntiBot Pro v2.3.0 — максимальная защита вашего Velocity-сервера</b><br>
  <i>От атак, ботов, читеров и злоупотреблений + Discord интеграция</i>
</p>
