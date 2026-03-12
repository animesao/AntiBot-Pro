package com.antibot.velocity;

import com.antibot.velocity.detection.*;
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
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Plugin(
    id = "antibot",
    name = "AntiBot Pro",
    version = "2.0.0",
    description = "Продвинутая защита от ботов, читов и DDoS атак",
    authors = {"Developer"}
)
public class AntiBotPlugin {

    private static final ChannelIdentifier BRAND_CHANNEL = MinecraftChannelIdentifier.create("minecraft", "brand");

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private ConfigManager configManager;

    private final Map<String, ConnectionData> ipConnections = new ConcurrentHashMap<>();
    private final Map<String, Long> blockedIps = new ConcurrentHashMap<>();
    private final Map<String, Integer> accountsPerIp = new ConcurrentHashMap<>();
    private final Map<String, String> playerClientBrands = new ConcurrentHashMap<>();
    private final Map<String, Long> pendingBrandCheck = new ConcurrentHashMap<>();
    private final Map<String, Long> playerJoinTimes = new ConcurrentHashMap<>();
    private final Map<String, String> playerCountries = new ConcurrentHashMap<>();
    private final Set<String> whitelistedIps = ConcurrentHashMap.newKeySet();
    private final Set<String> whitelistedPlayers = ConcurrentHashMap.newKeySet();

    private int globalConnectionsPerSecond = 0;
    private long lastSecond = System.currentTimeMillis() / 1000;
    private boolean attackMode = false;
    private long attackModeStartTime = 0;

    private ClientBrandDetector clientBrandDetector;
    private UsernameAnalyzer usernameAnalyzer;
    private VPNProxyDetector vpnProxyDetector;
    private BehaviorAnalyzer behaviorAnalyzer;
    private GeoIPChecker geoIPChecker;
    private CaptchaVerification captchaVerification;
    private DiscordWebhook discordWebhook;

    private long totalBlockedConnections = 0;
    private long totalCheatDetections = 0;
    private long totalVPNDetections = 0;
    private long totalBotDetections = 0;
    private long totalPlayerJoins = 0;

    @Inject
    public AntiBotPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        this.configManager = new ConfigManager(dataDirectory, logger);
        configManager.loadConfig();

        this.clientBrandDetector = new ClientBrandDetector();
        this.usernameAnalyzer = new UsernameAnalyzer();
        this.vpnProxyDetector = new VPNProxyDetector();
        this.behaviorAnalyzer = new BehaviorAnalyzer();
        this.geoIPChecker = new GeoIPChecker();
        this.captchaVerification = new CaptchaVerification();
        
        setupDiscordWebhook();
        loadGeoIPSettings();
        loadWhitelists();

        server.getChannelRegistrar().register(BRAND_CHANNEL);
        server.getCommandManager().register("antibot", new AntiBotCommand(this));

        server.getScheduler().buildTask(this, this::cleanupTask)
            .repeat(30, TimeUnit.SECONDS)
            .schedule();

        server.getScheduler().buildTask(this, this::resetConnectionsPerSecond)
            .repeat(1, TimeUnit.SECONDS)
            .schedule();

        server.getScheduler().buildTask(this, this::checkPendingBrands)
            .repeat(5, TimeUnit.SECONDS)
            .schedule();

        logger.info("╔══════════════════════════════════════╗");
        logger.info("║     AntiBot Pro v2.0.0 загружен!     ║");
        logger.info("║  Продвинутая защита активирована     ║");
        logger.info("╠══════════════════════════════════════╣");
        logger.info("║ + Обнаружение читерских клиентов     ║");
        logger.info("║ + VPN/Proxy детекция                 ║");
        logger.info("║ + Анализ поведения                   ║");
        logger.info("║ + GeoIP защита                       ║");
        logger.info("║ + Discord уведомления                ║");
        logger.info("║ + Верификация игроков                ║");
        logger.info("╚══════════════════════════════════════╝");
    }

    private void setupDiscordWebhook() {
        this.discordWebhook = new DiscordWebhook(configManager.getDiscordWebhookUrl());
        
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

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        String ip = event.getConnection().getRemoteAddress().getAddress().getHostAddress();
        String username = event.getUsername();

        if (whitelistedIps.contains(ip) || whitelistedPlayers.contains(username.toLowerCase())) {
            logger.debug("Вайтлист: пропускаем {} / {}", username, ip);
            return;
        }

        if (blockedIps.containsKey(ip)) {
            long blockedUntil = blockedIps.get(ip);
            if (System.currentTimeMillis() < blockedUntil) {
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    Component.text(configManager.getBlockedMessage()).color(NamedTextColor.RED)
                ));
                totalBlockedConnections++;
                logger.info("Заблокировано подключение с IP: {} (в черном списке)", ip);
                return;
            } else {
                blockedIps.remove(ip);
            }
        }

        long currentSecond = System.currentTimeMillis() / 1000;
        if (currentSecond != lastSecond) {
            lastSecond = currentSecond;
            globalConnectionsPerSecond = 0;
        }
        globalConnectionsPerSecond++;

        if (globalConnectionsPerSecond > configManager.getMaxConnectionsPerSecond()) {
            if (!attackMode) {
                attackMode = true;
                attackModeStartTime = System.currentTimeMillis();
                logger.warn("╔═══════════════════════════════════════════════════════╗");
                logger.warn("║  ВНИМАНИЕ! Обнаружена бот-атака! Режим защиты ВКЛЮЧЕН ║");
                logger.warn("╚═══════════════════════════════════════════════════════╝");
                discordWebhook.sendAttackModeAlert(true, globalConnectionsPerSecond);
            }
        }

        if (attackMode && globalConnectionsPerSecond < configManager.getMaxConnectionsPerSecond() / 2) {
            attackMode = false;
            long duration = (System.currentTimeMillis() - attackModeStartTime) / 1000;
            logger.info("Режим защиты ОТКЛЮЧЕН. Атака длилась {} секунд.", duration);
            discordWebhook.sendAttackModeAlert(false, globalConnectionsPerSecond);
        }

        behaviorAnalyzer.recordConnection(username, ip);

        ConnectionData connectionData = ipConnections.computeIfAbsent(ip, k -> new ConnectionData());
        connectionData.addConnection();

        if (connectionData.getConnectionsInWindow(configManager.getTimeWindowSeconds()) > 
            configManager.getMaxConnectionsPerIp()) {
            
            blockIP(ip, configManager.getBlockDurationMinutes(), "Превышение лимита подключений");
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                Component.text(configManager.getRateLimitMessage()).color(NamedTextColor.RED)
            ));
            return;
        }

        int accounts = accountsPerIp.getOrDefault(ip, 0);
        if (accounts >= configManager.getMaxAccountsPerIp()) {
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                Component.text(configManager.getMaxAccountsMessage()).color(NamedTextColor.RED)
            ));
            logger.info("Превышен лимит аккаунтов с IP: {} (аккаунт: {})", ip, username);
            return;
        }

        if (attackMode && configManager.isStrictModeEnabled()) {
            if (connectionData.getConnectionsInWindow(5) > 1) {
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    Component.text(configManager.getAttackModeMessage()).color(NamedTextColor.RED)
                ));
                logger.info("Строгий режим: отклонено подключение с IP: {}", ip);
                return;
            }
        }

        if (configManager.isNameCheckEnabled()) {
            UsernameAnalyzer.AnalysisResult usernameResult = usernameAnalyzer.analyze(username);
            
            if (usernameResult.isSuspicious()) {
                if (usernameResult.getRiskLevel() == UsernameAnalyzer.RiskLevel.CRITICAL) {
                    event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                        Component.text(configManager.getInvalidNameMessage()).color(NamedTextColor.RED)
                    ));
                    logger.warn("Заблокирован подозрительный никнейм: {} (риск: {}%)", 
                        username, usernameResult.getRiskScore());
                    discordWebhook.sendSuspiciousUsername(username, ip, usernameResult.getRiskScore(),
                        String.join(", ", usernameResult.getReasons()));
                    totalBotDetections++;
                    return;
                }
                
                if (attackMode && usernameResult.getRiskLevel() == UsernameAnalyzer.RiskLevel.HIGH) {
                    event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                        Component.text(configManager.getAttackModeMessage()).color(NamedTextColor.RED)
                    ));
                    logger.warn("Режим атаки: отклонен подозрительный ник: {} (риск: {}%)", 
                        username, usernameResult.getRiskScore());
                    return;
                }
            }
        }

        String country = "Unknown";
        if (configManager.isVpnCheckEnabled() || configManager.isGeoBlockEnabled()) {
            GeoIPChecker.GeoData geoData = geoIPChecker.lookup(ip);
            country = geoData.getCountryCode();
            playerCountries.put(username.toLowerCase(), geoData.getCountryName() + " (" + country + ")");
        }

        if (configManager.isVpnCheckEnabled()) {
            VPNProxyDetector.DetectionResult vpnResult = 
                vpnProxyDetector.checkIP(ip, configManager.getVpnApiKey());
            
            if (vpnResult.isSuspicious()) {
                if (vpnResult.isVPN() || vpnResult.isProxy() || vpnResult.isTor()) {
                    if (configManager.isBlockVpn()) {
                        event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                            Component.text(configManager.getVpnBlockedMessage()).color(NamedTextColor.RED)
                        ));
                        logger.warn("<{}> Заблокирован VPN/Proxy: {} (ISP: {}, Страна: {})", 
                            username, ip, vpnResult.getIsp(), vpnResult.getCountryCode());
                        discordWebhook.sendVPNDetection(username, ip, vpnResult.getIsp(), 
                            vpnResult.getCountryCode(), vpnResult.getRiskScore());
                        totalVPNDetections++;
                        return;
                    }
                }
            }
        }

        if (configManager.isGeoBlockEnabled()) {
            GeoIPChecker.GeoData geoData = geoIPChecker.lookup(ip);
            if (!geoIPChecker.isCountryAllowed(geoData.getCountryCode())) {
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    Component.text(configManager.getGeoBlockedMessage()).color(NamedTextColor.RED)
                ));
                logger.info("Заблокирована страна: {} ({}) для игрока {}", 
                    geoData.getCountryName(), geoData.getCountryCode(), username);
                totalBlockedConnections++;
                return;
            }
        }

        BehaviorAnalyzer.AnalysisResult behaviorResult = behaviorAnalyzer.analyze(username, ip);
        if (behaviorResult.isSuspicious()) {
            if (behaviorResult.getBehaviorType() == BehaviorAnalyzer.BehaviorType.ATTACK_LIKE) {
                blockIP(ip, configManager.getBlockDurationMinutes() * 2, "Атакующее поведение");
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    Component.text(configManager.getBlockedMessage()).color(NamedTextColor.RED)
                ));
                logger.warn("Обнаружено атакующее поведение: {} / {} (риск: {}%)", 
                    username, ip, behaviorResult.getRiskScore());
                discordWebhook.sendBotDetection(username, ip, behaviorResult.getRiskScore(),
                    behaviorResult.getBehaviorType().name());
                totalBotDetections++;
                return;
            }
            
            if (behaviorResult.getBehaviorType() == BehaviorAnalyzer.BehaviorType.BOT_LIKE && attackMode) {
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    Component.text(configManager.getAttackModeMessage()).color(NamedTextColor.RED)
                ));
                logger.info("Режим атаки: отклонено бот-подобное поведение: {} / {}", username, ip);
                totalBotDetections++;
                return;
            }
        }

        pendingBrandCheck.put(username.toLowerCase(), System.currentTimeMillis());
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        if (event.getPlayer() != null) {
            Player player = event.getPlayer();
            String ip = player.getRemoteAddress().getAddress().getHostAddress();
            String username = player.getUsername();
            
            accountsPerIp.merge(ip, 1, Integer::sum);
            playerJoinTimes.put(username.toLowerCase(), System.currentTimeMillis());
            totalPlayerJoins++;
            
            logger.info("Игрок {} подключился с IP {}", username, ip);
        }
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        String username = player.getUsername();
        String ip = player.getRemoteAddress().getAddress().getHostAddress();
        
        server.getScheduler().buildTask(this, () -> {
            checkClientBrand(player);
            
            String clientBrand = playerClientBrands.getOrDefault(username.toLowerCase(), "Unknown");
            String country = playerCountries.getOrDefault(username.toLowerCase(), "Unknown");
            
            discordWebhook.sendPlayerJoin(username, ip, clientBrand, country);
        }).delay(2, TimeUnit.SECONDS).schedule();
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

    private void processClientBrand(Player player, String clientBrand, String username, String ip) {
        if (playerClientBrands.containsKey(username.toLowerCase())) {
            return;
        }
        
        playerClientBrands.put(username.toLowerCase(), clientBrand);
        pendingBrandCheck.remove(username.toLowerCase());

        logger.info("Клиент игрока {}: {}", username, clientBrand);

        if (configManager.isClientCheckEnabled()) {
            ClientBrandDetector.DetectionResult result = clientBrandDetector.analyze(clientBrand);

            if (result.isDetected()) {
                logger.warn("╔═══════════════════════════════════════════════════════════╗");
                logger.warn("║ Обнаружен читерский клиент!                    ║");
                logger.warn("╠═══════════════════════════════════════════════════════════╣");
                logger.warn("║ <{}_{}> !https://discord.gg/server", 
                    String.format("%04d", Math.abs(player.getUniqueId().hashCode() % 10000)),
                    player.getUsername());
                logger.warn("║ Клиент: {}", clientBrand);
                logger.warn("╚═══════════════════════════════════════════════════════════╝");

                discordWebhook.sendCheatClientDetection(username, clientBrand, ip, result.getReason());
                totalCheatDetections++;

                if (configManager.isKickCheatClients()) {
                    player.disconnect(Component.text()
                        .append(Component.text("AntiCheat", NamedTextColor.RED, TextDecoration.BOLD))
                        .append(Component.newline())
                        .append(Component.text(configManager.getCheatClientMessage(), NamedTextColor.WHITE))
                        .build());
                    
                    discordWebhook.sendPlayerKicked(username, ip, "Читерский клиент: " + clientBrand);
                }

                behaviorAnalyzer.getOrCreateBehavior(username, ip).incrementSuspiciousActions();
            } else if (result.getType() == ClientBrandDetector.DetectionType.UNKNOWN_CLIENT) {
                logger.info("Неизвестный клиент: {} использует '{}'", username, clientBrand);
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

        pendingBrandCheck.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > timeout) {
                String username = entry.getKey();
                
                server.getPlayer(username).ifPresent(player -> {
                    if (!playerClientBrands.containsKey(username)) {
                        String ip = player.getRemoteAddress().getAddress().getHostAddress();
                        
                        logger.warn("Игрок {} не отправил бренд клиента в течение {} секунд", 
                            username, configManager.getBrandCheckTimeoutSeconds());

                        if (configManager.isKickNoBrand()) {
                            player.disconnect(Component.text(configManager.getNoBrandMessage())
                                .color(NamedTextColor.RED));
                            discordWebhook.sendPlayerKicked(player.getUsername(), ip, 
                                "Клиент не отправил бренд");
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
        logger.warn("IP {} заблокирован на {} минут. Причина: {}", ip, minutes, reason);
        discordWebhook.sendIPBlocked(ip, reason, minutes);
        totalBlockedConnections++;
    }

    private void cleanupTask() {
        long now = System.currentTimeMillis();
        
        blockedIps.entrySet().removeIf(entry -> entry.getValue() < now);
        
        ipConnections.entrySet().removeIf(entry -> 
            entry.getValue().getConnectionsInWindow(configManager.getTimeWindowSeconds()) == 0);
        
        accountsPerIp.clear();
        
        server.getAllPlayers().forEach(player -> {
            String ip = player.getRemoteAddress().getAddress().getHostAddress();
            accountsPerIp.merge(ip, 1, Integer::sum);
        });

        behaviorAnalyzer.cleanup();
        vpnProxyDetector.cleanupExpiredCache();
        geoIPChecker.cleanupExpiredCache();
        captchaVerification.cleanup();
    }

    private void resetConnectionsPerSecond() {
        long currentSecond = System.currentTimeMillis() / 1000;
        if (currentSecond != lastSecond) {
            globalConnectionsPerSecond = 0;
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
        
        logger.info("Конфигурация перезагружена!");
    }

    public ProxyServer getServer() { return server; }
    public Logger getLogger() { return logger; }
    public ConfigManager getConfigManager() { return configManager; }
    public Map<String, Long> getBlockedIps() { return blockedIps; }
    public Set<String> getWhitelistedIps() { return whitelistedIps; }
    public Set<String> getWhitelistedPlayers() { return whitelistedPlayers; }
    public boolean isAttackMode() { return attackMode; }
    public int getGlobalConnectionsPerSecond() { return globalConnectionsPerSecond; }
    public BehaviorAnalyzer getBehaviorAnalyzer() { return behaviorAnalyzer; }
    public ClientBrandDetector getClientBrandDetector() { return clientBrandDetector; }
    public VPNProxyDetector getVpnProxyDetector() { return vpnProxyDetector; }
    public GeoIPChecker getGeoIPChecker() { return geoIPChecker; }
    public CaptchaVerification getCaptchaVerification() { return captchaVerification; }

    public long getTotalBlockedConnections() { return totalBlockedConnections; }
    public long getTotalCheatDetections() { return totalCheatDetections; }
    public long getTotalVPNDetections() { return totalVPNDetections; }
    public long getTotalBotDetections() { return totalBotDetections; }
    public long getTotalPlayerJoins() { return totalPlayerJoins; }

    public void addWhitelistedIP(String ip) { whitelistedIps.add(ip); }
    public void removeWhitelistedIP(String ip) { whitelistedIps.remove(ip); }
    public void addWhitelistedPlayer(String player) { whitelistedPlayers.add(player.toLowerCase()); }
    public void removeWhitelistedPlayer(String player) { whitelistedPlayers.remove(player.toLowerCase()); }
    public void unblockIP(String ip) { blockedIps.remove(ip); }
}
