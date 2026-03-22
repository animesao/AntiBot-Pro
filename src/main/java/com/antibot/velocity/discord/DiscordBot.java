package com.antibot.velocity.discord;

import com.antibot.velocity.AntiBotPlugin;
import com.velocitypowered.api.proxy.Player;
import java.awt.*;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discord Bot для интеграции с сервером
 * Поддержка команд, привязки аккаунтов и уведомлений
 */
public class DiscordBot extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(
        DiscordBot.class
    );
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final AntiBotPlugin plugin;
    private JDA jda;
    private boolean enabled;
    private String botToken;
    private String serverName;
    private boolean accountLinkingEnabled;
    private boolean dmVerificationEnabled;
    private boolean commandWhitelistEnabled;
    private Set<Long> allowedGuilds = new HashSet<>();
    private Set<Long> allowedRoles = new HashSet<>();

    private final Map<String, String> pendingVerifications = new HashMap<>();
    private final Map<String, Long> verificationCodes = new HashMap<>();
    private final Random random = new Random();

    public DiscordBot(AntiBotPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Инициализация Discord бота
     */
    public void initialize() {
        if (!enabled || botToken == null || botToken.isEmpty()) {
            logger.warn("Discord Bot отключен или токен не настроен");
            return;
        }

        try {
            jda = JDABuilder.createDefault(botToken)
                .enableIntents(
                    GatewayIntent.GUILD_MESSAGES,
                    GatewayIntent.MESSAGE_CONTENT,
                    GatewayIntent.GUILD_MEMBERS,
                    GatewayIntent.DIRECT_MESSAGES
                )
                .setActivity(
                    Activity.playing(
                        serverName != null ? serverName : "Minecraft"
                    )
                )
                .addEventListeners(this)
                .build();

            jda.awaitReady();

            // Регистрация slash команд
            registerSlashCommands();

            logger.info(
                "Discord Bot успешно запущен: {}",
                jda.getSelfUser().getAsTag()
            );
        } catch (Exception e) {
            logger.error("Ошибка запуска Discord Bot: {}", e.getMessage());
        }
    }

    /**
     * Регистрация slash команд
     */
    private void registerSlashCommands() {
        if (jda == null) return;

        jda
            .updateCommands()
            .addCommands(
                // Команда /link - Привязка аккаунта
                jda
                    .upsertCommand(
                        "link",
                        "Привязать Minecraft аккаунт к Discord"
                    )
                    .addOption(
                        OptionType.STRING,
                        "nickname",
                        "Ваш никнейм на сервере",
                        true
                    ),
                // Команда /unlink - Отвязка аккаунта
                jda.upsertCommand(
                    "unlink",
                    "Отвязать Minecraft аккаунт от Discord"
                ),
                // Команда /status - Статус сервера
                jda.upsertCommand("status", "Показать статус сервера"),
                // Команда /online - Игроки онлайн
                jda.upsertCommand("online", "Показать игроков онлайн"),
                // Команда /verify - Верификация через DM
                jda.upsertCommand("verify", "Получить код верификации в ЛС"),
                // Команда /help - Помощь
                jda.upsertCommand("help", "Показать список команд"),
                // Админ команды
                jda.upsertCommand("stats", "Показать статистику AntiBot"),
                jda.upsertCommand(
                    "reload",
                    "Перезагрузить конфигурацию AntiBot"
                ),
                jda
                    .upsertCommand("check", "Проверить игрока")
                    .addOption(
                        OptionType.STRING,
                        "player",
                        "Никнейм игрока",
                        true
                    ),
                jda
                    .upsertCommand("whitelist", "Управление whitelist")
                    .addOption(OptionType.STRING, "action", "add/remove", true)
                    .addOption(OptionType.STRING, "type", "ip/player", true)
                    .addOption(OptionType.STRING, "value", "Значение", true)
            )
            .queue();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!enabled) {
            event
                .reply("❌ Discord Bot отключен на сервере")
                .setEphemeral(true)
                .queue();
            return;
        }

        // Проверка whitelist серверов
        if (
            commandWhitelistEnabled &&
            !allowedGuilds.isEmpty() &&
            !allowedGuilds.contains(
                event.getGuild() != null ? event.getGuild().getIdLong() : 0L
            )
        ) {
            event
                .reply("❌ Эта команда недоступна на этом сервере")
                .setEphemeral(true)
                .queue();
            return;
        }

        String command = event.getName();

        executor.submit(() -> {
            switch (command) {
                case "link":
                    handleLinkCommand(event);
                    break;
                case "unlink":
                    handleUnlinkCommand(event);
                    break;
                case "status":
                    handleStatusCommand(event);
                    break;
                case "online":
                    handleOnlineCommand(event);
                    break;
                case "verify":
                    handleVerifyCommand(event);
                    break;
                case "help":
                    handleHelpCommand(event);
                    break;
                case "stats":
                    handleStatsCommand(event);
                    break;
                case "reload":
                    handleReloadCommand(event);
                    break;
                case "check":
                    handleCheckCommand(event);
                    break;
                case "whitelist":
                    handleWhitelistCommand(event);
                    break;
            }
        });
    }

    /**
     * Обработка команды /link
     */
    private void handleLinkCommand(SlashCommandInteractionEvent event) {
        String nickname = event.getOption("nickname").getAsString();
        String discordId = event.getUser().getId();
        String discordName = event.getUser().getName();

        // Проверка игрока онлайн
        Optional<Player> playerOpt = plugin.getServer().getPlayer(nickname);

        if (playerOpt.isEmpty()) {
            event
                .reply("❌ Игрок **" + nickname + "** не найден онлайн!")
                .setEphemeral(true)
                .queue();
            return;
        }

        Player player = playerOpt.get();
        String ip = player.getRemoteAddress().getAddress().getHostAddress();

        // Проверка уже привязанного аккаунта
        String existingLink = plugin
            .getAccountLinkManager()
            .getDiscordId(nickname);
        if (existingLink != null) {
            event
                .reply(
                    "❌ Аккаунт **" + nickname + "** уже привязан к Discord!"
                )
                .setEphemeral(true)
                .queue();
            return;
        }

        // Проверка привязки по IP
        String linkedByIp = plugin.getAccountLinkManager().getLinkedByIP(ip);
        if (linkedByIp != null) {
            event
                .reply(
                    "⚠️ С вашего IP уже привязан аккаунт: **" +
                        linkedByIp +
                        "**"
                )
                .setEphemeral(true)
                .queue();
            return;
        }

        // Привязка аккаунта
        plugin.getAccountLinkManager().linkAccount(nickname, discordId, ip);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("✅ Аккаунт привязан!");
        embed.setColor(Color.GREEN);
        embed.setDescription(
            "Minecraft аккаунт **" +
                nickname +
                "** успешно привязан к вашему Discord."
        );
        embed.addField("Никнейм", nickname, true);
        embed.addField("Discord", discordName, true);
        embed.addField("IP", ip, true);
        embed.setFooter("AntiBot Pro v2.0.0");
        embed.setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).queue();

        // Отправка уведомления в лог
        plugin
            .getDiscordWebhook()
            .sendCustomMessage(
                "🔗 Привязка аккаунта",
                "Игрок привязал свой аккаунт к Discord",
                0x00FF00,
                Map.of("Никнейм", nickname, "Discord", discordName, "IP", ip)
            );
    }

    /**
     * Обработка команды /unlink
     */
    private void handleUnlinkCommand(SlashCommandInteractionEvent event) {
        String discordId = event.getUser().getId();

        String nickname = plugin.getAccountLinkManager().getNickname(discordId);
        if (nickname == null) {
            event
                .reply("❌ У вас нет привязанного Minecraft аккаунта!")
                .setEphemeral(true)
                .queue();
            return;
        }

        plugin.getAccountLinkManager().unlinkAccount(discordId);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("✅ Аккаунт отвязан!");
        embed.setColor(Color.YELLOW);
        embed.setDescription(
            "Minecraft аккаунт **" + nickname + "** отвязан от вашего Discord."
        );
        embed.setFooter("AntiBot Pro v2.0.0");
        embed.setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).queue();
    }

    /**
     * Обработка команды /status
     */
    private void handleStatusCommand(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("📊 Статус сервера");
        embed.setColor(Color.BLUE);

        int online = plugin.getServer().getPlayerCount();
        int maxPlayers = plugin
            .getServer()
            .getConfiguration()
            .getShowMaxPlayers();
        boolean attackMode = plugin.isAttackMode();

        embed.addField("Статус", "🟢 Онлайн", true);
        embed.addField("Игроков", online + " / " + maxPlayers, true);
        embed.addField(
            "Режим атаки",
            attackMode ? "🚨 АКТИВЕН" : "✅ Неактивен",
            true
        );
        embed.addField(
            "Заблокировано IP",
            String.valueOf(plugin.getBlockedIps().size()),
            true
        );
        embed.addField(
            "Всего подключений",
            String.valueOf(plugin.getTotalPlayerJoins()),
            true
        );
        embed.addField(
            "Обнаружено читов",
            String.valueOf(plugin.getTotalCheatDetections()),
            true
        );

        embed.setFooter(serverName != null ? serverName : "AntiBot Pro");
        embed.setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).queue();
    }

    /**
     * Обработка команды /online
     */
    private void handleOnlineCommand(SlashCommandInteractionEvent event) {
        List<Player> players = new ArrayList<>(
            plugin.getServer().getAllPlayers()
        );

        if (players.isEmpty()) {
            event
                .reply("❌ На сервере нет игроков онлайн.")
                .setEphemeral(true)
                .queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("👥 Игроки онлайн (" + players.size() + ")");
        embed.setColor(Color.GREEN);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(players.size(), 25); i++) {
            Player player = players.get(i);
            String discordId = plugin
                .getAccountLinkManager()
                .getDiscordId(player.getUsername());
            String linked = discordId != null ? "✅" : "❌";
            sb
                .append(linked)
                .append(" ")
                .append(player.getUsername())
                .append("\n");
        }

        embed.setDescription(sb.toString());
        embed.setFooter(
            "Показано " + Math.min(players.size(), 25) + " из " + players.size()
        );
        embed.setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).queue();
    }

    /**
     * Обработка команды /verify
     */
    private void handleVerifyCommand(SlashCommandInteractionEvent event) {
        if (!dmVerificationEnabled) {
            event
                .reply("❌ DM верификация отключена на этом сервере")
                .setEphemeral(true)
                .queue();
            return;
        }

        String discordId = event.getUser().getId();
        long code = 100000 + random.nextInt(900000);
        verificationCodes.put(discordId, code);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("🔐 Код верификации");
        embed.setColor(Color.ORANGE);
        embed.setDescription("Ваш код верификации: **" + code + "**");
        embed.addField(
            "Инструкция",
            "1. Зайдите на сервер\n" +
                "2. Введите команду `/verify " +
                code +
                "`\n" +
                "3. После успешной верификации используйте `/link <ник>`",
            false
        );
        embed.setFooter("Код действителен 5 минут");
        embed.setTimestamp(Instant.now());

        event
            .getUser()
            .openPrivateChannel()
            .queue(privateChannel ->
                privateChannel
                    .sendMessageEmbeds(embed.build())
                    .queue(
                        success ->
                            event
                                .reply("✅ Код отправлен в ЛС!")
                                .setEphemeral(true)
                                .queue(),
                        error ->
                            event
                                .reply(
                                    "❌ Не удалось отправить ЛС. Проверьте настройки приватности."
                                )
                                .setEphemeral(true)
                                .queue()
                    )
            );
    }

    /**
     * Обработка команды /help
     */
    private void handleHelpCommand(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("📚 Помощь - AntiBot Pro");
        embed.setColor(Color.CYAN);
        embed.setDescription("Список доступных команд Discord бота:");

        embed.addField(
            "🔗 Привязка аккаунтов",
            "`/link <ник>` - Привязать Minecraft аккаунт\n" +
                "`/unlink` - Отвязать Minecraft аккаунт\n" +
                "`/verify` - Получить код верификации",
            false
        );

        embed.addField(
            "📊 Информация о сервере",
            "`/status` - Статус сервера\n" + "`/online` - Игроки онлайн",
            false
        );

        embed.addField(
            "🛡️ Админ команды",
            "`/stats` - Статистика AntiBot\n" +
                "`/reload` - Перезагрузить конфиг\n" +
                "`/check <игрок>` - Проверить игрока\n" +
                "`/whitelist <action> <type> <value>` - Whitelist",
            false
        );

        embed.setFooter("AntiBot Pro v2.0.0");
        embed.setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    /**
     * Обработка команды /stats
     */
    private void handleStatsCommand(SlashCommandInteractionEvent event) {
        // Проверка прав администратора
        if (!hasAdminRole(event.getMember())) {
            event
                .reply("❌ У вас нет прав для использования этой команды!")
                .setEphemeral(true)
                .queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("📈 Статистика AntiBot Pro");
        embed.setColor(Color.PINK);

        embed.addField(
            "🚫 Заблокировано подключений",
            String.valueOf(plugin.getTotalBlockedConnections()),
            true
        );
        embed.addField(
            "🎯 Обнаружено читов",
            String.valueOf(plugin.getTotalCheatDetections()),
            true
        );
        embed.addField(
            "🌐 Обнаружено VPN/Proxy",
            String.valueOf(plugin.getTotalVPNDetections()),
            true
        );
        embed.addField(
            "🤖 Обнаружено ботов",
            String.valueOf(plugin.getTotalBotDetections()),
            true
        );
        embed.addField(
            "👥 Отслеживается игроков",
            String.valueOf(
                plugin.getBehaviorAnalyzer().getTotalTrackedPlayers()
            ),
            true
        );
        embed.addField(
            "🔒 Заблокировано IP",
            String.valueOf(plugin.getBlockedIps().size()),
            true
        );

        embed.setFooter("AntiBot Pro v2.0.0");
        embed.setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).queue();
    }

    /**
     * Обработка команды /reload
     */
    private void handleReloadCommand(SlashCommandInteractionEvent event) {
        if (!hasAdminRole(event.getMember())) {
            event
                .reply("❌ У вас нет прав для использования этой команды!")
                .setEphemeral(true)
                .queue();
            return;
        }

        plugin.reloadConfig();

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("✅ Конфигурация перезагружена");
        embed.setColor(Color.GREEN);
        embed.setDescription("Конфигурация AntiBot Pro успешно перезагружена!");
        embed.setFooter("AntiBot Pro v2.0.0");
        embed.setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).queue();
    }

    /**
     * Обработка команды /check
     */
    private void handleCheckCommand(SlashCommandInteractionEvent event) {
        if (!hasAdminRole(event.getMember())) {
            event
                .reply("❌ У вас нет прав для использования этой команды!")
                .setEphemeral(true)
                .queue();
            return;
        }

        String playerName = event.getOption("player").getAsString();
        Optional<Player> playerOpt = plugin.getServer().getPlayer(playerName);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("🔍 Проверка игрока: " + playerName);
        embed.setColor(Color.YELLOW);

        if (playerOpt.isPresent()) {
            Player player = playerOpt.get();
            String ip = player.getRemoteAddress().getAddress().getHostAddress();
            String clientBrand = player.getClientBrand();

            embed.addField("Никнейм", playerName, true);
            embed.addField("IP", ip, true);
            embed.addField(
                "Клиент",
                clientBrand != null ? clientBrand : "Unknown",
                true
            );

            // Проверка VPN
            var vpnResult = plugin
                .getVpnProxyDetector()
                .checkIP(ip, plugin.getConfigManager().getVpnApiKey());
            embed.addField(
                "VPN/Proxy",
                vpnResult.isSuspicious() ? "🚨 Обнаружен" : "✅ Чист",
                true
            );

            // Проверка GeoIP
            var geoData = plugin.getGeoIPChecker().lookup(ip);
            embed.addField(
                "Страна",
                geoData.getCountryName() +
                    " (" +
                    geoData.getCountryCode() +
                    ")",
                true
            );

            // Привязка Discord
            String discordId = plugin
                .getAccountLinkManager()
                .getDiscordId(playerName);
            embed.addField(
                "Discord",
                discordId != null ? "✅ Привязан" : "❌ Не привязан",
                true
            );
        } else {
            embed.setDescription("❌ Игрок не найден онлайн");
        }

        embed.setFooter("AntiBot Pro v2.0.0");
        embed.setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).queue();
    }

    /**
     * Обработка команды /whitelist
     */
    private void handleWhitelistCommand(SlashCommandInteractionEvent event) {
        if (!hasAdminRole(event.getMember())) {
            event
                .reply("❌ У вас нет прав для использования этой команды!")
                .setEphemeral(true)
                .queue();
            return;
        }

        String action = event.getOption("action").getAsString();
        String type = event.getOption("type").getAsString();
        String value = event.getOption("value").getAsString();

        boolean success = false;
        String message = "";

        switch (action) {
            case "add":
                if (type.equals("ip")) {
                    plugin.addWhitelistedIP(value);
                    success = true;
                    message = "IP **" + value + "** добавлен в whitelist!";
                } else if (type.equals("player")) {
                    plugin.addWhitelistedPlayer(value);
                    success = true;
                    message = "Игрок **" + value + "** добавлен в whitelist!";
                }
                break;
            case "remove":
                if (type.equals("ip")) {
                    plugin.removeWhitelistedIP(value);
                    success = true;
                    message = "IP **" + value + "** удален из whitelist!";
                } else if (type.equals("player")) {
                    plugin.removeWhitelistedPlayer(value);
                    success = true;
                    message = "Игрок **" + value + "** удален из whitelist!";
                }
                break;
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(success ? "✅ Успешно" : "❌ Ошибка");
        embed.setColor(success ? Color.GREEN : Color.RED);
        embed.setDescription(message);
        embed.setFooter("AntiBot Pro v2.0.0");
        embed.setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).queue();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!enabled || !dmVerificationEnabled) return;
        if (event.isFromGuild()) return; // Только DM

        String discordId = event.getAuthor().getId();
        String message = event.getMessage().getContentRaw().trim();

        // Проверка кода верификации
        Long expectedCode = verificationCodes.get(discordId);
        if (expectedCode != null) {
            try {
                long code = Long.parseLong(message);
                if (code == expectedCode) {
                    // Успешная верификация
                    plugin
                        .getAccountLinkManager()
                        .markDiscordVerified(discordId);

                    EmbedBuilder embed = new EmbedBuilder();
                    embed.setTitle("✅ Верификация успешна!");
                    embed.setColor(Color.GREEN);
                    embed.setDescription(
                        "Ваш Discord аккаунт успешно верифицирован!\nТеперь используйте `/link <ник>` для привязки."
                    );
                    embed.setFooter("AntiBot Pro v2.2.0");
                    embed.setTimestamp(Instant.now());

                    event.getChannel().sendMessageEmbeds(embed.build()).queue();
                    verificationCodes.remove(discordId);
                } else {
                    event
                        .getChannel()
                        .sendMessage(
                            "❌ Неверный код! Попробуйте `/verify` ещё раз."
                        )
                        .queue();
                }
            } catch (NumberFormatException e) {
                // Не число
            }
        }
    }

    /**
     * Проверка наличия админ роли
     */
    private boolean hasAdminRole(net.dv8tion.jda.api.entities.Member member) {
        if (member == null) return false;
        if (member.isOwner()) return true;
        if (
            member.hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR)
        ) return true;

        if (!allowedRoles.isEmpty()) {
            return member
                .getRoles()
                .stream()
                .anyMatch(role -> allowedRoles.contains(role.getIdLong()));
        }

        return false;
    }

    /**
     * Отправить уведомление о входе игрока
     */
    public void sendPlayerJoinNotification(
        String username,
        String ip,
        String clientBrand,
        String country
    ) {
        if (!enabled || jda == null) return;

        String discordId = plugin
            .getAccountLinkManager()
            .getDiscordId(username);
        if (discordId == null) return;

        net.dv8tion.jda.api.entities.User user = jda.getUserById(discordId);
        if (user == null) return;

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("✅ Вы вошли на сервер");
        embed.setColor(Color.GREEN);
        embed.addField("Никнейм", username, true);
        embed.addField("Страна", country, true);
        embed.addField(
            "Клиент",
            clientBrand != null ? clientBrand : "Unknown",
            true
        );
        embed.setFooter("AntiBot Pro v2.0.0");
        embed.setTimestamp(Instant.now());

        user
            .openPrivateChannel()
            .queue(channel -> channel.sendMessageEmbeds(embed.build()).queue());
    }

    /**
     * Настройка бота
     */
    public void configure(
        String botToken,
        String serverName,
        boolean enabled,
        boolean accountLinkingEnabled,
        boolean dmVerificationEnabled,
        boolean commandWhitelistEnabled,
        Set<Long> allowedGuilds,
        Set<Long> allowedRoles
    ) {
        this.botToken = botToken;
        this.serverName = serverName;
        this.enabled = enabled;
        this.accountLinkingEnabled = accountLinkingEnabled;
        this.dmVerificationEnabled = dmVerificationEnabled;
        this.commandWhitelistEnabled = commandWhitelistEnabled;
        this.allowedGuilds = allowedGuilds;
        this.allowedRoles = allowedRoles;
    }

    /**
     * Остановка бота
     */
    public void shutdown() {
        if (jda != null) {
            jda.shutdown();
        }
        executor.shutdown();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isAccountLinkingEnabled() {
        return accountLinkingEnabled;
    }

    public boolean isDmVerificationEnabled() {
        return dmVerificationEnabled;
    }

    public JDA getJDA() {
        return jda;
    }

    public Long getVerificationCode(String discordId) {
        return verificationCodes.get(discordId);
    }

    public void removeVerificationCode(String discordId) {
        verificationCodes.remove(discordId);
    }
}
