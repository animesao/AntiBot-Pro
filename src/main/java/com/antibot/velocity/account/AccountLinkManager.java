package com.antibot.velocity.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер привязки аккаунтов Minecraft <-> Discord
 * Хранение данных в JSON файле
 */
public class AccountLinkManager {

    private static final Logger logger = LoggerFactory.getLogger(AccountLinkManager.class);

    private final Path dataDirectory;
    private final Path accountsFile;

    // Minecraft Nickname -> Discord ID
    private final Map<String, String> minecraftToDiscord = new ConcurrentHashMap<>();

    // Discord ID -> Minecraft Nickname
    private final Map<String, String> discordToMinecraft = new ConcurrentHashMap<>();

    // IP -> Minecraft Nickname (последний привязанный)
    private final Map<String, String> ipToMinecraft = new ConcurrentHashMap<>();

    // Discord ID -> Время верификации
    private final Map<String, Long> discordVerified = new ConcurrentHashMap<>();

    // Discord ID -> Время последней привязки
    private final Map<String, Long> discordLinkTime = new ConcurrentHashMap<>();

    // Настройки
    private boolean autoWhitelistEnabled;
    private boolean dmNotificationEnabled;
    private int maxAccountsPerDiscord;
    private boolean requireVerification;
    private Set<String> trustedDiscordRoles;

    public AccountLinkManager(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
        this.accountsFile = dataDirectory.resolve("linked_accounts.json");
        this.autoWhitelistEnabled = false;
        this.dmNotificationEnabled = true;
        this.maxAccountsPerDiscord = 3;
        this.requireVerification = false;
        this.trustedDiscordRoles = new HashSet<>();
    }

    /**
     * Загрузка привязанных аккаунтов из файла
     */
    public void load() {
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }

            if (Files.exists(accountsFile)) {
                String json = Files.readString(accountsFile);
                parseJson(json);
                logger.info("Загружено {} привязанных аккаунтов", minecraftToDiscord.size());
            } else {
                logger.info("Файл привязанных аккаунтов не найден, создаётся новый");
            }
        } catch (IOException e) {
            logger.error("Ошибка загрузки привязанных аккаунтов: {}", e.getMessage());
        }
    }

    /**
     * Сохранение привязанных аккаунтов в файл
     */
    public void save() {
        try {
            String json = toJson();
            Files.writeString(accountsFile, json);
            logger.debug("Привязанные аккаунты сохранены");
        } catch (IOException e) {
            logger.error("Ошибка сохранения привязанных аккаунтов: {}", e.getMessage());
        }
    }

    /**
     * Привязка аккаунта Minecraft к Discord
     */
    public boolean linkAccount(String minecraftNickname, String discordId, String ip) {
        // Проверка лимита аккаунтов на Discord
        long linkedCount = discordToMinecraft.values().stream()
            .filter(nick -> getDiscordId(nick) != null && getDiscordId(nick).equals(discordId))
            .count();

        if (linkedCount >= maxAccountsPerDiscord) {
            logger.warn("Discord {} достиг лимита привязанных аккаунтов ({})", discordId, maxAccountsPerDiscord);
            return false;
        }

        // Проверка уже привязанного аккаунта
        if (minecraftToDiscord.containsKey(minecraftNickname.toLowerCase())) {
            logger.warn("Аккаунт {} уже привязан к другому Discord", minecraftNickname);
            return false;
        }

        // Проверка привязки по IP
        String existingByIp = ipToMinecraft.get(ip);
        if (existingByIp != null && !existingByIp.equalsIgnoreCase(minecraftNickname)) {
            logger.warn("С IP {} уже привязан аккаунт {}", ip, existingByIp);
        }

        // Привязка
        minecraftToDiscord.put(minecraftNickname.toLowerCase(), discordId);
        discordToMinecraft.put(discordId, minecraftNickname);
        ipToMinecraft.put(ip, minecraftNickname);
        discordLinkTime.put(discordId, System.currentTimeMillis());

        save();
        logger.info("Аккаунт {} привязан к Discord {}", minecraftNickname, discordId);

        return true;
    }

    /**
     * Отвязка аккаунта по Discord ID
     */
    public boolean unlinkAccount(String discordId) {
        String nickname = discordToMinecraft.remove(discordId);
        if (nickname != null) {
            minecraftToDiscord.remove(nickname.toLowerCase());
            discordLinkTime.remove(discordId);
            discordVerified.remove(discordId);

            // Очистка IP маппинга
            ipToMinecraft.entrySet().removeIf(entry -> entry.getValue().equalsIgnoreCase(nickname));

            save();
            logger.info("Аккаунт {} отвязан от Discord {}", nickname, discordId);
            return true;
        }
        return false;
    }

    /**
     * Отвязка аккаунта по Minecraft нику
     */
    public boolean unlinkByNickname(String nickname) {
        String discordId = minecraftToDiscord.remove(nickname.toLowerCase());
        if (discordId != null) {
            discordToMinecraft.remove(discordId);
            discordLinkTime.remove(discordId);
            discordVerified.remove(discordId);
            save();
            logger.info("Аккаунт {} отвязан от Discord {}", nickname, discordId);
            return true;
        }
        return false;
    }

    /**
     * Получить Discord ID по Minecraft нику
     */
    public String getDiscordId(String minecraftNickname) {
        return minecraftToDiscord.get(minecraftNickname.toLowerCase());
    }

    /**
     * Получить Minecraft ник по Discord ID
     */
    public String getNickname(String discordId) {
        return discordToMinecraft.get(discordId);
    }

    /**
     * Получить привязанный аккаунт по IP
     */
    public String getLinkedByIP(String ip) {
        return ipToMinecraft.get(ip);
    }

    /**
     * Проверка верификации Discord
     */
    public boolean isDiscordVerified(String discordId) {
        return discordVerified.containsKey(discordId);
    }

    /**
     * Отметить Discord как верифицированный
     */
    public void markDiscordVerified(String discordId) {
        discordVerified.put(discordId, System.currentTimeMillis());
        save();
    }

    /**
     * Получить время верификации Discord
     */
    public Long getVerificationTime(String discordId) {
        return discordVerified.get(discordId);
    }

    /**
     * Получить время привязки Discord
     */
    public Long getLinkTime(String discordId) {
        return discordLinkTime.get(discordId);
    }

    /**
     * Проверка доверенной роли Discord
     */
    public boolean hasTrustedRole(String discordId) {
        return trustedDiscordRoles.contains(discordId);
    }

    /**
     * Добавить доверенную роль
     */
    public void addTrustedRole(String roleId) {
        trustedDiscordRoles.add(roleId);
    }

    /**
     * Удалить доверенную роль
     */
    public void removeTrustedRole(String roleId) {
        trustedDiscordRoles.remove(roleId);
    }

    /**
     * Получить все привязанные аккаунты
     */
    public Map<String, String> getAllLinks() {
        return new HashMap<>(minecraftToDiscord);
    }

    /**
     * Получить количество привязанных аккаунтов
     */
    public int getLinkedAccountsCount() {
        return minecraftToDiscord.size();
    }

    /**
     * Проверка авто-whitelist
     */
    public boolean isAutoWhitelistEnabled() {
        return autoWhitelistEnabled;
    }

    /**
     * Настройка менеджера
     */
    public void configure(boolean autoWhitelistEnabled, boolean dmNotificationEnabled,
                          int maxAccountsPerDiscord, boolean requireVerification,
                          Set<String> trustedDiscordRoles) {
        this.autoWhitelistEnabled = autoWhitelistEnabled;
        this.dmNotificationEnabled = dmNotificationEnabled;
        this.maxAccountsPerDiscord = maxAccountsPerDiscord;
        this.requireVerification = requireVerification;
        this.trustedDiscordRoles = trustedDiscordRoles != null ? trustedDiscordRoles : new HashSet<>();
    }

    /**
     * Парсинг JSON
     */
    @SuppressWarnings("unchecked")
    private void parseJson(String json) {
        try {
            // Простой парсинг JSON без внешних библиотек
            json = json.trim();
            if (json.startsWith("{")) {
                json = json.substring(1);
            }
            if (json.endsWith("}")) {
                json = json.substring(0, json.length() - 1);
            }

            // Разбор секций
            String[] sections = json.split("\"");
            Map<String, Object> data = new HashMap<>();
            String currentKey = null;

            for (int i = 0; i < sections.length; i++) {
                String section = sections[i].trim();
                if (section.isEmpty() || section.equals(":") || section.equals(",")) continue;

                if (currentKey == null) {
                    currentKey = section;
                } else {
                    if (section.startsWith("{") || section.startsWith("[")) {
                        // Вложенный объект
                        StringBuilder nested = new StringBuilder(section);
                        while (i + 1 < sections.length) {
                            i++;
                            String next = sections[i];
                            nested.append("\"").append(next);
                            if (next.endsWith("}") || next.endsWith("]")) break;
                        }
                        data.put(currentKey, nested.toString());
                    } else {
                        data.put(currentKey, section.replace("\"", "").trim());
                    }
                    currentKey = null;
                }
            }

            // Парсинг minecraftToDiscord
            Object mcToDiscord = data.get("minecraftToDiscord");
            if (mcToDiscord instanceof String) {
                parseMap((String) mcToDiscord, minecraftToDiscord);
            }

            // Парсинг discordToMinecraft
            Object discordToMc = data.get("discordToMinecraft");
            if (discordToMc instanceof String) {
                parseMap((String) discordToMc, discordToMinecraft);
            }

            // Парсинг ipToMinecraft
            Object ipToMc = data.get("ipToMinecraft");
            if (ipToMc instanceof String) {
                parseMap((String) ipToMc, ipToMinecraft);
            }

            // Парсинг discordVerified
            Object verified = data.get("discordVerified");
            if (verified instanceof String) {
                parseLongMap((String) verified, discordVerified);
            }

            // Парсинг discordLinkTime
            Object linkTimes = data.get("discordLinkTime");
            if (linkTimes instanceof String) {
                parseLongMap((String) linkTimes, discordLinkTime);
            }

        } catch (Exception e) {
            logger.error("Ошибка парсинга JSON: {}", e.getMessage());
        }
    }

    private void parseMap(String json, Map<String, String> map) {
        try {
            json = json.trim();
            if (json.startsWith("{")) json = json.substring(1);
            if (json.endsWith("}")) json = json.substring(0, json.length() - 1);

            String[] entries = json.split(",");
            for (String entry : entries) {
                entry = entry.trim();
                if (entry.isEmpty()) continue;

                String[] parts = entry.split(":");
                if (parts.length >= 2) {
                    String key = parts[0].trim().replace("\"", "");
                    String value = parts[1].trim().replace("\"", "");
                    map.put(key, value);
                }
            }
        } catch (Exception e) {
            logger.error("Ошибка парсинга map: {}", e.getMessage());
        }
    }

    private void parseLongMap(String json, Map<String, Long> map) {
        try {
            json = json.trim();
            if (json.startsWith("{")) json = json.substring(1);
            if (json.endsWith("}")) json = json.substring(0, json.length() - 1);

            String[] entries = json.split(",");
            for (String entry : entries) {
                entry = entry.trim();
                if (entry.isEmpty()) continue;

                String[] parts = entry.split(":");
                if (parts.length >= 2) {
                    String key = parts[0].trim().replace("\"", "");
                    String value = parts[1].trim().replace("\"", "");
                    try {
                        map.put(key, Long.parseLong(value));
                    } catch (NumberFormatException e) {
                        // Игнор
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Ошибка парсинга long map: {}", e.getMessage());
        }
    }

    /**
     * Конвертация в JSON
     */
    private String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        sb.append("\"minecraftToDiscord\":").append(mapToJson(minecraftToDiscord)).append(",");
        sb.append("\"discordToMinecraft\":").append(mapToJson(discordToMinecraft)).append(",");
        sb.append("\"ipToMinecraft\":").append(mapToJson(ipToMinecraft)).append(",");
        sb.append("\"discordVerified\":").append(longMapToJson(discordVerified)).append(",");
        sb.append("\"discordLinkTime\":").append(longMapToJson(discordLinkTime));

        sb.append("}");
        return sb.toString();
    }

    private String mapToJson(Map<String, String> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":\"")
              .append(escapeJson(entry.getValue())).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String longMapToJson(Map<String, Long> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Long> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":")
              .append(entry.getValue());
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    /**
     * Очистка старых данных
     */
    public void cleanup() {
        // Очистка непроверенных аккаунтов старше 24 часов
        long cutoff = System.currentTimeMillis() - (24 * 60 * 60 * 1000);

        List<String> toRemove = new ArrayList<>();
        discordVerified.entrySet().removeIf(entry -> {
            if (entry.getValue() < cutoff) {
                toRemove.add(entry.getKey());
                return true;
            }
            return false;
        });

        for (String discordId : toRemove) {
            String nickname = discordToMinecraft.get(discordId);
            if (nickname != null) {
                minecraftToDiscord.remove(nickname.toLowerCase());
                discordToMinecraft.remove(discordId);
            }
        }

        save();
    }
}
