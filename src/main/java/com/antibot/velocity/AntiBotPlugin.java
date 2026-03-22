package com.antibot.velocity;

import com.antibot.velocity.account.AccountLinkManager;
import com.antibot.velocity.detection.*;
import com.antibot.velocity.discord.DiscordBot;
import com.antibot.velocity.verification.CaptchaVerification;
import com.antibot.velocity.webhook.DiscordWebhook;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.slf4j.Logger;

@Plugin(
    id = "antibot",
    name = "AntiBot Pro",
    version = "2.2.0",
    description = "Продвинутая защита от ботов, читов и DDoS атак с Discord интеграцией",
    authors = { "Developer" }
)
public class AntiBotPlugin {

    private static final ChannelIdentifier BRAND_CHANNEL =
        MinecraftChannelIdentifier.create("minecraft", "brand");

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private ConfigManager configManager;

    private final Map<String, ConnectionData> ipConnections =
        new ConcurrentHashMap<>();
    private final Map<String, Long> blockedIps = new ConcurrentHashMap<>();
    private final Map<String, Integer> accountsPerIp =
        new ConcurrentHashMap<>();
    private final Map<String, String> playerClientBrands =
        new ConcurrentHashMap<>();
    private final Map<String, Long> pendingBrandCheck =
        new ConcurrentHashMap<>();
    private final Map<String, Long> playerJoinTimes = new ConcurrentHashMap<>();
    private final Map<String, String> playerCountries =
        new ConcurrentHashMap<>();
    private final Set<String> whitelistedIps = ConcurrentHashMap.newKeySet();
    private final Set<String> whitelistedPlayers =
        ConcurrentHashMap.newKeySet();

    private final AtomicLong globalConnectionsPerSecond = new AtomicLong(0);
    private volatile long lastSecond = System.currentTimeMillis() / 1000;
    private volatile boolean attackMode = false;
    private volatile long attackModeStartTime = 0;

    private ClientBrandDetector clientBrandDetector;
    private UsernameAnalyzer usernameAnalyzer;
    private VPNProxyDetector vpnProxyDetector;
    private BehaviorAnalyzer behaviorAnalyzer;
    private GeoIPChecker geoIPChecker;
    private CaptchaVerification captchaVerification;
    private DiscordWebhook discordWebhook;
    private DiscordBot discordBot;
    private AccountLinkManager accountLinkManager;

    private final AtomicLong totalBlockedConnections = new AtomicLong(0);
    private final AtomicLong totalCheatDetections = new AtomicLong(0);
    private final AtomicLong totalVPNDetections = new AtomicLong(0);
    private final AtomicLong totalBotDetections = new AtomicLong(0);
    private final AtomicLong totalPlayerJoins = new AtomicLong(0);

    @Inject
    public AntiBotPlugin(
        ProxyServer server,
        Logger logger,
        @DataDirectory Path dataDirectory
    ) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("AntiBot Pro v2.2.0 инициализация...");
        this.configManager = new ConfigManager(dataDirectory, logger);
        configManager.loadConfig();

        this.clientBrandDetector = new ClientBrandDetector();
        this.usernameAnalyzer = new UsernameAnalyzer();
        this.vpnProxyDetector = new VPNProxyDetector();
        this.behaviorAnalyzer = new BehaviorAnalyzer();
        this.geoIPChecker = new GeoIPChecker();
        this.captchaVerification = new CaptchaVerification();

        // Инициализация менеджера привязки аккаунтов
        this.accountLinkManager = new AccountLinkManager(dataDirectory);
        accountLinkManager.load();
        accountLinkManager.configure(
            configManager.isAccountLinkAutoWhitelist(),
            configManager.isAccountLinkDmNotification(),
            configManager.getAccountLinkMaxPerDiscord(),
            configManager.isAccountLinkRequireVerification(),
            new java.util.HashSet<>(configManager.getAccountLinkTrustedRoles())
        );

        setupDiscordWebhook();
        setupDiscordBot();
        loadGeoIPSettings();
        loadWhitelists();

        server.getChannelRegistrar().register(BRAND_CHANNEL);
        server
            .getCommandManager()
            .register("antibot", new AntiBotCommand(this));

        // Задача очистки
        server
            .getScheduler()
            .buildTask(this, this::cleanupTask)
            .repeat(30, TimeUnit.SECONDS)
            .schedule();

        // Сброс подключений в секунду
        server
            .getScheduler()
            .buildTask(this, this::resetConnectionsPerSecond)
            .repeat(1, TimeUnit.SECONDS)
            .schedule();

        // Проверка pending brand
        server
            .getScheduler()
            .buildTask(this, this::checkPendingBrands)
            .repeat(5, TimeUnit.SECONDS)
            .schedule();

        logger.info(
            "╔═══════════════════════════════════════════════════════╗"
        );
        logger.info(
            "║     AntiBot Pro v2.2.0 загружен!                      ║"
        );
        logger.info(
            "║  Продвинутая защита активирована                      ║"
        );
        logger.info(
            "╠═══════════════════════════════════════════════════════╣"
        );
        logger.info("║ + Обнаружение читерских клиентов                     ║");
        logger.info("║ + VPN/Proxy детекция                                 ║");
        logger.info("║ + Анализ поведения                                   ║");
        logger.info("║ + GeoIP защита                                       ║");
        logger.info("║ + Discord Webhook уведомления                        ║");
        logger.info("║ + Discord Bot интеграция                             ║");
        logger.info("║ + Привязка аккаунтов Minecraft <-> Discord           ║");
        logger.info("║ + Верификация игроков                                ║");
        logger.info(
            "╚═══════════════════════════════════════════════════════╝"
        );
    }

    private void setupDiscordWebhook() {
        this.discordWebhook = new DiscordWebhook(
            configManager.getDiscordWebhookUrl()
        );

        discordWebhook.configure(
            configManager.getDiscordWebhookUrl(),
            configManager.isDiscordEnabled(),
            configManager.getDiscordBotName(),
            configManager.getDiscordAvatarUrl(),
            configManager.isDiscordMaskIp()
        );

        discordWebhook.configureEvents(
            configManager.isDiscordLogJoins(),
            configManager.isDiscordLogLeaves(),
            configManager.isDiscordLogCheats(),
            configManager.isDiscordLogVpn(),
            configManager.isDiscordLogBots(),
            configManager.isDiscordLogBlocks(),
            configManager.isDiscordLogKicks(),
            configManager.isDiscordLogAttackMode(),
            configManager.isDiscordLogSuspiciousNames()
        );

        discordWebhook.configureColors(
            configManager.getDiscordColorJoin(),
            configManager.getDiscordColorLeave(),
            configManager.getDiscordColorCheat(),
            configManager.getDiscordColorVpn(),
            configManager.getDiscordColorBot(),
            configManager.getDiscordColorBlock(),
            configManager.getDiscordColorKick(),
            configManager.getDiscordColorAttack(),
            configManager.getDiscordColorSuspicious()
        );
    }

    public void setupDiscordBot() {
        if (!configManager.isDiscordBotEnabled()) {
            logger.info("Discord Bot отключен в конфигурации");
            return;
        }

        String token = configManager.getDiscordBotToken();
        if (token == null || token.isEmpty()) {
            logger.warn("Discord Bot токен не настроен!");
            return;
        }

        this.discordBot = new DiscordBot(this);

        // Конвертация строк в Long для guilds и roles
        Set<Long> allowedGuilds = new java.util.HashSet<>();
        for (String guild : configManager.getDiscordAllowedGuilds()) {
            try {
                allowedGuilds.add(Long.parseLong(guild));
            } catch (NumberFormatException e) {
                // Игнор
            }
        }

        Set<Long> allowedRoles = new java.util.HashSet<>();
        for (String role : configManager.getDiscordAllowedRoles()) {
            try {
                allowedRoles.add(Long.parseLong(role));
            } catch (NumberFormatException e) {
                // Игнор
            }
        }

        discordBot.configure(
            token,
            configManager.getDiscordServerName(),
            configManager.isDiscordBotEnabled(),
            configManager.isDiscordAccountLinkingEnabled(),
            configManager.isDiscordDmVerificationEnabled(),
            configManager.isDiscordCommandWhitelistEnabled(),
            allowedGuilds,
            allowedRoles
        );

        // Запуск бота в отдельном потоке
        new Thread(discordBot::initialize, "DiscordBot-Init").start();

        logger.info("Discord Bot инициализирован");
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        String ip = event
            .getConnection()
            .getRemoteAddress()
            .getAddress()
            .getHostAddress();
        String username = event.getUsername();

        // Проверка whitelist
        if (
            whitelistedIps.contains(ip) ||
            whitelistedPlayers.contains(username.toLowerCase())
        ) {
            logger.debug("Вайтлист: пропускаем {} / {}", username, ip);
            return;
        }

        // Проверка заблокированных IP
        if (blockedIps.containsKey(ip)) {
            long blockedUntil = blockedIps.get(ip);
            if (System.currentTimeMillis() < blockedUntil) {
                event.setResult(
                    PreLoginEvent.PreLoginComponentResult.denied(
                        Component.text(configManager.getBlockedMessage()).color(
                            NamedTextColor.RED
                        )
                    )
                );
                totalBlockedConnections.incrementAndGet();
                logger.info(
                    "Заблокировано подключение с IP: {} (в черном списке)",
                    ip
                );
                return;
            } else {
                blockedIps.remove(ip);
            }
        }

        // Проверка лимита подключений в секунду
        long currentSecond = System.currentTimeMillis() / 1000;
        if (currentSecond != lastSecond) {
            lastSecond = currentSecond;
            globalConnectionsPerSecond.set(0);
        }
        long cps = globalConnectionsPerSecond.incrementAndGet();

        if (cps > configManager.getMaxConnectionsPerSecond()) {
            if (!attackMode) {
                attackMode = true;
                attackModeStartTime = System.currentTimeMillis();
                logger.warn(
                    "╔═══════════════════════════════════════════════════════╗"
                );
                logger.warn(
                    "║  ВНИМАНИЕ! Обнаружена бот-атака! Режим защиты ВКЛЮЧЕН ║"
                );
                logger.warn(
                    "╚═══════════════════════════════════════════════════════╝"
                );
                discordWebhook.sendAttackModeAlert(true, (int) cps);
            }
        }

        if (
            attackMode && cps < configManager.getMaxConnectionsPerSecond() / 2
        ) {
            attackMode = false;
            long duration =
                (System.currentTimeMillis() - attackModeStartTime) / 1000;
            logger.info(
                "Режим защиты ОТКЛЮЧЕН. Атака длилась {} секунд.",
                duration
            );
            discordWebhook.sendAttackModeAlert(false, (int) cps);
        }

        // Запись поведения
        behaviorAnalyzer.recordConnection(username, ip);

        // Проверка лимита подключений с IP
        ConnectionData connectionData = ipConnections.computeIfAbsent(ip, k ->
            new ConnectionData()
        );
        connectionData.addConnection();

        if (
            connectionData.getConnectionsInWindow(
                configManager.getTimeWindowSeconds()
            ) >
            configManager.getMaxConnectionsPerIp()
        ) {
            blockIP(
                ip,
                configManager.getBlockDurationMinutes(),
                "Превышение лимита подключений"
            );
            event.setResult(
                PreLoginEvent.PreLoginComponentResult.denied(
                    Component.text(configManager.getRateLimitMessage()).color(
                        NamedTextColor.RED
                    )
                )
            );
            return;
        }

        // Проверка лимита аккаунтов с IP
        int accounts = accountsPerIp.getOrDefault(ip, 0);
        if (accounts >= configManager.getMaxAccountsPerIp()) {
            event.setResult(
                PreLoginEvent.PreLoginComponentResult.denied(
                    Component.text(configManager.getMaxAccountsMessage()).color(
                        NamedTextColor.RED
                    )
                )
            );
            logger.info(
                "Превышен лимит аккаунтов с IP: {} (аккаунт: {})",
                ip,
                username
            );
            return;
        }

        // Строгий режим
        if (attackMode && configManager.isStrictModeEnabled()) {
            if (connectionData.getConnectionsInWindow(5) > 1) {
                event.setResult(
                    PreLoginEvent.PreLoginComponentResult.denied(
                        Component.text(
                            configManager.getAttackModeMessage()
                        ).color(NamedTextColor.RED)
                    )
                );
                logger.info(
                    "Строгий режим: отклонено подключение с IP: {}",
                    ip
                );
                return;
            }
        }

        // Проверка никнейма
        if (configManager.isNameCheckEnabled()) {
            UsernameAnalyzer.AnalysisResult usernameResult =
                usernameAnalyzer.analyze(username);

            if (usernameResult.isSuspicious()) {
                if (
                    usernameResult.getRiskLevel() ==
                    UsernameAnalyzer.RiskLevel.CRITICAL
                ) {
                    event.setResult(
                        PreLoginEvent.PreLoginComponentResult.denied(
                            Component.text(
                                configManager.getInvalidNameMessage()
                            ).color(NamedTextColor.RED)
                        )
                    );
                    logger.warn(
                        "Заблокирован подозрительный никнейм: {} (риск: {}%)",
                        username,
                        usernameResult.getRiskScore()
                    );
                    discordWebhook.sendSuspiciousUsername(
                        username,
                        ip,
                        usernameResult.getRiskScore(),
                        String.join(", ", usernameResult.getReasons())
                    );
                    totalBotDetections.incrementAndGet();
                    return;
                }

                if (
                    attackMode &&
                    usernameResult.getRiskLevel() ==
                    UsernameAnalyzer.RiskLevel.HIGH
                ) {
                    event.setResult(
                        PreLoginEvent.PreLoginComponentResult.denied(
                            Component.text(
                                configManager.getAttackModeMessage()
                            ).color(NamedTextColor.RED)
                        )
                    );
                    logger.warn(
                        "Режим атаки: отклонен подозрительный ник: {} (риск: {}%)",
                        username,
                        usernameResult.getRiskScore()
                    );
                    return;
                }
            }
        }

        // GeoIP и VPN проверка
        String country = "Unknown";
        if (
            configManager.isVpnCheckEnabled() ||
            configManager.isGeoBlockEnabled()
        ) {
            GeoIPChecker.GeoData geoData = geoIPChecker.lookup(ip);
            country = geoData.getCountryCode();
            playerCountries.put(
                username.toLowerCase(),
                geoData.getCountryName() + " (" + country + ")"
            );
        }

        // VPN/Proxy проверка
        if (configManager.isVpnCheckEnabled()) {
            VPNProxyDetector.DetectionResult vpnResult =
                vpnProxyDetector.checkIP(ip, configManager.getVpnApiKey());

            if (vpnResult.isSuspicious()) {
                if (
                    vpnResult.isVPN() ||
                    vpnResult.isProxy() ||
                    vpnResult.isTor()
                ) {
                    if (configManager.isBlockVpn()) {
                        event.setResult(
                            PreLoginEvent.PreLoginComponentResult.denied(
                                Component.text(
                                    configManager.getVpnBlockedMessage()
                                ).color(NamedTextColor.RED)
                            )
                        );
                        logger.warn(
                            "<{}> Заблокирован VPN/Proxy: {} (ISP: {}, Страна: {})",
                            username,
                            ip,
                            vpnResult.getIsp(),
                            vpnResult.getCountryCode()
                        );
                        discordWebhook.sendVPNDetection(
                            username,
                            ip,
                            vpnResult.getIsp(),
                            vpnResult.getCountryCode(),
                            vpnResult.getRiskScore()
                        );
                        totalVPNDetections.incrementAndGet();
                        return;
                    }
                }
            }
        }

        // Geo блокировка
        if (configManager.isGeoBlockEnabled()) {
            GeoIPChecker.GeoData geoData = geoIPChecker.lookup(ip);
            if (!geoIPChecker.isCountryAllowed(geoData.getCountryCode())) {
                event.setResult(
                    PreLoginEvent.PreLoginComponentResult.denied(
                        Component.text(
                            configManager.getGeoBlockedMessage()
                        ).color(NamedTextColor.RED)
                    )
                );
                logger.info(
                    "Заблокирована страна: {} ({}) для игрока {}",
                    geoData.getCountryName(),
                    geoData.getCountryCode(),
                    username
                );
                totalBlockedConnections.incrementAndGet();
                return;
            }
        }

        // Анализ поведения
        BehaviorAnalyzer.AnalysisResult behaviorResult =
            behaviorAnalyzer.analyze(username, ip);
        if (behaviorResult.isSuspicious()) {
            if (
                behaviorResult.getBehaviorType() ==
                BehaviorAnalyzer.BehaviorType.ATTACK_LIKE
            ) {
                blockIP(
                    ip,
                    configManager.getBlockDurationMinutes() * 2,
                    "Атакующее поведение"
                );
                event.setResult(
                    PreLoginEvent.PreLoginComponentResult.denied(
                        Component.text(configManager.getBlockedMessage()).color(
                            NamedTextColor.RED
                        )
                    )
                );
                logger.warn(
                    "Обнаружено атакующее поведение: {} / {} (риск: {}%)",
                    username,
                    ip,
                    behaviorResult.getRiskScore()
                );
                discordWebhook.sendBotDetection(
                    username,
                    ip,
                    behaviorResult.getRiskScore(),
                    behaviorResult.getBehaviorType().name()
                );
                totalBotDetections.incrementAndGet();
                return;
            }

            if (
                behaviorResult.getBehaviorType() ==
                    BehaviorAnalyzer.BehaviorType.BOT_LIKE &&
                attackMode
            ) {
                event.setResult(
                    PreLoginEvent.PreLoginComponentResult.denied(
                        Component.text(
                            configManager.getAttackModeMessage()
                        ).color(NamedTextColor.RED)
                    )
                );
                logger.info(
                    "Режим атаки: отклонено бот-подобное поведение: {} / {}",
                    username,
                    ip
                );
                totalBotDetections.incrementAndGet();
                return;
            }
        }

        pendingBrandCheck.put(
            username.toLowerCase(),
            System.currentTimeMillis()
        );
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        if (event.getPlayer() != null) {
            Player player = event.getPlayer();
            String ip = player.getRemoteAddress().getAddress().getHostAddress();
            String username = player.getUsername();

            accountsPerIp.merge(ip, 1, Integer::sum);
            playerJoinTimes.put(
                username.toLowerCase(),
                System.currentTimeMillis()
            );
            totalPlayerJoins.incrementAndGet();

            logger.info("Игрок {} подключился с IP {}", username, ip);
        }
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        String username = player.getUsername();
        String ip = player.getRemoteAddress().getAddress().getHostAddress();

        server
            .getScheduler()
            .buildTask(this, () -> {
                checkClientBrand(player);

                String clientBrand = playerClientBrands.getOrDefault(
                    username.toLowerCase(),
                    "Unknown"
                );
                String country = playerCountries.getOrDefault(
                    username.toLowerCase(),
                    "Unknown"
                );

                discordWebhook.sendPlayerJoin(
                    username,
                    ip,
                    clientBrand,
                    country
                );

                // Уведомление через Discord Bot если аккаунт привязан
                if (discordBot != null && discordBot.isEnabled()) {
                    discordBot.sendPlayerJoinNotification(
                        username,
                        ip,
                        clientBrand,
                        country
                    );
                }
            })
            .delay(2, TimeUnit.SECONDS)
            .schedule();
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(BRAND_CHANNEL)) {
            return;
        }

        if (!(event.getSource() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getSource();
        String username = player.getUsername();
        String ip = player.getRemoteAddress().getAddress().getHostAddress();

        byte[] data = event.getData();
        if (data.length > 0) {
            String clientBrand = readBrandString(data);

            if (clientBrand != null && !clientBrand.isEmpty()) {
                processClientBrand(player, clientBrand, username, ip);
            }
        }
    }

    private String readBrandString(byte[] data) {
        try {
            if (data.length == 0) return null;

            int length = data[0] & 0xFF;
            if (length > data.length - 1) {
                length = data.length - 1;
            }

            if (length > 0) {
                return new String(data, 1, length, StandardCharsets.UTF_8);
            }

            return new String(data, StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return null;
        }
    }

    private void checkClientBrand(Player player) {
        String username = player.getUsername();
        String ip = player.getRemoteAddress().getAddress().getHostAddress();

        String clientBrand = player.getClientBrand();

        if (clientBrand != null && !clientBrand.isEmpty()) {
            processClientBrand(player, clientBrand, username, ip);
        }
    }

    private void processClientBrand(
        Player player,
        String clientBrand,
        String username,
        String ip
    ) {
        if (playerClientBrands.containsKey(username.toLowerCase())) {
            return;
        }

        playerClientBrands.put(username.toLowerCase(), clientBrand);
        pendingBrandCheck.remove(username.toLowerCase());

        logger.info("Клиент игрока {}: {}", username, clientBrand);

        if (configManager.isClientCheckEnabled()) {
            ClientBrandDetector.DetectionResult result =
                clientBrandDetector.analyze(clientBrand);

            if (result.isDetected()) {
                logger.warn(
                    "╔═══════════════════════════════════════════════════════════╗"
                );
                logger.warn(
                    "║ Обнаружен читерский клиент!                               ║"
                );
                logger.warn(
                    "╠═══════════════════════════════════════════════════════════╣"
                );
                logger.warn(
                    "║ <{}_{}> Клиент: {}",
                    String.format(
                        "%04d",
                        Math.abs(player.getUniqueId().hashCode() % 10000)
                    ),
                    player.getUsername(),
                    clientBrand
                );
                logger.warn(
                    "╚═══════════════════════════════════════════════════════════╝"
                );

                discordWebhook.sendCheatClientDetection(
                    username,
                    clientBrand,
                    ip,
                    result.getReason()
                );
                totalCheatDetections.incrementAndGet();

                if (configManager.isKickCheatClients()) {
                    player.disconnect(
                        Component.text()
                            .append(
                                Component.text(
                                    "AntiCheat",
                                    NamedTextColor.RED,
                                    TextDecoration.BOLD
                                )
                            )
                            .append(Component.newline())
                            .append(
                                Component.text(
                                    configManager.getCheatClientMessage(),
                                    NamedTextColor.WHITE
                                )
                            )
                            .build()
                    );

                    discordWebhook.sendPlayerKicked(
                        username,
                        ip,
                        "Читерский клиент: " + clientBrand
                    );
                }

                behaviorAnalyzer
                    .getOrCreateBehavior(username, ip)
                    .incrementSuspiciousActions();
            } else if (
                result.getType() ==
                ClientBrandDetector.DetectionType.UNKNOWN_CLIENT
            ) {
                logger.info(
                    "Неизвестный клиент: {} использует '{}'",
                    username,
                    clientBrand
                );
            }
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        String username = player.getUsername();
        String ip = player.getRemoteAddress().getAddress().getHostAddress();

        Long joinTime = playerJoinTimes.remove(username.toLowerCase());
        if (joinTime != null) {
            long sessionDuration = System.currentTimeMillis() - joinTime;
            discordWebhook.sendPlayerLeave(username, ip, sessionDuration);
        }

        behaviorAnalyzer.recordDisconnection(username, ip);

        accountsPerIp.computeIfPresent(ip, (k, v) -> v > 1 ? v - 1 : null);

        playerClientBrands.remove(username.toLowerCase());
        pendingBrandCheck.remove(username.toLowerCase());
        playerCountries.remove(username.toLowerCase());
    }

    private void checkPendingBrands() {
        long now = System.currentTimeMillis();
        long timeout = configManager.getBrandCheckTimeoutSeconds() * 1000L;

        pendingBrandCheck
            .entrySet()
            .removeIf(entry -> {
                if (now - entry.getValue() > timeout) {
                    String username = entry.getKey();

                    server
                        .getPlayer(username)
                        .ifPresent(player -> {
                            if (!playerClientBrands.containsKey(username)) {
                                String ip = player
                                    .getRemoteAddress()
                                    .getAddress()
                                    .getHostAddress();

                                logger.warn(
                                    "Игрок {} не отправил бренд клиента в течение {} секунд",
                                    username,
                                    configManager.getBrandCheckTimeoutSeconds()
                                );

                                if (configManager.isKickNoBrand()) {
                                    player.disconnect(
                                        Component.text(
                                            configManager.getNoBrandMessage()
                                        ).color(NamedTextColor.RED)
                                    );
                                    discordWebhook.sendPlayerKicked(
                                        player.getUsername(),
                                        ip,
                                        "Клиент не отправил бренд"
                                    );
                                }
                            }
                        });
                    return true;
                }
                return false;
            });
    }

    private void blockIP(String ip, int minutes, String reason) {
        blockedIps.put(ip, System.currentTimeMillis() + (minutes * 60 * 1000L));
        logger.warn(
            "IP {} заблокирован на {} минут. Причина: {}",
            ip,
            minutes,
            reason
        );
        discordWebhook.sendIPBlocked(ip, reason, minutes);
        totalBlockedConnections.incrementAndGet();
    }

    private void cleanupTask() {
        long now = System.currentTimeMillis();

        blockedIps.entrySet().removeIf(entry -> entry.getValue() < now);

        ipConnections
            .entrySet()
            .removeIf(
                entry ->
                    entry
                        .getValue()
                        .getConnectionsInWindow(
                            configManager.getTimeWindowSeconds()
                        ) ==
                    0
            );

        accountsPerIp.clear();

        server
            .getAllPlayers()
            .forEach(player -> {
                String ip = player
                    .getRemoteAddress()
                    .getAddress()
                    .getHostAddress();
                accountsPerIp.merge(ip, 1, Integer::sum);
            });

        behaviorAnalyzer.cleanup();
        vpnProxyDetector.cleanupExpiredCache();
        geoIPChecker.cleanupExpiredCache();
        captchaVerification.cleanup();
        accountLinkManager.cleanup();
    }

    private void resetConnectionsPerSecond() {
        long currentSecond = System.currentTimeMillis() / 1000;
        if (currentSecond != lastSecond) {
            globalConnectionsPerSecond.set(0);
            lastSecond = currentSecond;
        }
    }

    private void loadGeoIPSettings() {
        geoIPChecker.setWhitelistMode(configManager.isGeoWhitelistMode());
        geoIPChecker.setAllowedCountries(configManager.getAllowedCountries());
        geoIPChecker.setBlockedCountries(configManager.getBlockedCountries());
    }

    private void loadWhitelists() {
        whitelistedIps.addAll(configManager.getWhitelistedIps());
        whitelistedPlayers.addAll(configManager.getWhitelistedPlayers());
    }

    public void reloadConfig() {
        configManager.loadConfig();
        blockedIps.clear();
        ipConnections.clear();

        loadGeoIPSettings();
        loadWhitelists();
        setupDiscordWebhook();

        // Перезагрузка account link manager
        accountLinkManager.configure(
            configManager.isAccountLinkAutoWhitelist(),
            configManager.isAccountLinkDmNotification(),
            configManager.getAccountLinkMaxPerDiscord(),
            configManager.isAccountLinkRequireVerification(),
            new java.util.HashSet<>(configManager.getAccountLinkTrustedRoles())
        );

        logger.info("Конфигурация перезагружена!");
    }

    // Геттеры
    public ProxyServer getServer() {
        return server;
    }

    public Logger getLogger() {
        return logger;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ClientBrandDetector getClientBrandDetector() {
        return clientBrandDetector;
    }

    public VPNProxyDetector getVpnProxyDetector() {
        return vpnProxyDetector;
    }

    public GeoIPChecker getGeoIPChecker() {
        return geoIPChecker;
    }

    public BehaviorAnalyzer getBehaviorAnalyzer() {
        return behaviorAnalyzer;
    }

    public DiscordWebhook getDiscordWebhook() {
        return discordWebhook;
    }

    public DiscordBot getDiscordBot() {
        return discordBot;
    }

    public AccountLinkManager getAccountLinkManager() {
        return accountLinkManager;
    }

    public boolean isAttackMode() {
        return attackMode;
    }

    public long getGlobalConnectionsPerSecond() {
        return globalConnectionsPerSecond.get();
    }

    public Map<String, Long> getBlockedIps() {
        return blockedIps;
    }

    public long getTotalBlockedConnections() {
        return totalBlockedConnections.get();
    }

    public long getTotalCheatDetections() {
        return totalCheatDetections.get();
    }

    public long getTotalVPNDetections() {
        return totalVPNDetections.get();
    }

    public long getTotalBotDetections() {
        return totalBotDetections.get();
    }

    public long getTotalPlayerJoins() {
        return totalPlayerJoins.get();
    }

    public void addWhitelistedIP(String ip) {
        whitelistedIps.add(ip);
        configManager.getWhitelistedIps().add(ip);
    }

    public void removeWhitelistedIP(String ip) {
        whitelistedIps.remove(ip);
        configManager.getWhitelistedIps().remove(ip);
    }

    public void addWhitelistedPlayer(String player) {
        whitelistedPlayers.add(player.toLowerCase());
        configManager.getWhitelistedPlayers().add(player);
    }

    public void removeWhitelistedPlayer(String player) {
        whitelistedPlayers.remove(player.toLowerCase());
        configManager.getWhitelistedPlayers().remove(player);
    }

    public void unblockIP(String ip) {
        blockedIps.remove(ip);
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        logger.info("AntiBot Pro v2.2.0 выключение...");

        if (discordWebhook != null) {
            discordWebhook.shutdown();
        }

        if (discordBot != null) {
            discordBot.shutdown();
        }

        if (accountLinkManager != null) {
            accountLinkManager.save();
        }

        logger.info("AntiBot Pro успешно выключен!");
    }
}
