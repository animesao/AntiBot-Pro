package com.antibot.velocity.account;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AccountLinkManager {

    private static final Logger logger = LoggerFactory.getLogger(AccountLinkManager.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final Path dataDirectory;
    private final Path accountsFile;

    private final Map<String, String> minecraftToDiscord = new ConcurrentHashMap<>();
    private final Map<String, String> discordToMinecraft = new ConcurrentHashMap<>();
    private final Map<String, String> ipToMinecraft = new ConcurrentHashMap<>();
    private final Map<String, Long> discordVerified = new ConcurrentHashMap<>();
    private final Map<String, Long> discordLinkTime = new ConcurrentHashMap<>();
    private final Map<String, Long> pendingTwoFactorAuth = new ConcurrentHashMap<>();
    private final Map<String, String> pendingTwoFactorUsers = new ConcurrentHashMap<>();
    private final Map<String, String> playerLastKnownIP = new ConcurrentHashMap<>();
    private final Map<String, Long> playerLastVerified = new ConcurrentHashMap<>();

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

    public void save() {
        try {
            String json = toJson();
            Files.writeString(accountsFile, json);
            logger.debug("Привязанные аккаунты сохранены");
        } catch (IOException e) {
            logger.error("Ошибка сохранения привязанных аккаунтов: {}", e.getMessage());
        }
    }

    public boolean linkAccount(String minecraftNickname, String discordId, String ip) {
        long linkedCount = minecraftToDiscord.values().stream()
            .filter(discordId::equals)
            .count();

        if (linkedCount >= maxAccountsPerDiscord) {
            logger.warn("Discord {} достиг лимита привязанных аккаунтов ({})", discordId, maxAccountsPerDiscord);
            return false;
        }

        if (minecraftToDiscord.containsKey(minecraftNickname.toLowerCase())) {
            logger.warn("Аккаунт {} уже привязан к другому Discord", minecraftNickname);
            return false;
        }

        String existingByIp = ipToMinecraft.get(ip);
        if (existingByIp != null && !existingByIp.equalsIgnoreCase(minecraftNickname)) {
            logger.warn("С IP {} уже привязан аккаунт {}", ip, existingByIp);
        }

        minecraftToDiscord.put(minecraftNickname.toLowerCase(), discordId);
        discordToMinecraft.put(discordId, minecraftNickname);
        ipToMinecraft.put(ip, minecraftNickname);
        discordLinkTime.put(discordId, System.currentTimeMillis());

        save();
        logger.info("Аккаунт {} привязан к Discord {}", minecraftNickname, discordId);

        return true;
    }

    public boolean unlinkAccount(String discordId) {
        String nickname = discordToMinecraft.remove(discordId);
        if (nickname != null) {
            minecraftToDiscord.remove(nickname.toLowerCase());
            discordLinkTime.remove(discordId);
            discordVerified.remove(discordId);
            ipToMinecraft.entrySet().removeIf(entry -> entry.getValue().equalsIgnoreCase(nickname));
            save();
            logger.info("Аккаунт {} отвязан от Discord {}", nickname, discordId);
            return true;
        }
        return false;
    }

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

    public String getDiscordId(String minecraftNickname) {
        return minecraftToDiscord.get(minecraftNickname.toLowerCase());
    }

    public String getNickname(String discordId) {
        return discordToMinecraft.get(discordId);
    }

    public String getLinkedByIP(String ip) {
        return ipToMinecraft.get(ip);
    }

    public boolean isDiscordVerified(String discordId) {
        return discordVerified.containsKey(discordId);
    }

    public void markDiscordVerified(String discordId) {
        discordVerified.put(discordId, System.currentTimeMillis());
        save();
    }

    public Long getVerificationTime(String discordId) {
        return discordVerified.get(discordId);
    }

    public Long getLinkTime(String discordId) {
        return discordLinkTime.get(discordId);
    }

    public void requestTwoFactorAuth(String playerName, String discordId) {
        pendingTwoFactorAuth.put(discordId, System.currentTimeMillis());
        pendingTwoFactorUsers.put(playerName.toLowerCase(), discordId);
    }

    public boolean isTwoFactorPending(String playerName) {
        String discordId = pendingTwoFactorUsers.get(playerName.toLowerCase());
        if (discordId == null) return false;
        
        Long requestTime = pendingTwoFactorAuth.get(discordId);
        if (requestTime == null) return false;
        
        if (System.currentTimeMillis() - requestTime > 300000) {
            pendingTwoFactorAuth.remove(discordId);
            pendingTwoFactorUsers.remove(playerName.toLowerCase());
            return false;
        }
        
        return true;
    }

    public void completeTwoFactorAuth(String playerName) {
        String discordId = pendingTwoFactorUsers.remove(playerName.toLowerCase());
        if (discordId != null) {
            pendingTwoFactorAuth.remove(discordId);
        }
    }

    public void updatePlayerIP(String playerName, String ip) {
        String normalizedName = playerName.toLowerCase();
        playerLastKnownIP.put(normalizedName, ip);
    }

    public String getPlayerLastIP(String playerName) {
        return playerLastKnownIP.get(playerName.toLowerCase());
    }

    public void updateLastVerified(String playerName) {
        playerLastVerified.put(playerName.toLowerCase(), System.currentTimeMillis());
    }

    public void markPlayerVerified(String playerName) {
        playerLastVerified.put(playerName.toLowerCase(), System.currentTimeMillis());
    }

    public Long getLastVerifiedTime(String playerName) {
        return playerLastVerified.get(playerName.toLowerCase());
    }

    public boolean needsPeriodicReverify(String playerName, int periodHours) {
        Long lastVerified = playerLastVerified.get(playerName.toLowerCase());
        if (lastVerified == null) return true;
        
        long periodMs = periodHours * 60L * 60L * 1000L;
        return System.currentTimeMillis() - lastVerified > periodMs;
    }

    public boolean hasTrustedRole(String discordId) {
        return trustedDiscordRoles.contains(discordId);
    }

    public void addTrustedRole(String roleId) {
        trustedDiscordRoles.add(roleId);
    }

    public void removeTrustedRole(String roleId) {
        trustedDiscordRoles.remove(roleId);
    }

    public Map<String, String> getAllLinks() {
        return new HashMap<>(minecraftToDiscord);
    }

    public int getLinkedAccountsCount() {
        return minecraftToDiscord.size();
    }

    public boolean isAutoWhitelistEnabled() {
        return autoWhitelistEnabled;
    }

    public void configure(boolean autoWhitelistEnabled, boolean dmNotificationEnabled,
                          int maxAccountsPerDiscord, boolean requireVerification,
                          Set<String> trustedDiscordRoles) {
        this.autoWhitelistEnabled = autoWhitelistEnabled;
        this.dmNotificationEnabled = dmNotificationEnabled;
        this.maxAccountsPerDiscord = maxAccountsPerDiscord;
        this.requireVerification = requireVerification;
        this.trustedDiscordRoles = trustedDiscordRoles != null ? trustedDiscordRoles : new HashSet<>();
    }

    private void parseJson(String json) {
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root == null) return;

            parseStringMap(root, "minecraftToDiscord", minecraftToDiscord);
            parseStringMap(root, "discordToMinecraft", discordToMinecraft);
            parseStringMap(root, "ipToMinecraft", ipToMinecraft);
            parseLongMap(root, "discordVerified", discordVerified);
            parseLongMap(root, "discordLinkTime", discordLinkTime);

        } catch (JsonSyntaxException e) {
            logger.error("Ошибка парсинга JSON: {}", e.getMessage());
        }
    }

    private void parseStringMap(JsonObject root, String key, Map<String, String> target) {
        if (root.has(key) && root.get(key).isJsonObject()) {
            root.getAsJsonObject(key).entrySet().forEach(entry ->
                target.put(entry.getKey(), entry.getValue().getAsString())
            );
        }
    }

    private void parseLongMap(JsonObject root, String key, Map<String, Long> target) {
        if (root.has(key) && root.get(key).isJsonObject()) {
            root.getAsJsonObject(key).entrySet().forEach(entry -> {
                try {
                    target.put(entry.getKey(), entry.getValue().getAsLong());
                } catch (NumberFormatException e) {
                    logger.warn("Не удалось распарсить число для {}: {}", entry.getKey(), entry.getValue());
                }
            });
        }
    }

    private String toJson() {
        JsonObject root = new JsonObject();

        root.add("minecraftToDiscord", gson.toJsonTree(minecraftToDiscord).getAsJsonObject());
        root.add("discordToMinecraft", gson.toJsonTree(discordToMinecraft).getAsJsonObject());
        root.add("ipToMinecraft", gson.toJsonTree(ipToMinecraft).getAsJsonObject());
        root.add("discordVerified", gson.toJsonTree(discordVerified).getAsJsonObject());
        root.add("discordLinkTime", gson.toJsonTree(discordLinkTime).getAsJsonObject());

        return gson.toJson(root);
    }

    public void cleanup() {
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
