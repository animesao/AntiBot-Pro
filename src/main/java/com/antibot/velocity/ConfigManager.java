package com.antibot.velocity;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.slf4j.Logger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

public class ConfigManager {

    private final Path dataDirectory;
    private final Logger logger;
    private final Path configFile;

    private int maxConnectionsPerIp = 5;
    private int maxConnectionsPerSecond = 30;
    private int maxAccountsPerIp = 3;
    private int timeWindowSeconds = 60;
    private int blockDurationMinutes = 10;
    private boolean strictModeEnabled = true;
    private boolean nameCheckEnabled = true;
    private boolean allowNumberNames = false;

    private boolean clientCheckEnabled = true;
    private boolean kickCheatClients = true;
    private boolean kickNoBrand = false;
    private int brandCheckTimeoutSeconds = 10;

    private boolean vpnCheckEnabled = true;
    private boolean blockVpn = true;
    private String vpnApiKey = "";

    private boolean geoBlockEnabled = false;
    private boolean geoWhitelistMode = false;
    private List<String> allowedCountries = new ArrayList<>(
        Arrays.asList("RU", "UA", "BY", "KZ")
    );
    private List<String> blockedCountries = new ArrayList<>();

    private String discordWebhookUrl = "";
    private boolean discordEnabled = true;
    private String discordBotName = "AntiBot Pro";
    private String discordAvatarUrl = "";
    private boolean discordMaskIp = true;

    private boolean discordLogJoins = true;
    private boolean discordLogLeaves = false;
    private boolean discordLogCheats = true;
    private boolean discordLogVpn = true;
    private boolean discordLogBots = true;
    private boolean discordLogBlocks = true;
    private boolean discordLogKicks = true;
    private boolean discordLogAttackMode = true;
    private boolean discordLogSuspiciousNames = true;

    private int discordColorJoin = 0x00FF00;
    private int discordColorLeave = 0x808080;
    private int discordColorCheat = 0xFF0000;
    private int discordColorVpn = 0xFFA500;
    private int discordColorBot = 0xFF4500;
    private int discordColorBlock = 0x8B0000;
    private int discordColorKick = 0xDC143C;
    private int discordColorAttack = 0xFF0000;
    private int discordColorSuspicious = 0xFFFF00;

    private List<String> whitelistedIps = new ArrayList<>();
    private List<String> whitelistedPlayers = new ArrayList<>();

    private String blockedMessage = "Вы заблокированы! Попробуйте позже.";
    private String rateLimitMessage =
        "Слишком много подключений! Подождите немного.";
    private String maxAccountsMessage = "Превышен лимит аккаунтов с вашего IP!";
    private String attackModeMessage = "Сервер под защитой. Попробуйте позже.";
    private String invalidNameMessage = "Недопустимый никнейм!";
    private String cheatClientMessage = "Обнаружен запрещенный клиент!";
    private String vpnBlockedMessage = "VPN/Proxy запрещены на этом сервере!";
    private String geoBlockedMessage =
        "Подключение из вашего региона заблокировано!";
    private String noBrandMessage = "Ваш клиент не прошел проверку!";

    // Discord Bot настройки
    private boolean discordBotEnabled = false;
    private String discordBotToken = "";
    private String discordServerName = "Minecraft Server";
    private boolean discordAccountLinkingEnabled = true;
    private boolean discordDmVerificationEnabled = true;
    private boolean discordCommandWhitelistEnabled = false;
    private List<String> discordAllowedGuilds = new ArrayList<>();
    private List<String> discordAllowedRoles = new ArrayList<>();
    private List<String> discordAdminRoles = new ArrayList<>();

    // Настройки привязки аккаунтов
    private boolean accountLinkAutoWhitelist = false;
    private boolean accountLinkDmNotification = true;
    private int accountLinkMaxPerDiscord = 3;
    private boolean accountLinkRequireVerification = false;
    private List<String> accountLinkTrustedRoles = new ArrayList<>();

    // Настройки повторной верификации
    private boolean reverifyOnIpChange = true;
    private boolean reverifyPeriodic = true;
    private int reverifyPeriodHours = 24;

    public ConfigManager(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
        this.configFile = dataDirectory.resolve("config.yml");
    }

    @SuppressWarnings("unchecked")
    public void loadConfig() {
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }

            if (!Files.exists(configFile)) {
                createDefaultConfig();
            }

            Yaml yaml = new Yaml();
            try (InputStream inputStream = Files.newInputStream(configFile)) {
                Map<String, Object> config = yaml.load(inputStream);

                if (config != null) {
                    Map<String, Object> limits = getMap(config, "limits");
                    if (limits != null) {
                        maxConnectionsPerIp = getInt(
                            limits,
                            "max-connections-per-ip",
                            maxConnectionsPerIp
                        );
                        maxConnectionsPerSecond = getInt(
                            limits,
                            "max-connections-per-second",
                            maxConnectionsPerSecond
                        );
                        maxAccountsPerIp = getInt(
                            limits,
                            "max-accounts-per-ip",
                            maxAccountsPerIp
                        );
                        timeWindowSeconds = getInt(
                            limits,
                            "time-window-seconds",
                            timeWindowSeconds
                        );
                        blockDurationMinutes = getInt(
                            limits,
                            "block-duration-minutes",
                            blockDurationMinutes
                        );
                    }

                    Map<String, Object> protection = getMap(
                        config,
                        "protection"
                    );
                    if (protection != null) {
                        strictModeEnabled = getBoolean(
                            protection,
                            "strict-mode",
                            strictModeEnabled
                        );
                        nameCheckEnabled = getBoolean(
                            protection,
                            "name-check",
                            nameCheckEnabled
                        );
                        allowNumberNames = getBoolean(
                            protection,
                            "allow-number-names",
                            allowNumberNames
                        );
                    }

                    Map<String, Object> clientCheck = getMap(
                        config,
                        "client-check"
                    );
                    if (clientCheck != null) {
                        clientCheckEnabled = getBoolean(
                            clientCheck,
                            "enabled",
                            clientCheckEnabled
                        );
                        kickCheatClients = getBoolean(
                            clientCheck,
                            "kick-cheat-clients",
                            kickCheatClients
                        );
                        kickNoBrand = getBoolean(
                            clientCheck,
                            "kick-no-brand",
                            kickNoBrand
                        );
                        brandCheckTimeoutSeconds = getInt(
                            clientCheck,
                            "brand-check-timeout",
                            brandCheckTimeoutSeconds
                        );
                    }

                    Map<String, Object> vpnCheck = getMap(config, "vpn-check");
                    if (vpnCheck != null) {
                        vpnCheckEnabled = getBoolean(
                            vpnCheck,
                            "enabled",
                            vpnCheckEnabled
                        );
                        blockVpn = getBoolean(vpnCheck, "block-vpn", blockVpn);
                        vpnApiKey = getString(vpnCheck, "api-key", vpnApiKey);
                    }

                    Map<String, Object> geoBlock = getMap(config, "geo-block");
                    if (geoBlock != null) {
                        geoBlockEnabled = getBoolean(
                            geoBlock,
                            "enabled",
                            geoBlockEnabled
                        );
                        geoWhitelistMode = getBoolean(
                            geoBlock,
                            "whitelist-mode",
                            geoWhitelistMode
                        );
                        allowedCountries = getStringList(
                            geoBlock,
                            "allowed-countries",
                            allowedCountries
                        );
                        blockedCountries = getStringList(
                            geoBlock,
                            "blocked-countries",
                            blockedCountries
                        );
                    }

                    Map<String, Object> discord = getMap(config, "discord");
                    if (discord != null) {
                        discordWebhookUrl = getString(
                            discord,
                            "webhook-url",
                            discordWebhookUrl
                        );
                        discordEnabled = getBoolean(
                            discord,
                            "enabled",
                            discordEnabled
                        );
                        discordBotName = getString(
                            discord,
                            "bot-name",
                            discordBotName
                        );
                        discordAvatarUrl = getString(
                            discord,
                            "avatar-url",
                            discordAvatarUrl
                        );
                        discordMaskIp = getBoolean(
                            discord,
                            "mask-ip",
                            discordMaskIp
                        );

                        Map<String, Object> events = getMap(
                            discord,
                            "log-events"
                        );
                        if (events != null) {
                            discordLogJoins = getBoolean(
                                events,
                                "player-join",
                                discordLogJoins
                            );
                            discordLogLeaves = getBoolean(
                                events,
                                "player-leave",
                                discordLogLeaves
                            );
                            discordLogCheats = getBoolean(
                                events,
                                "cheat-detection",
                                discordLogCheats
                            );
                            discordLogVpn = getBoolean(
                                events,
                                "vpn-detection",
                                discordLogVpn
                            );
                            discordLogBots = getBoolean(
                                events,
                                "bot-detection",
                                discordLogBots
                            );
                            discordLogBlocks = getBoolean(
                                events,
                                "ip-block",
                                discordLogBlocks
                            );
                            discordLogKicks = getBoolean(
                                events,
                                "player-kick",
                                discordLogKicks
                            );
                            discordLogAttackMode = getBoolean(
                                events,
                                "attack-mode",
                                discordLogAttackMode
                            );
                            discordLogSuspiciousNames = getBoolean(
                                events,
                                "suspicious-name",
                                discordLogSuspiciousNames
                            );
                        }

                        Map<String, Object> colors = getMap(discord, "colors");
                        if (colors != null) {
                            discordColorJoin = parseColor(
                                getString(colors, "join", "#00FF00")
                            );
                            discordColorLeave = parseColor(
                                getString(colors, "leave", "#808080")
                            );
                            discordColorCheat = parseColor(
                                getString(colors, "cheat", "#FF0000")
                            );
                            discordColorVpn = parseColor(
                                getString(colors, "vpn", "#FFA500")
                            );
                            discordColorBot = parseColor(
                                getString(colors, "bot", "#FF4500")
                            );
                            discordColorBlock = parseColor(
                                getString(colors, "block", "#8B0000")
                            );
                            discordColorKick = parseColor(
                                getString(colors, "kick", "#DC143C")
                            );
                            discordColorAttack = parseColor(
                                getString(colors, "attack", "#FF0000")
                            );
                            discordColorSuspicious = parseColor(
                                getString(colors, "suspicious", "#FFFF00")
                            );
                        }
                    }

                    // Discord Bot настройки
                    Map<String, Object> discordBot = getMap(
                        config,
                        "discord-bot"
                    );
                    if (discordBot != null) {
                        discordBotEnabled = getBoolean(
                            discordBot,
                            "enabled",
                            discordBotEnabled
                        );
                        discordBotToken = getString(
                            discordBot,
                            "bot-token",
                            discordBotToken
                        );
                        discordServerName = getString(
                            discordBot,
                            "server-name",
                            discordServerName
                        );
                        discordAccountLinkingEnabled = getBoolean(
                            discordBot,
                            "account-linking-enabled",
                            discordAccountLinkingEnabled
                        );
                        discordDmVerificationEnabled = getBoolean(
                            discordBot,
                            "dm-verification-enabled",
                            discordDmVerificationEnabled
                        );
                        discordCommandWhitelistEnabled = getBoolean(
                            discordBot,
                            "command-whitelist-enabled",
                            discordCommandWhitelistEnabled
                        );
                        discordAllowedGuilds = getStringList(
                            discordBot,
                            "allowed-guilds",
                            discordAllowedGuilds
                        );
                        discordAllowedRoles = getStringList(
                            discordBot,
                            "allowed-roles",
                            discordAllowedRoles
                        );
                        discordAdminRoles = getStringList(
                            discordBot,
                            "admin-roles",
                            discordAdminRoles
                        );
                    }

                    // Настройки привязки аккаунтов
                    Map<String, Object> accountLink = getMap(
                        config,
                        "account-linking"
                    );
                    if (accountLink != null) {
                        accountLinkAutoWhitelist = getBoolean(
                            accountLink,
                            "auto-whitelist",
                            accountLinkAutoWhitelist
                        );
                        accountLinkDmNotification = getBoolean(
                            accountLink,
                            "dm-notification",
                            accountLinkDmNotification
                        );
                        accountLinkMaxPerDiscord = getInt(
                            accountLink,
                            "max-per-discord",
                            accountLinkMaxPerDiscord
                        );
                        accountLinkRequireVerification = getBoolean(
                            accountLink,
                            "require-verification",
                            accountLinkRequireVerification
                        );
                        accountLinkTrustedRoles = getStringList(
                            accountLink,
                            "trusted-roles",
                            accountLinkTrustedRoles
                        );
                        reverifyOnIpChange = getBoolean(
                            accountLink,
                            "reverify-on-ip-change",
                            reverifyOnIpChange
                        );
                        reverifyPeriodic = getBoolean(
                            accountLink,
                            "reverify-periodic",
                            reverifyPeriodic
                        );
                        reverifyPeriodHours = getInt(
                            accountLink,
                            "reverify-period-hours",
                            reverifyPeriodHours
                        );
                    }

                    Map<String, Object> whitelist = getMap(config, "whitelist");
                    if (whitelist != null) {
                        whitelistedIps = getStringList(
                            whitelist,
                            "ips",
                            whitelistedIps
                        );
                        whitelistedPlayers = getStringList(
                            whitelist,
                            "players",
                            whitelistedPlayers
                        );
                    }

                    Map<String, Object> messages = getMap(config, "messages");
                    if (messages != null) {
                        blockedMessage = getString(
                            messages,
                            "blocked",
                            blockedMessage
                        );
                        rateLimitMessage = getString(
                            messages,
                            "rate-limit",
                            rateLimitMessage
                        );
                        maxAccountsMessage = getString(
                            messages,
                            "max-accounts",
                            maxAccountsMessage
                        );
                        attackModeMessage = getString(
                            messages,
                            "attack-mode",
                            attackModeMessage
                        );
                        invalidNameMessage = getString(
                            messages,
                            "invalid-name",
                            invalidNameMessage
                        );
                        cheatClientMessage = getString(
                            messages,
                            "cheat-client",
                            cheatClientMessage
                        );
                        vpnBlockedMessage = getString(
                            messages,
                            "vpn-blocked",
                            vpnBlockedMessage
                        );
                        geoBlockedMessage = getString(
                            messages,
                            "geo-blocked",
                            geoBlockedMessage
                        );
                        noBrandMessage = getString(
                            messages,
                            "no-brand",
                            noBrandMessage
                        );
                    }
                }
            }

            logger.info("Конфигурация загружена успешно!");
        } catch (IOException e) {
            logger.error("Ошибка загрузки конфигурации: {}", e.getMessage());
        }
    }

    private int parseColor(String colorStr) {
        try {
            if (colorStr.startsWith("#")) {
                return Integer.parseInt(colorStr.substring(1), 16);
            } else if (colorStr.startsWith("0x")) {
                return Integer.parseInt(colorStr.substring(2), 16);
            }
            return Integer.parseInt(colorStr);
        } catch (NumberFormatException e) {
            return 0x808080;
        }
    }

    private void createDefaultConfig() throws IOException {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml yaml = new Yaml(options);

        Map<String, Object> config = new LinkedHashMap<>();

        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("max-connections-per-ip", 5);
        limits.put("max-connections-per-second", 30);
        limits.put("max-accounts-per-ip", 3);
        limits.put("time-window-seconds", 60);
        limits.put("block-duration-minutes", 10);
        config.put("limits", limits);

        Map<String, Object> protection = new LinkedHashMap<>();
        protection.put("strict-mode", true);
        protection.put("name-check", true);
        protection.put("allow-number-names", false);
        config.put("protection", protection);

        Map<String, Object> clientCheck = new LinkedHashMap<>();
        clientCheck.put("enabled", true);
        clientCheck.put("kick-cheat-clients", true);
        clientCheck.put("kick-no-brand", false);
        clientCheck.put("brand-check-timeout", 10);
        config.put("client-check", clientCheck);

        Map<String, Object> vpnCheck = new LinkedHashMap<>();
        vpnCheck.put("enabled", true);
        vpnCheck.put("block-vpn", true);
        vpnCheck.put("api-key", "");
        config.put("vpn-check", vpnCheck);

        Map<String, Object> geoBlock = new LinkedHashMap<>();
        geoBlock.put("enabled", false);
        geoBlock.put("whitelist-mode", false);
        geoBlock.put(
            "allowed-countries",
            Arrays.asList("RU", "UA", "BY", "KZ")
        );
        geoBlock.put("blocked-countries", new ArrayList<>());
        config.put("geo-block", geoBlock);

        Map<String, Object> discord = new LinkedHashMap<>();
        discord.put("webhook-url", "");
        discord.put("enabled", true);
        discord.put("bot-name", "AntiBot Pro");
        discord.put("avatar-url", "");
        discord.put("mask-ip", true);

        Map<String, Object> logEvents = new LinkedHashMap<>();
        logEvents.put("player-join", true);
        logEvents.put("player-leave", false);
        logEvents.put("cheat-detection", true);
        logEvents.put("vpn-detection", true);
        logEvents.put("bot-detection", true);
        logEvents.put("ip-block", true);
        logEvents.put("player-kick", true);
        logEvents.put("attack-mode", true);
        logEvents.put("suspicious-name", true);
        discord.put("log-events", logEvents);

        Map<String, Object> colors = new LinkedHashMap<>();
        colors.put("join", "#00FF00");
        colors.put("leave", "#808080");
        colors.put("cheat", "#FF0000");
        colors.put("vpn", "#FFA500");
        colors.put("bot", "#FF4500");
        colors.put("block", "#8B0000");
        colors.put("kick", "#DC143C");
        colors.put("attack", "#FF0000");
        colors.put("suspicious", "#FFFF00");
        discord.put("colors", colors);

        config.put("discord", discord);

        // Discord Bot настройки
        Map<String, Object> discordBot = new LinkedHashMap<>();
        discordBot.put("enabled", false);
        discordBot.put("bot-token", "");
        discordBot.put("server-name", "Minecraft Server");
        discordBot.put("account-linking-enabled", true);
        discordBot.put("dm-verification-enabled", true);
        discordBot.put("command-whitelist-enabled", false);
        discordBot.put("allowed-guilds", new ArrayList<>());
        discordBot.put("allowed-roles", new ArrayList<>());
        discordBot.put("admin-roles", new ArrayList<>());
        config.put("discord-bot", discordBot);

        // Настройки привязки аккаунтов
        Map<String, Object> accountLink = new LinkedHashMap<>();
        accountLink.put("auto-whitelist", false);
        accountLink.put("dm-notification", true);
        accountLink.put("max-per-discord", 3);
        accountLink.put("require-verification", false);
        accountLink.put("trusted-roles", new ArrayList<>());
        accountLink.put("reverify-on-ip-change", true);
        accountLink.put("reverify-periodic", true);
        accountLink.put("reverify-period-hours", 24);
        config.put("account-linking", accountLink);

        Map<String, Object> whitelist = new LinkedHashMap<>();
        whitelist.put("ips", new ArrayList<>());
        whitelist.put("players", new ArrayList<>());
        config.put("whitelist", whitelist);

        Map<String, Object> messages = new LinkedHashMap<>();
        messages.put("blocked", "Вы заблокированы! Попробуйте позже.");
        messages.put(
            "rate-limit",
            "Слишком много подключений! Подождите немного."
        );
        messages.put("max-accounts", "Превышен лимит аккаунтов с вашего IP!");
        messages.put("attack-mode", "Сервер под защитой. Попробуйте позже.");
        messages.put("invalid-name", "Недопустимый никнейм!");
        messages.put("cheat-client", "Обнаружен запрещенный клиент!");
        messages.put("vpn-blocked", "VPN/Proxy запрещены на этом сервере!");
        messages.put(
            "geo-blocked",
            "Подключение из вашего региона заблокировано!"
        );
        messages.put("no-brand", "Ваш клиент не прошел проверку!");
        config.put("messages", messages);

        try (Writer writer = Files.newBufferedWriter(configFile)) {
            writer.write(
                "# ═══════════════════════════════════════════════════════════\n"
            );
            writer.write(
                "# AntiBot Pro v2.0.0 - Продвинутая защита от ботов\n"
            );
            writer.write(
                "# ═══════════════════════════════════════════════════════════\n\n"
            );
            yaml.dump(config, writer);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    private int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    private boolean getBoolean(
        Map<String, Object> map,
        String key,
        boolean defaultValue
    ) {
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }

    private String getString(
        Map<String, Object> map,
        String key,
        String defaultValue
    ) {
        Object value = map.get(key);
        if (value instanceof String) {
            return (String) value;
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private List<String> getStringList(
        Map<String, Object> map,
        String key,
        List<String> defaultValue
    ) {
        Object value = map.get(key);
        if (value instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object item : (List<?>) value) {
                result.add(String.valueOf(item));
            }
            return result;
        }
        return defaultValue;
    }

    public int getMaxConnectionsPerIp() {
        return maxConnectionsPerIp;
    }

    public int getMaxConnectionsPerSecond() {
        return maxConnectionsPerSecond;
    }

    public int getMaxAccountsPerIp() {
        return maxAccountsPerIp;
    }

    public int getTimeWindowSeconds() {
        return timeWindowSeconds;
    }

    public int getBlockDurationMinutes() {
        return blockDurationMinutes;
    }

    public boolean isStrictModeEnabled() {
        return strictModeEnabled;
    }

    public boolean isNameCheckEnabled() {
        return nameCheckEnabled;
    }

    public boolean isAllowNumberNames() {
        return allowNumberNames;
    }

    public boolean isClientCheckEnabled() {
        return clientCheckEnabled;
    }

    public boolean isKickCheatClients() {
        return kickCheatClients;
    }

    public boolean isKickNoBrand() {
        return kickNoBrand;
    }

    public int getBrandCheckTimeoutSeconds() {
        return brandCheckTimeoutSeconds;
    }

    public boolean isVpnCheckEnabled() {
        return vpnCheckEnabled;
    }

    public boolean isBlockVpn() {
        return blockVpn;
    }

    public String getVpnApiKey() {
        return vpnApiKey;
    }

    public boolean isGeoBlockEnabled() {
        return geoBlockEnabled;
    }

    public boolean isGeoWhitelistMode() {
        return geoWhitelistMode;
    }

    public List<String> getAllowedCountries() {
        return allowedCountries;
    }

    public List<String> getBlockedCountries() {
        return blockedCountries;
    }

    public String getDiscordWebhookUrl() {
        return discordWebhookUrl;
    }

    public boolean isDiscordEnabled() {
        return discordEnabled;
    }

    public String getDiscordBotName() {
        return discordBotName;
    }

    public String getDiscordAvatarUrl() {
        return discordAvatarUrl;
    }

    public boolean isDiscordMaskIp() {
        return discordMaskIp;
    }

    public boolean isDiscordLogJoins() {
        return discordLogJoins;
    }

    public boolean isDiscordLogLeaves() {
        return discordLogLeaves;
    }

    public boolean isDiscordLogCheats() {
        return discordLogCheats;
    }

    public boolean isDiscordLogVpn() {
        return discordLogVpn;
    }

    public boolean isDiscordLogBots() {
        return discordLogBots;
    }

    public boolean isDiscordLogBlocks() {
        return discordLogBlocks;
    }

    public boolean isDiscordLogKicks() {
        return discordLogKicks;
    }

    public boolean isDiscordLogAttackMode() {
        return discordLogAttackMode;
    }

    public boolean isDiscordLogSuspiciousNames() {
        return discordLogSuspiciousNames;
    }

    public int getDiscordColorJoin() {
        return discordColorJoin;
    }

    public int getDiscordColorLeave() {
        return discordColorLeave;
    }

    public int getDiscordColorCheat() {
        return discordColorCheat;
    }

    public int getDiscordColorVpn() {
        return discordColorVpn;
    }

    public int getDiscordColorBot() {
        return discordColorBot;
    }

    public int getDiscordColorBlock() {
        return discordColorBlock;
    }

    public int getDiscordColorKick() {
        return discordColorKick;
    }

    public int getDiscordColorAttack() {
        return discordColorAttack;
    }

    public int getDiscordColorSuspicious() {
        return discordColorSuspicious;
    }

    public List<String> getWhitelistedIps() {
        return whitelistedIps;
    }

    public List<String> getWhitelistedPlayers() {
        return whitelistedPlayers;
    }

    public String getBlockedMessage() {
        return blockedMessage;
    }

    public String getRateLimitMessage() {
        return rateLimitMessage;
    }

    public String getMaxAccountsMessage() {
        return maxAccountsMessage;
    }

    public String getAttackModeMessage() {
        return attackModeMessage;
    }

    public String getInvalidNameMessage() {
        return invalidNameMessage;
    }

    public String getCheatClientMessage() {
        return cheatClientMessage;
    }

    public String getVpnBlockedMessage() {
        return vpnBlockedMessage;
    }

    public String getGeoBlockedMessage() {
        return geoBlockedMessage;
    }

    public String getNoBrandMessage() {
        return noBrandMessage;
    }

    // Discord Bot геттеры
    public boolean isDiscordBotEnabled() {
        return discordBotEnabled;
    }

    public String getDiscordBotToken() {
        return discordBotToken;
    }

    public String getDiscordServerName() {
        return discordServerName;
    }

    public boolean isDiscordAccountLinkingEnabled() {
        return discordAccountLinkingEnabled;
    }

    public boolean isDiscordDmVerificationEnabled() {
        return discordDmVerificationEnabled;
    }

    public boolean isDiscordCommandWhitelistEnabled() {
        return discordCommandWhitelistEnabled;
    }

    public List<String> getDiscordAllowedGuilds() {
        return discordAllowedGuilds;
    }

    public List<String> getDiscordAllowedRoles() {
        return discordAllowedRoles;
    }

    public List<String> getDiscordAdminRoles() {
        return discordAdminRoles;
    }

    // Account Linking геттеры
    public boolean isAccountLinkAutoWhitelist() {
        return accountLinkAutoWhitelist;
    }

    public boolean isAccountLinkDmNotification() {
        return accountLinkDmNotification;
    }

    public int getAccountLinkMaxPerDiscord() {
        return accountLinkMaxPerDiscord;
    }

    public boolean isAccountLinkRequireVerification() {
        return accountLinkRequireVerification;
    }

    public List<String> getAccountLinkTrustedRoles() {
        return accountLinkTrustedRoles;
    }

    public boolean isReverifyOnIpChange() {
        return reverifyOnIpChange;
    }

    public boolean isReverifyPeriodic() {
        return reverifyPeriodic;
    }

    public int getReverifyPeriodHours() {
        return reverifyPeriodHours;
    }
}
