# 🌐 Web Dashboard - Быстрая настройка для хостинга

## 📍 Как получить доступ к дашборду на хостинге

### Вариант 1: Локальный доступ (по умолчанию)

```yaml
web-dashboard:
  enabled: true
  port: 8080
  bind-address: "127.0.0.1"
```

**URL:** `http://localhost:8080`  
**Доступ:** Только с сервера (через SSH)

---

### Вариант 2: Удалённый доступ (рекомендуется)

```yaml
web-dashboard:
  enabled: true
  port: 8080
  bind-address: "0.0.0.0"  # ← Слушать на всех интерфейсах
  
  # ОБЯЗАТЕЛЬНО установите пароль!
  username: "admin"
  password: "ваш_сложный_пароль_123"
```

**URL:** `http://ВАШ_IP_СЕРВЕРА:8080`

#### Как узнать IP сервера?

```bash
# Выполните на сервере
curl ifconfig.me
```

Или посмотрите в панели управления хостингом.

#### Примеры URL:

- `http://123.45.67.89:8080` - если IP сервера 123.45.67.89
- `http://mc.example.com:8080` - если есть домен
- `http://192.168.1.100:8080` - для локальной сети

---

### Вариант 3: С доменом (профессионально)

#### Шаг 1: Настройте AntiBot

```yaml
web-dashboard:
  enabled: true
  port: 8080
  bind-address: "127.0.0.1"  # Только локально
```

#### Шаг 2: Установите Nginx

```bash
sudo apt update
sudo apt install nginx certbot python3-certbot-nginx
```

#### Шаг 3: Создайте конфиг Nginx

```bash
sudo nano /etc/nginx/sites-available/antibot
```

Вставьте:

```nginx
server {
    listen 80;
    server_name antibot.example.com;  # ← Ваш домен

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

#### Шаг 4: Активируйте

```bash
sudo ln -s /etc/nginx/sites-available/antibot /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

#### Шаг 5: Установите SSL (HTTPS)

```bash
sudo certbot --nginx -d antibot.example.com
```

**URL:** `https://antibot.example.com`

---

## 🔓 Открытие порта в firewall

### Ubuntu/Debian (UFW)

```bash
sudo ufw allow 8080/tcp
sudo ufw status
```

### CentOS/RHEL (firewalld)

```bash
sudo firewall-cmd --permanent --add-port=8080/tcp
sudo firewall-cmd --reload
```

### Windows Server

```powershell
New-NetFirewallRule -DisplayName "AntiBot Dashboard" -Direction Inbound -LocalPort 8080 -Protocol TCP -Action Allow
```

---

## ✅ Проверка работы

### 1. Проверьте, что дашборд запущен

```bash
netstat -tulpn | grep 8080
```

Должно показать:
```
tcp  0  0  0.0.0.0:8080  0.0.0.0:*  LISTEN  12345/java
```

### 2. Проверьте доступ локально

```bash
curl http://localhost:8080/api/status
```

### 3. Проверьте доступ удалённо

С другого компьютера:
```bash
curl http://ВАШ_IP:8080/api/status
```

Или откройте в браузере:
```
http://ВАШ_IP:8080
```

---

## 🔐 Безопасность

### Обязательно:

1. **Установите пароль**
```yaml
username: "admin"
password: "сложный_пароль_с_цифрами_123"
```

2. **Используйте HTTPS** (через Nginx + Certbot)

3. **Ограничьте доступ по IP** (через firewall)
```bash
# Разрешить только с вашего IP
sudo ufw allow from ВАШ_IP to any port 8080
```

---

## 🎯 Быстрый старт для новичков

### Шаг 1: Откройте config.yml

```bash
nano plugins/antibot/config.yml
```

### Шаг 2: Найдите секцию web-dashboard

### Шаг 3: Измените на:

```yaml
web-dashboard:
  enabled: true
  port: 8080
  bind-address: "0.0.0.0"
  username: "admin"
  password: "мой_пароль_123"
```

### Шаг 4: Сохраните (Ctrl+O, Enter, Ctrl+X)

### Шаг 5: Перезапустите сервер

### Шаг 6: Откройте порт

```bash
sudo ufw allow 8080/tcp
```

### Шаг 7: Узнайте IP

```bash
curl ifconfig.me
```

### Шаг 8: Откройте в браузере

```
http://ВАШ_IP:8080
```

Введите логин `admin` и ваш пароль.

---

## 📱 Доступ с телефона

1. Подключитесь к той же сети (или используйте мобильный интернет)
2. Откройте браузер
3. Введите: `http://ВАШ_IP:8080`
4. Введите логин/пароль
5. Добавьте в закладки

---

## 🐛 Проблемы?

### Не могу подключиться

1. Проверьте firewall: `sudo ufw status`
2. Проверьте порт: `netstat -tulpn | grep 8080`
3. Проверьте логи: `tail -f plugins/antibot/logs/latest.log`

### 401 Unauthorized

1. Проверьте username/password в config.yml
2. Очистите кэш браузера
3. Попробуйте другой браузер

### Медленно загружается

1. Используйте Nginx (Вариант 3)
2. Проверьте нагрузку на сервер: `top`

---

## 📞 Нужна помощь?

- Discord: https://dsc.gg/alfheimguide
- GitHub: Создайте issue

---

<p align="center">
  <b>Готово! Теперь вы можете мониторить сервер из любой точки мира! 🌍</b>
</p>
