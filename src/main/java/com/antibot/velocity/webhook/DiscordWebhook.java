package com.antibot.velocity.webhook;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import java.awt.*;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DiscordWebhook {

    private static final Logger logger = LoggerFactory.getLogger(DiscordWebhook.class);
    private static final Gson gson = new GsonBuilder().create();
    private static final String VERSION = "2.3.0";

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "DiscordWebhook-Worker");
        t.setDaemon(true);
        return t;
    });

    private volatile String webhookUrl;
    private volatile boolean enabled = true;
    private volatile String botName = "AntiBot Pro";
    private volatile String avatarUrl;
    private volatile boolean maskIp = true;

    private volatile boolean logJoins = true;
    private volatile boolean logLeaves;
    private volatile boolean logCheats = true;
    private volatile boolean logVpn = true;
    private volatile boolean logBots = true;
    private volatile boolean logBlocks = true;
    private volatile boolean logKicks = true;
    private volatile boolean logAttackMode = true;
    private volatile boolean logSuspiciousNames = true;

    private int colorJoin = 0x00FF00;
    private int colorLeave = 0x808080;
    private int colorCheat = 0xFF0000;
    private int colorVpn = 0xFFA500;
    private int colorBot = 0xFF4500;
    private int colorBlock = 0x8B0000;
    private int colorKick = 0xDC143C;
    private int colorAttack = 0xFF0000;
    private int colorSuspicious = 0xFFFF00;

    public DiscordWebhook(String webhookUrl) {
        this.webhookUrl = webhookUrl;
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

    public void sendPlayerJoin(String username, String ip, String clientBrand, String country) {
        if (!enabled || !logJoins) return;

        EmbedObject embed = new EmbedObject();
        embed.setTitle("Вход игрока");
        embed.setColor(colorJoin);
        embed.addField("Никнейм", username, true);
        embed.addField("IP", maskIp ? maskIP(ip) : ip, true);
        embed.addField("Страна", country, true);
        embed.addField("Клиент", clientBrand != null ? clientBrand : "Unknown", false);
        embed.setTimestamp(System.currentTimeMillis());
        embed.setFooter("AntiBot Pro v" + VERSION, avatarUrl);

        sendEmbed(embed);
    }

    public void sendPlayerLeave(String username, String ip, long sessionDuration) {
        if (!enabled || !logLeaves) return;

        long minutes = sessionDuration / 60000;
        long seconds = (sessionDuration % 60000) / 1000;

        EmbedObject embed = new EmbedObject();
        embed.setTitle("Выход игрока");
        embed.setColor(colorLeave);
        embed.addField("Никнейм", username, true);
        embed.addField("IP", maskIp ? maskIP(ip) : ip, true);
        embed.addField("Время сессии", String.format("%d мин %d сек", minutes, seconds), true);
        embed.setTimestamp(System.currentTimeMillis());
        embed.setFooter("AntiBot Pro v" + VERSION, avatarUrl);

        sendEmbed(embed);
    }

    public void sendCheatClientDetection(String username, String clientBrand, String ip, String reason) {
        if (!enabled || !logCheats) return;

        EmbedObject embed = new EmbedObject();
        embed.setTitle("Обнаружен читерский клиент");
        embed.setColor(colorCheat);
        embed.addField("Никнейм", username, true);
        embed.addField("IP", maskIp ? maskIP(ip) : ip, true);
        embed.addField("Клиент", clientBrand, false);
        embed.addField("Причина", reason, false);
        embed.setTimestamp(System.currentTimeMillis());
        embed.setFooter("AntiBot Pro v" + VERSION, avatarUrl);

        sendEmbed(embed);
    }

    public void sendVPNDetection(String username, String ip, String isp, String countryCode, int riskScore) {
        if (!enabled || !logVpn) return;

        EmbedObject embed = new EmbedObject();
        embed.setTitle("Обнаружен VPN/Proxy");
        embed.setColor(colorVpn);
        embed.addField("Никнейм", username, true);
        embed.addField("IP", maskIp ? maskIP(ip) : ip, true);
        embed.addField("Страна", countryCode, true);
        embed.addField("Провайдер", isp != null ? isp : "Unknown", false);
        embed.addField("Риск", riskScore + "%", true);
        embed.setTimestamp(System.currentTimeMillis());
        embed.setFooter("AntiBot Pro v" + VERSION, avatarUrl);

        sendEmbed(embed);
    }

    public void sendBotDetection(String username, String ip, int riskScore, String behaviorType) {
        if (!enabled || !logBots) return;

        EmbedObject embed = new EmbedObject();
        embed.setTitle("Обнаружен бот");
        embed.setColor(colorBot);
        embed.addField("Никнейм", username, true);
        embed.addField("IP", maskIp ? maskIP(ip) : ip, true);
        embed.addField("Тип поведения", behaviorType, true);
        embed.addField("Риск", riskScore + "%", true);
        embed.setTimestamp(System.currentTimeMillis());
        embed.setFooter("AntiBot Pro v" + VERSION, avatarUrl);

        sendEmbed(embed);
    }

    public void sendIPBlocked(String ip, String reason, int durationMinutes) {
        if (!enabled || !logBlocks) return;

        EmbedObject embed = new EmbedObject();
        embed.setTitle("IP заблокирован");
        embed.setColor(colorBlock);
        embed.addField("IP", maskIp ? maskIP(ip) : ip, true);
        embed.addField("Длительность", durationMinutes + " мин", true);
        embed.addField("Причина", reason, false);
        embed.setTimestamp(System.currentTimeMillis());
        embed.setFooter("AntiBot Pro v" + VERSION, avatarUrl);

        sendEmbed(embed);
    }

    public void sendPlayerKicked(String username, String ip, String reason) {
        if (!enabled || !logKicks) return;

        EmbedObject embed = new EmbedObject();
        embed.setTitle("Игрок кикнут");
        embed.setColor(colorKick);
        embed.addField("Никнейм", username, true);
        embed.addField("IP", maskIp ? maskIP(ip) : ip, true);
        embed.addField("Причина", reason, false);
        embed.setTimestamp(System.currentTimeMillis());
        embed.setFooter("AntiBot Pro v" + VERSION, avatarUrl);

        sendEmbed(embed);
    }

    public void sendAttackModeAlert(boolean activated, int connectionsPerSecond) {
        if (!enabled || !logAttackMode) return;

        EmbedObject embed = new EmbedObject();
        if (activated) {
            embed.setTitle("РЕЖИМ АТАКИ АКТИВИРОВАН");
            embed.setDescription("Обнаружена бот-атака! Включена усиленная защита.");
        } else {
            embed.setTitle("РЕЖИМ АТАКИ ДЕАКТИВИРОВАН");
            embed.setDescription("Атака завершена. Защита в обычном режиме.");
        }
        embed.setColor(colorAttack);
        embed.addField("Подключений/сек", String.valueOf(connectionsPerSecond), true);
        embed.setTimestamp(System.currentTimeMillis());
        embed.setFooter("AntiBot Pro v" + VERSION, avatarUrl);

        sendEmbed(embed);
    }

    public void sendSuspiciousUsername(String username, String ip, int riskScore, String reasons) {
        if (!enabled || !logSuspiciousNames) return;

        EmbedObject embed = new EmbedObject();
        embed.setTitle("Подозрительный никнейм");
        embed.setColor(colorSuspicious);
        embed.addField("Никнейм", username, true);
        embed.addField("IP", maskIp ? maskIP(ip) : ip, true);
        embed.addField("Риск", riskScore + "%", true);
        embed.addField("Причины", reasons, false);
        embed.setTimestamp(System.currentTimeMillis());
        embed.setFooter("AntiBot Pro v" + VERSION, avatarUrl);

        sendEmbed(embed);
    }

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
        embed.setFooter("AntiBot Pro v" + VERSION, avatarUrl);

        sendEmbed(embed);
    }

    private void sendEmbed(EmbedObject embed) {
        String currentUrl = this.webhookUrl;
        if (currentUrl == null || currentUrl.isEmpty()) {
            logger.warn("Webhook URL не настроен!");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                execute(currentUrl, Collections.singletonList(embed));
            } catch (IOException e) {
                logger.error("Ошибка отправки webhook в Discord: {}", e.getMessage());
            }
        }, executor);
    }

    private void execute(String webhookUrl, List<EmbedObject> embeds) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("username", botName);

        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            json.addProperty("avatar_url", avatarUrl);
        }

        JsonArray embedArray = new JsonArray();
        for (EmbedObject embed : embeds) {
            embedArray.add(embed.toJson());
        }
        json.add("embeds", embedArray);

        URL url = new URL(webhookUrl);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.addRequestProperty("Content-Type", "application/json");
        connection.addRequestProperty("User-Agent", "AntiBot-Pro/" + VERSION);
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");

        try (OutputStream stream = connection.getOutputStream()) {
            stream.write(gson.toJson(json).getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = connection.getResponseCode();
        connection.disconnect();

        if (responseCode != 200 && responseCode != 204) {
            logger.warn("Discord webhook вернул код: {}", responseCode);
        }
    }

    private String maskIP(String ip) {
        if (ip == null) return "Unknown";
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return ip;
        return parts[0] + "." + parts[1] + ".***.***";
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public static class EmbedObject {
        private String title;
        private String description;
        private int color;
        private String footerText;
        private String footerIconUrl;
        private String imageUrl;
        private String thumbnailUrl;
        private String authorName;
        private String authorUrl;
        private String authorIconUrl;
        private Long timestamp;
        private final List<EmbedField> fields = new ArrayList<>();

        public String getTitle() { return title; }
        public EmbedObject setTitle(String title) { this.title = title; return this; }

        public String getDescription() { return description; }
        public EmbedObject setDescription(String description) { this.description = description; return this; }

        public int getColor() { return color; }
        public EmbedObject setColor(int color) { this.color = color; return this; }

        public EmbedObject setFooter(String text, String iconUrl) {
            this.footerText = text;
            this.footerIconUrl = iconUrl;
            return this;
        }

        public EmbedObject setImage(String url) {
            this.imageUrl = url;
            return this;
        }

        public EmbedObject setThumbnail(String url) {
            this.thumbnailUrl = url;
            return this;
        }

        public EmbedObject setAuthor(String name, String url, String iconUrl) {
            this.authorName = name;
            this.authorUrl = url;
            this.authorIconUrl = iconUrl;
            return this;
        }

        public Long getTimestamp() { return timestamp; }
        public EmbedObject setTimestamp(Long timestamp) { this.timestamp = timestamp; return this; }

        public EmbedObject addField(String name, String value, boolean inline) {
            this.fields.add(new EmbedField(name, value, inline));
            return this;
        }

        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            if (title != null) json.addProperty("title", title);
            if (description != null) json.addProperty("description", description);
            json.addProperty("color", color);

            if (footerText != null) {
                JsonObject footer = new JsonObject();
                footer.addProperty("text", footerText);
                if (footerIconUrl != null) footer.addProperty("icon_url", footerIconUrl);
                json.add("footer", footer);
            }

            if (imageUrl != null) {
                JsonObject image = new JsonObject();
                image.addProperty("url", imageUrl);
                json.add("image", image);
            }

            if (thumbnailUrl != null) {
                JsonObject thumbnail = new JsonObject();
                thumbnail.addProperty("url", thumbnailUrl);
                json.add("thumbnail", thumbnail);
            }

            if (authorName != null) {
                JsonObject author = new JsonObject();
                author.addProperty("name", authorName);
                if (authorUrl != null) author.addProperty("url", authorUrl);
                if (authorIconUrl != null) author.addProperty("icon_url", authorIconUrl);
                json.add("author", author);
            }

            if (timestamp != null) {
                json.addProperty("timestamp", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                    .format(new Date(timestamp)));
            }

            if (!fields.isEmpty()) {
                JsonArray fieldsArray = new JsonArray();
                for (EmbedField field : fields) {
                    fieldsArray.add(field.toJson());
                }
                json.add("fields", fieldsArray);
            }

            return json;
        }
    }

    public static class EmbedField {
        private final String name;
        private final String value;
        private final boolean inline;

        public EmbedField(String name, String value, boolean inline) {
            this.name = name;
            this.value = value;
            this.inline = inline;
        }

        public String getName() { return name; }
        public String getValue() { return value; }
        public boolean isInline() { return inline; }

        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("name", name);
            json.addProperty("value", value);
            json.addProperty("inline", inline);
            return json;
        }
    }
}
