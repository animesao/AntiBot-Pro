package com.antibot.velocity.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import java.awt.*;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Discord Webhook для отправки уведомлений в Discord
 */
public class DiscordWebhook {

    private static final Logger logger = LoggerFactory.getLogger(DiscordWebhook.class);
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private String webhookUrl;
    private boolean enabled;
    private String botName;
    private String avatarUrl;
    private boolean maskIp;

    private boolean logJoins;
    private boolean logLeaves;
    private boolean logCheats;
    private boolean logVpn;
    private boolean logBots;
    private boolean logBlocks;
    private boolean logKicks;
    private boolean logAttackMode;
    private boolean logSuspiciousNames;

    private int colorJoin;
    private int colorLeave;
    private int colorCheat;
    private int colorVpn;
    private int colorBot;
    private int colorBlock;
    private int colorKick;
    private int colorAttack;
    private int colorSuspicious;

    public DiscordWebhook(String webhookUrl) {
        this.webhookUrl = webhookUrl;
        this.enabled = true;
        this.botName = "AntiBot Pro";
        this.maskIp = true;
    }

    public void configure(String webhookUrl, boolean enabled, String botName, String avatarUrl, boolean maskIp) {
        this.webhookUrl = webhookUrl;
        this.enabled = enabled;
        this.botName = botName != null ? botName : "AntiBot Pro";
        this.avatarUrl = avatarUrl;
        this.maskIp = maskIp;
    }

    public void configureEvents(boolean joins, boolean leaves, boolean cheats, boolean vpn,
                                boolean bots, boolean blocks, boolean kicks, boolean attackMode,
                                boolean suspiciousNames) {
        this.logJoins = joins;
        this.logLeaves = leaves;
        this.logCheats = cheats;
        this.logVpn = vpn;
        this.logBots = bots;
        this.logBlocks = blocks;
        this.logKicks = kicks;
        this.logAttackMode = attackMode;
        this.logSuspiciousNames = suspiciousNames;
    }

    public void configureColors(int join, int leave, int cheat, int vpn, int bot,
                                int block, int kick, int attack, int suspicious) {
        this.colorJoin = join;
        this.colorLeave = leave;
        this.colorCheat = cheat;
        this.colorVpn = vpn;
        this.colorBot = bot;
        this.colorBlock = block;
        this.colorKick = kick;
        this.colorAttack = attack;
        this.colorSuspicious = suspicious;
    }

    /**
     * Отправить уведомление о входе игрока
     */
    public void sendPlayerJoin(String username, String ip, String clientBrand, String country) {
        if (!enabled || !logJoins) return;

        EmbedObject embed = new EmbedObject();
        embed.setTitle("✅ Вход игрока");
        embed.setColor(colorJoin);

        embed.addField("Никнейм", username, true);
        embed.addField("IP", maskIp ? maskIP(ip) : ip, true);
        embed.addField("Страна", country, true);
        embed.addField("Клиент", clientBrand != null ? clientBrand : "Unknown", false);
        embed.setTimestamp(System.currentTimeMillis());
        embed.setFooter("AntiBot Pro v2.0.0", avatarUrl);

        sendEmbed(embed);
    }

    /**
     * Отправить уведомление о выходе игрока
     */
    public void sendPlayerLeave(String username, String ip, long sessionDuration) {
        if (!enabled || !logLeaves) return;

        long minutes = sessionDuration / 60000;
        long seconds = (sessionDuration % 60000) / 1000;

        EmbedObject embed = new EmbedObject();
        embed.setTitle("❌ Выход игрока");
        embed.setColor(colorLeave);

        embed.addField("Никнейм", username, true);
        embed.addField("IP", maskIp ? maskIP(ip) : ip, true);
        embed.addField("Время сессии", minutes + " мин " + seconds + " сек", true);
        embed.setTimestamp(System.currentTimeMillis());
        embed.setFooter("AntiBot Pro v2.0.0", avatarUrl);

        sendEmbed(embed);
    }

    /**
     * Отправить уведомление об обнаружении читерского клиента
     */
    public void sendCheatClientDetection(String username, String clientBrand, String ip, String reason) {
        if (!enabled || !logCheats) return;

        EmbedObject embed = new EmbedObject();
        embed.setTitle("🚨 Обнаружен читерский клиент");
        embed.setColor(colorCheat);

        embed.addField("Никнейм", username, true);
        embed.addField("IP", maskIp ? maskIP(ip) : ip, true);
        embed.addField("Клиент", clientBrand, false);
        embed.addField("Причина", reason, false);
        embed.setTimestamp(System.currentTimeMillis());
        embed.setFooter("AntiBot Pro v2.0.0", avatarUrl);

        sendEmbed(embed);
    }

    /**
     * Отправить уведомление об обнаружении VPN/Proxy
     */
    public void sendVPNDetection(String username, String ip, String isp, String countryCode, int riskScore) {
        if (!enabled || !logVpn) return;

        EmbedObject embed = new EmbedObject();
        embed.setTitle("🌐 Обнаружен VPN/Proxy");
        embed.setColor(colorVpn);

        embed.addField("Никнейм", username, true);
        embed.addField("IP", maskIp ? maskIP(ip) : ip, true);
        embed.addField("Страна", countryCode, true);
        embed.addField("Провайдер", isp != null ? isp : "Unknown", false);
        embed.addField("Риск", riskScore + "%", true);
        embed.setTimestamp(System.currentTimeMillis());
        embed.setFooter("AntiBot Pro v2.0.0", avatarUrl);

        sendEmbed(embed);
    }

    /**
     * Отправить уведомление об обнаружении бота
     */
    public void sendBotDetection(String username, String ip, int riskScore, String behaviorType) {
        if (!enabled || !logBots) return;

        EmbedObject embed = new EmbedObject();
        embed.setTitle("🤖 Обнаружен бот");
        embed.setColor(colorBot);

        embed.addField("Никнейм", username, true);
        embed.addField("IP", maskIp ? maskIP(ip) : ip, true);
        embed.addField("Тип поведения", behaviorType, true);
        embed.addField("Риск", riskScore + "%", true);
        embed.setTimestamp(System.currentTimeMillis());
        embed.setFooter("AntiBot Pro v2.0.0", avatarUrl);

        sendEmbed(embed);
    }

    /**
     * Отправить уведомление о блокировке IP
     */
    public void sendIPBlocked(String ip, String reason, int durationMinutes) {
        if (!enabled || !logBlocks) return;

        EmbedObject embed = new EmbedObject();
        embed.setTitle("🔒 IP заблокирован");
        embed.setColor(colorBlock);

        embed.addField("IP", maskIp ? maskIP(ip) : ip, true);
        embed.addField("Длительность", durationMinutes + " мин", true);
        embed.addField("Причина", reason, false);
        embed.setTimestamp(System.currentTimeMillis());
        embed.setFooter("AntiBot Pro v2.0.0", avatarUrl);

        sendEmbed(embed);
    }

    /**
     * Отправить уведомление о кике игрока
     */
    public void sendPlayerKicked(String username, String ip, String reason) {
        if (!enabled || !logKicks) return;

        EmbedObject embed = new EmbedObject();
        embed.setTitle("⚠️ Игрок кикнут");
        embed.setColor(colorKick);

        embed.addField("Никнейм", username, true);
        embed.addField("IP", maskIp ? maskIP(ip) : ip, true);
        embed.addField("Причина", reason, false);
        embed.setTimestamp(System.currentTimeMillis());
        embed.setFooter("AntiBot Pro v2.0.0", avatarUrl);

        sendEmbed(embed);
    }

    /**
     * Отправить уведомление о режиме атаки
     */
    public void sendAttackModeAlert(boolean activated, int connectionsPerSecond) {
        if (!enabled || !logAttackMode) return;

        EmbedObject embed = new EmbedObject();
        if (activated) {
            embed.setTitle("🚨 РЕЖИМ АТАКИ АКТИВИРОВАН");
            embed.setDescription("Обнаружена бот-атака! Включена усиленная защита.");
        } else {
            embed.setTitle("✅ РЕЖИМ АТАКИ ДЕАКТИВИРОВАН");
            embed.setDescription("Атака завершена. Защита в обычном режиме.");
        }
        embed.setColor(colorAttack);
        embed.addField("Подключений/сек", String.valueOf(connectionsPerSecond), true);
        embed.setTimestamp(System.currentTimeMillis());
        embed.setFooter("AntiBot Pro v2.0.0", avatarUrl);

        sendEmbed(embed);
    }

    /**
     * Отправить уведомление о подозрительном никнейме
     */
    public void sendSuspiciousUsername(String username, String ip, int riskScore, String reasons) {
        if (!enabled || !logSuspiciousNames) return;

        EmbedObject embed = new EmbedObject();
        embed.setTitle("⚠️ Подозрительный никнейм");
        embed.setColor(colorSuspicious);

        embed.addField("Никнейм", username, true);
        embed.addField("IP", maskIp ? maskIP(ip) : ip, true);
        embed.addField("Риск", riskScore + "%", true);
        embed.addField("Причины", reasons, false);
        embed.setTimestamp(System.currentTimeMillis());
        embed.setFooter("AntiBot Pro v2.0.0", avatarUrl);

        sendEmbed(embed);
    }

    /**
     * Отправить кастомное сообщение
     */
    public void sendCustomMessage(String title, String description, int color, Map<String, String> fields) {
        if (!enabled) return;

        EmbedObject embed = new EmbedObject();
        embed.setTitle(title);
        embed.setDescription(description);
        embed.setColor(color);

        if (fields != null) {
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                embed.addField(entry.getKey(), entry.getValue(), false);
            }
        }

        embed.setTimestamp(System.currentTimeMillis());
        embed.setFooter("AntiBot Pro v2.0.0", avatarUrl);

        sendEmbed(embed);
    }

    /**
     * Отправить Embed в Discord (асинхронно)
     */
    private void sendEmbed(EmbedObject embed) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            logger.warn("Webhook URL не настроен!");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                execute(webhookUrl, Collections.singletonList(embed));
            } catch (IOException e) {
                logger.error("Ошибка отправки webhook в Discord: {}", e.getMessage());
            }
        }, executor);
    }

    /**
     * Выполнить HTTP запрос к Discord webhook
     */
    private void execute(String webhookUrl, List<EmbedObject> embeds) throws IOException {
        JSONObject json = new JSONObject();
        json.put("username", botName);

        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            json.put("avatar_url", avatarUrl);
        }

        List<JSONObject> embedObjects = new ArrayList<>();
        for (EmbedObject embed : embeds) {
            JSONObject jsonEmbed = new JSONObject();

            jsonEmbed.put("title", embed.getTitle());
            jsonEmbed.put("description", embed.getDescription());
            jsonEmbed.put("color", embed.getColor());

            if (embed.getFooter() != null) {
                JSONObject footer = new JSONObject();
                footer.put("text", embed.getFooter().getText());
                if (embed.getFooter().getIconUrl() != null) {
                    footer.put("icon_url", embed.getFooter().getIconUrl());
                }
                jsonEmbed.put("footer", footer);
            }

            if (embed.getImage() != null) {
                JSONObject image = new JSONObject();
                image.put("url", embed.getImage().getUrl());
                jsonEmbed.put("image", image);
            }

            if (embed.getThumbnail() != null) {
                JSONObject thumbnail = new JSONObject();
                thumbnail.put("url", embed.getThumbnail().getUrl());
                jsonEmbed.put("thumbnail", thumbnail);
            }

            if (embed.getAuthor() != null) {
                JSONObject author = new JSONObject();
                author.put("name", embed.getAuthor().getName());
                if (embed.getAuthor().getUrl() != null) {
                    author.put("url", embed.getAuthor().getUrl());
                }
                if (embed.getAuthor().getIconUrl() != null) {
                    author.put("icon_url", embed.getAuthor().getIconUrl());
                }
                jsonEmbed.put("author", author);
            }

            if (embed.getTimestamp() != null) {
                jsonEmbed.put("timestamp", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                    .format(new java.util.Date(embed.getTimestamp())));
            }

            List<JSONObject> fields = new ArrayList<>();
            for (EmbedObject.EmbedField field : embed.getFields()) {
                JSONObject jsonField = new JSONObject();
                jsonField.put("name", field.getName());
                jsonField.put("value", field.getValue());
                jsonField.put("inline", field.isInline());
                fields.add(jsonField);
            }
            jsonEmbed.put("fields", fields.toArray());

            embedObjects.add(jsonEmbed);
        }

        json.put("embeds", embedObjects.toArray());

        URL url = new URL(webhookUrl);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.addRequestProperty("Content-Type", "application/json");
        connection.addRequestProperty("User-Agent", "AntiBot-Pro/2.0.0");
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");

        try (OutputStream stream = connection.getOutputStream()) {
            stream.write(json.toString().getBytes(StandardCharsets.UTF_8));
        }

        connection.getInputStream().close();
        connection.disconnect();
    }

    /**
     * Замаскировать IP адрес
     */
    private String maskIP(String ip) {
        if (ip == null) return "Unknown";
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return ip;
        return parts[0] + "." + parts[1] + ".***.***";
    }

    public void shutdown() {
        executor.shutdown();
    }

    // Внутренние классы для JSON и Embed
    public static class EmbedObject {
        private String title;
        private String description;
        private int color;
        private Footer footer;
        private Image image;
        private Image thumbnail;
        private Author author;
        private Long timestamp;
        private final List<EmbedField> fields = new ArrayList<>();

        public String getTitle() { return title; }
        public EmbedObject setTitle(String title) { this.title = title; return this; }

        public String getDescription() { return description; }
        public EmbedObject setDescription(String description) { this.description = description; return this; }

        public int getColor() { return color; }
        public EmbedObject setColor(int color) { this.color = color; return this; }

        public Footer getFooter() { return footer; }
        public EmbedObject setFooter(String text, String iconUrl) {
            this.footer = new Footer(text, iconUrl);
            return this;
        }

        public Image getImage() { return image; }
        public EmbedObject setImage(String url) {
            this.image = new Image(url);
            return this;
        }

        public Image getThumbnail() { return thumbnail; }
        public EmbedObject setThumbnail(String url) {
            this.thumbnail = new Image(url);
            return this;
        }

        public Author getAuthor() { return author; }
        public EmbedObject setAuthor(String name, String url, String iconUrl) {
            this.author = new Author(name, url, iconUrl);
            return this;
        }

        public Long getTimestamp() { return timestamp; }
        public EmbedObject setTimestamp(Long timestamp) { this.timestamp = timestamp; return this; }

        public List<EmbedField> getFields() { return fields; }
        public EmbedObject addField(String name, String value, boolean inline) {
            this.fields.add(new EmbedField(name, value, inline));
            return this;
        }

        public static class Footer {
            private final String text;
            private final String iconUrl;
            public Footer(String text, String iconUrl) { this.text = text; this.iconUrl = iconUrl; }
            public String getText() { return text; }
            public String getIconUrl() { return iconUrl; }
        }

        public static class Image {
            private final String url;
            public Image(String url) { this.url = url; }
            public String getUrl() { return url; }
        }

        public static class Author {
            private final String name;
            private final String url;
            private final String iconUrl;
            public Author(String name, String url, String iconUrl) {
                this.name = name; this.url = url; this.iconUrl = iconUrl;
            }
            public String getName() { return name; }
            public String getUrl() { return url; }
            public String getIconUrl() { return iconUrl; }
        }

        public static class EmbedField {
            private final String name;
            private final String value;
            private final boolean inline;
            public EmbedField(String name, String value, boolean inline) {
                this.name = name; this.value = value; this.inline = inline;
            }
            public String getName() { return name; }
            public String getValue() { return value; }
            public boolean isInline() { return inline; }
        }
    }

    // Простая JSON реализентация
    private static class JSONObject {
        private final Map<String, Object> map = new LinkedHashMap<>();

        public void put(String key, Object value) {
            map.put(key, value);
        }

        public Object get(String key) {
            return map.get(key);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(escape(entry.getKey())).append("\":");
                sb.append(valueToString(entry.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }

        private String valueToString(Object value) {
            if (value == null) return "null";
            if (value instanceof String) return "\"" + escape((String) value) + "\"";
            if (value instanceof Number) return value.toString();
            if (value instanceof Boolean) return value.toString();
            if (value.getClass().isArray()) {
                StringBuilder sb = new StringBuilder("[");
                int length = Array.getLength(value);
                for (int i = 0; i < length; i++) {
                    if (i > 0) sb.append(",");
                    sb.append(valueToString(Array.get(value, i)));
                }
                sb.append("]");
                return sb.toString();
            }
            if (value instanceof Collection) {
                StringBuilder sb = new StringBuilder("[");
                boolean first = true;
                for (Object item : (Collection<?>) value) {
                    if (!first) sb.append(",");
                    sb.append(valueToString(item));
                    first = false;
                }
                sb.append("]");
                return sb.toString();
            }
            if (value instanceof Map) {
                StringBuilder sb = new StringBuilder("{");
                boolean first = true;
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    if (!first) sb.append(",");
                    sb.append("\"").append(escape(entry.getKey().toString())).append("\":");
                    sb.append(valueToString(entry.getValue()));
                    first = false;
                }
                sb.append("}");
                return sb.toString();
            }
            return "\"" + escape(value.toString()) + "\"";
        }

        private String escape(String str) {
            return str.replace("\\", "\\\\")
                      .replace("\"", "\\\"")
                      .replace("\n", "\\n")
                      .replace("\r", "\\r")
                      .replace("\t", "\\t");
        }
    }
}
