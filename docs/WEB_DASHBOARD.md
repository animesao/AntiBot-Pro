# 🌐 Web Dashboard - Руководство по настройке

Полное руководство по настройке Web Dashboard для локального и удалённого доступа.

---

## 📋 Содержание

1. [Локальный доступ](#-локальный-доступ)
2. [Удалённый доступ](#-удалённый-доступ)
3. [Настройка на хостинге](#-настройка-на-хостинге)
4. [Безопасность](#-безопасность)
5. [Nginx/Apache](#-nginxapache-reverse-proxy)
6. [Troubleshooting](#-troubleshooting)

---

## 🏠 Локальный доступ

### Настройка

```yaml
web-dashboard:
  enabled: true
  port: 8080
  bind-address: "127.0.0.1"
```

### Доступ

```
http://localhost:8080
```

**Преимущества:**
- ✅ Максимальная безопасность
- ✅ Не требует настройки firewall
- ✅ Быстрый доступ с сервера

**Недостатки:**
- ❌ Доступ только с сервера
- ❌ Нужен SSH для просмотра

---

## 🌍 Удалённый доступ

### Настройка

```yaml
web-dashboard:
  enabled: true
  port: 8080
  bind-address: "0.0.0.0"  # Слушать на всех интерфейсах
  
  # ОБЯЗАТЕЛЬНО настройте аутентификацию!
  username: "admin"
  password: "ваш_сложный_пароль"
```

### Доступ

```
http://ВАШ_IP_СЕРВЕРА:8080
```

**Примеры:**
- `http://123.45.67.89:8080`
- `http://mc.example.com:8080`

### Как узнать IP сервера?

```bash
# Linux
curl ifconfig.me

# Windows
curl ifconfig.me

# Или
ip addr show
```

---

## 🖥️ Настройка на хостинге

### Шаг 1: Настройка конфигурации

```yaml
web-dashboard:
  enabled: true
  port: 8080
  bind-address: "0.0.0.0"
  username: "admin"
  password: "сложный_пароль_123"
  cors-enabled: true
  allowed-origins: "*"
```

### Шаг 2: Открытие порта в firewall

#### Ubuntu/Debian (UFW)

```bash
# Открыть порт 8080
sudo ufw allow 8080/tcp

# Проверить статус
sudo ufw status
```

#### CentOS/RHEL (firewalld)

```bash
# Открыть порт 8080
sudo firewall-cmd --permanent --add-port=8080/tcp
sudo firewall-cmd --reload

# Проверить
sudo firewall-cmd --list-ports
```

#### Windows Server

```powershell
# Открыть порт 8080
New-NetFirewallRule -DisplayName "AntiBot Dashboard" -Direction Inbound -LocalPort 8080 -Protocol TCP -Action Allow
```

### Шаг 3: Проверка доступа

```bash
# С другого компьютера
curl http://ВАШ_IP:8080/api/status
```

### Шаг 4: Настройка панели хостинга

Если используете панель управления (Pterodactyl, Multicraft и т.д.):

1. Добавьте порт 8080 в список разрешённых портов
2. Настройте проброс портов (port forwarding)
3. Проверьте доступ через внешний IP

---

## 🔐 Безопасность

### 1. Базовая аутентификация

```yaml
web-dashboard:
  username: "admin"
  password: "сложный_пароль_с_цифрами_123"
```

**Генерация сложного пароля:**
```bash
# Linux/Mac
openssl rand -base64 32

# Или онлайн
https://passwordsgenerator.net/
```

### 2. Ограничение по IP

```yaml
web-dashboard:
  bind-address: "ВАШ_ДОМАШНИЙ_IP"
```

Или используйте firewall:

```bash
# Разрешить только с определённого IP
sudo ufw allow from 123.45.67.89 to any port 8080
```

### 3. Использование VPN

Рекомендуется использовать VPN для доступа к дашборду:

1. Настройте VPN на сервере (WireGuard, OpenVPN)
2. Используйте `bind-address: "10.0.0.1"` (VPN IP)
3. Подключайтесь через VPN

### 4. Регулярная смена паролей

```bash
# Каждые 30 дней меняйте пароль
# Используйте менеджер паролей (1Password, Bitwarden)
```

---

## 🔄 Nginx/Apache Reverse Proxy

### Nginx (рекомендуется)

#### Установка

```bash
sudo apt install nginx certbot python3-certbot-nginx
```

#### Конфигурация

```nginx
# /etc/nginx/sites-available/antibot

server {
    listen 80;
    server_name antibot.example.com;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

#### Включение

```bash
sudo ln -s /etc/nginx/sites-available/antibot /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

#### SSL (HTTPS)

```bash
sudo certbot --nginx -d antibot.example.com
```

#### Настройка AntiBot

```yaml
web-dashboard:
  enabled: true
  port: 8080
  bind-address: "127.0.0.1"  # Только локальный доступ
  username: ""  # Nginx будет обрабатывать аутентификацию
  password: ""
```

### Apache

#### Конфигурация

```apache
# /etc/apache2/sites-available/antibot.conf

<VirtualHost *:80>
    ServerName antibot.example.com

    ProxyPreserveHost On
    ProxyPass / http://127.0.0.1:8080/
    ProxyPassReverse / http://127.0.0.1:8080/
</VirtualHost>
```

#### Включение

```bash
sudo a2enmod proxy proxy_http
sudo a2ensite antibot
sudo systemctl reload apache2
```

---

## 🎯 Примеры настройки

### Пример 1: Домашний сервер

```yaml
web-dashboard:
  enabled: true
  port: 8080
  bind-address: "0.0.0.0"
  username: "admin"
  password: "мой_пароль_123"
```

**Доступ:**
- Локально: `http://localhost:8080`
- Удалённо: `http://192.168.1.100:8080`

### Пример 2: VPS хостинг

```yaml
web-dashboard:
  enabled: true
  port: 8080
  bind-address: "0.0.0.0"
  username: "admin"
  password: "сложный_пароль"
```

**Firewall:**
```bash
sudo ufw allow 8080/tcp
```

**Доступ:**
```
http://123.45.67.89:8080
```

### Пример 3: С доменом и SSL

```yaml
web-dashboard:
  enabled: true
  port: 8080
  bind-address: "127.0.0.1"
```

**Nginx + Certbot:**
```bash
sudo certbot --nginx -d antibot.example.com
```

**Доступ:**
```
https://antibot.example.com
```

---

## 🐛 Troubleshooting

### Проблема: Не могу подключиться

**Решение:**

1. Проверьте, запущен ли дашборд:
```bash
netstat -tulpn | grep 8080
```

2. Проверьте firewall:
```bash
sudo ufw status
```

3. Проверьте bind-address в конфиге

4. Проверьте логи:
```bash
tail -f plugins/antibot/logs/latest.log
```

### Проблема: 401 Unauthorized

**Решение:**

1. Проверьте username/password в конфиге
2. Очистите кэш браузера
3. Используйте другой браузер

### Проблема: Медленная загрузка

**Решение:**

1. Используйте Nginx reverse proxy
2. Включите кэширование в браузере
3. Проверьте нагрузку на сервер

### Проблема: CORS ошибки

**Решение:**

```yaml
web-dashboard:
  cors-enabled: true
  allowed-origins: "*"
```

---

## 📱 Мобильный доступ

Dashboard адаптивен и работает на мобильных устройствах:

1. Откройте браузер на телефоне
2. Введите URL: `http://ВАШ_IP:8080`
3. Введите логин/пароль
4. Добавьте в закладки для быстрого доступа

---

## 🔗 Интеграция с другими сервисами

### Grafana

```yaml
# datasource.yml
apiVersion: 1
datasources:
  - name: AntiBot
    type: json
    url: http://localhost:8080/api
    access: proxy
```

### Discord Bot

```javascript
// Получение статистики
fetch('http://localhost:8080/api/stats')
  .then(r => r.json())
  .then(data => {
    console.log('Blocked:', data.totalBlockedConnections);
  });
```

### Telegram Bot

```python
import requests

response = requests.get('http://localhost:8080/api/status')
data = response.json()
print(f"Players online: {data['playersOnline']}")
```

---

## 📊 API Endpoints

| Endpoint | Метод | Описание |
|----------|-------|----------|
| `/` | GET | Главная страница |
| `/api/stats` | GET | Статистика |
| `/api/status` | GET | Статус системы |
| `/api/blocks` | GET | Заблокированные IP |

### Пример запроса

```bash
# С аутентификацией
curl -u admin:password http://localhost:8080/api/stats

# Без аутентификации
curl http://localhost:8080/api/stats
```

---

## 💡 Советы

1. **Используйте HTTPS** - настройте SSL через Nginx/Apache
2. **Сложные пароли** - минимум 16 символов
3. **Регулярные обновления** - обновляйте плагин
4. **Мониторинг логов** - следите за подозрительной активностью
5. **Backup конфигурации** - делайте резервные копии

---

## 📞 Поддержка

Нужна помощь?
- Discord: [присоединиться](https://dsc.gg/alfheimguide)
- GitHub Issues: [создать issue](#)

---

<p align="center">
  <b>Безопасный мониторинг вашего сервера! 🛡️</b>
</p>
