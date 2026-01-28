# AntiBot Pro v2.0.0

## Описание
Продвинутый плагин защиты для Velocity Proxy сервера Minecraft. Обеспечивает комплексную защиту от бот-атак, читерских клиентов, VPN/Proxy и подозрительного поведения.

## Возможности

### Защита от читов (LeakTime Detection)
- Обнаружение 50+ известных читерских клиентов (BeerClient, Wurst, Impact, Aristois, etc.)
- Паттерн-анализ подозрительных брендов клиентов
- Автоматический кик игроков с читами

### VPN/Proxy Детекция
- Интеграция с proxycheck.io API
- Обнаружение VPN, Proxy, Tor
- Обнаружение датацентр IP адресов
- Кэширование результатов для производительности

### Анализ поведения
- Отслеживание паттернов подключений
- Обнаружение бот-подобного поведения
- Анализ быстрых переподключений
- Отслеживание подозрительных действий

### Защита никнеймов
- Продвинутый анализ никнеймов
- Обнаружение автогенерированных ников
- Расчет риска на основе энтропии
- Черный список подозрительных паттернов

### GeoIP Защита
- Блокировка/разрешение по странам
- Режим белого/черного списка
- Интеграция с ip-api.com

### Discord Интеграция (Расширенная)
- Webhook уведомления о всех событиях
- Логирование входов/выходов игроков с IP адресами
- Настраиваемые цвета embed сообщений
- Выбор событий для логирования
- Маскирование IP (опционально)
- Настройка имени и аватара бота

## Структура проекта
```
src/main/java/com/antibot/velocity/
├── detection/
│   ├── BehaviorAnalyzer.java     - Анализ поведения игроков
│   ├── ClientBrandDetector.java   - Обнаружение читерских клиентов
│   ├── UsernameAnalyzer.java      - Анализ никнеймов
│   └── VPNProxyDetector.java      - VPN/Proxy детекция
├── verification/
│   └── CaptchaVerification.java   - Система верификации (CAPTCHA)
├── webhook/
│   └── DiscordWebhook.java        - Discord интеграция
├── AntiBotCommand.java            - Команды администратора
├── AntiBotPlugin.java             - Главный класс плагина
├── ConfigManager.java             - Управление конфигурацией
├── ConnectionData.java            - Данные подключений
└── GeoIPChecker.java              - GeoIP проверка
```

## Discord Webhook Настройки

### Основные
- `webhook-url` - URL вебхука Discord
- `enabled` - включить/выключить уведомления
- `bot-name` - имя бота в Discord
- `avatar-url` - URL аватара бота
- `mask-ip` - маскировать IP адреса (true/false)

### События для логирования
- `player-join` - вход игрока (ник, IP, страна, клиент)
- `player-leave` - выход игрока (время сессии)
- `cheat-detection` - обнаружение читов
- `vpn-detection` - обнаружение VPN/Proxy
- `bot-detection` - обнаружение ботов
- `ip-block` - блокировка IP
- `player-kick` - кик игрока
- `attack-mode` - режим атаки
- `suspicious-name` - подозрительный ник

### Цвета (HEX)
- `join` - зеленый (#00FF00)
- `leave` - серый (#808080)
- `cheat` - красный (#FF0000)
- `vpn` - оранжевый (#FFA500)
- `bot` - оранжево-красный (#FF4500)
- `block` - темно-красный (#8B0000)
- `kick` - малиновый (#DC143C)
- `attack` - красный (#FF0000)
- `suspicious` - желтый (#FFFF00)

## Команды
- `/antibot reload` - Перезагрузить конфигурацию
- `/antibot status` - Статус защиты и модулей
- `/antibot stats` - Статистика обнаружений
- `/antibot blocked` - Список заблокированных IP
- `/antibot unblock <ip>` - Разблокировать IP
- `/antibot whitelist <add/remove> <ip/player> <value>` - Управление вайтлистом
- `/antibot behavior [player]` - Анализ поведения
- `/antibot check <player/ip>` - Проверить игрока или IP

## Сборка
```bash
mvn clean package
```
Готовый плагин: `AntiBot-Pro-2.0.0.jar`

## Установка
1. Скопировать `AntiBot-Pro-2.0.0.jar` в папку `plugins/` Velocity сервера
2. Перезапустить сервер
3. Настроить `plugins/antibot/config.yml`
4. Выполнить `/antibot reload`

## Требования
- Velocity 3.3.0+
- Java 17+

## Права
- `antibot.admin` - Доступ ко всем командам
