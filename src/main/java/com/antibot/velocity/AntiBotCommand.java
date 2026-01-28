package com.antibot.velocity;

import com.antibot.velocity.detection.BehaviorAnalyzer;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class AntiBotCommand implements SimpleCommand {

    private final AntiBotPlugin plugin;

    public AntiBotCommand(AntiBotPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!invocation.source().hasPermission("antibot.admin")) {
            invocation.source().sendMessage(
                Component.text("У вас нет прав для использования этой команды!")
                    .color(NamedTextColor.RED)
            );
            return;
        }

        String[] args = invocation.arguments();

        if (args.length == 0) {
            sendHelp(invocation);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.reloadConfig();
                invocation.source().sendMessage(
                    Component.text("Конфигурация AntiBot перезагружена!")
                        .color(NamedTextColor.GREEN)
                );
                break;

            case "status":
                sendStatus(invocation);
                break;

            case "stats":
                sendStats(invocation);
                break;

            case "blocked":
                sendBlockedList(invocation);
                break;

            case "unblock":
                if (args.length < 2) {
                    invocation.source().sendMessage(
                        Component.text("Использование: /antibot unblock <ip>")
                            .color(NamedTextColor.RED)
                    );
                    return;
                }
                plugin.unblockIP(args[1]);
                invocation.source().sendMessage(
                    Component.text("IP " + args[1] + " разблокирован!")
                        .color(NamedTextColor.GREEN)
                );
                break;

            case "whitelist":
                handleWhitelist(invocation, args);
                break;

            case "behavior":
                if (args.length < 2) {
                    sendBehaviorStats(invocation);
                } else {
                    sendPlayerBehavior(invocation, args[1]);
                }
                break;

            case "check":
                if (args.length < 2) {
                    invocation.source().sendMessage(
                        Component.text("Использование: /antibot check <player/ip>")
                            .color(NamedTextColor.RED)
                    );
                    return;
                }
                checkTarget(invocation, args[1]);
                break;

            default:
                sendHelp(invocation);
        }
    }

    private void sendHelp(Invocation invocation) {
        invocation.source().sendMessage(Component.empty());
        invocation.source().sendMessage(
            Component.text("═══════════════════════════════════")
                .color(NamedTextColor.GOLD)
        );
        invocation.source().sendMessage(
            Component.text("      AntiBot Pro v2.0.0")
                .color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD)
        );
        invocation.source().sendMessage(
            Component.text("═══════════════════════════════════")
                .color(NamedTextColor.GOLD)
        );
        invocation.source().sendMessage(Component.empty());

        sendCommandHelp(invocation, "/antibot reload", "Перезагрузить конфиг");
        sendCommandHelp(invocation, "/antibot status", "Статус защиты");
        sendCommandHelp(invocation, "/antibot stats", "Статистика обнаружений");
        sendCommandHelp(invocation, "/antibot blocked", "Список заблокированных IP");
        sendCommandHelp(invocation, "/antibot unblock <ip>", "Разблокировать IP");
        sendCommandHelp(invocation, "/antibot whitelist <add/remove> <ip/player> <value>", "Управление вайтлистом");
        sendCommandHelp(invocation, "/antibot behavior [player]", "Анализ поведения");
        sendCommandHelp(invocation, "/antibot check <player/ip>", "Проверить игрока/IP");

        invocation.source().sendMessage(Component.empty());
    }

    private void sendCommandHelp(Invocation invocation, String command, String description) {
        invocation.source().sendMessage(
            Component.text(command)
                .color(NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.suggestCommand(command.split(" ")[0] + " " + command.split(" ")[1]))
                .append(Component.text(" - " + description).color(NamedTextColor.GRAY))
        );
    }

    private void sendStatus(Invocation invocation) {
        invocation.source().sendMessage(Component.empty());
        invocation.source().sendMessage(
            Component.text("══════ Статус AntiBot Pro ══════")
                .color(NamedTextColor.GOLD)
        );

        invocation.source().sendMessage(
            Component.text("Режим атаки: ")
                .color(NamedTextColor.WHITE)
                .append(Component.text(plugin.isAttackMode() ? "АКТИВЕН" : "Неактивен")
                    .color(plugin.isAttackMode() ? NamedTextColor.RED : NamedTextColor.GREEN)
                    .decorate(plugin.isAttackMode() ? TextDecoration.BOLD : TextDecoration.ITALIC))
        );

        invocation.source().sendMessage(
            Component.text("Подключений/сек: ")
                .color(NamedTextColor.WHITE)
                .append(Component.text(String.valueOf(plugin.getGlobalConnectionsPerSecond()))
                    .color(NamedTextColor.AQUA))
        );

        invocation.source().sendMessage(
            Component.text("Заблокировано IP: ")
                .color(NamedTextColor.WHITE)
                .append(Component.text(String.valueOf(plugin.getBlockedIps().size()))
                    .color(NamedTextColor.RED))
        );

        invocation.source().sendMessage(
            Component.text("Онлайн игроков: ")
                .color(NamedTextColor.WHITE)
                .append(Component.text(String.valueOf(plugin.getServer().getPlayerCount()))
                    .color(NamedTextColor.GREEN))
        );

        ConfigManager config = plugin.getConfigManager();
        invocation.source().sendMessage(Component.empty());
        invocation.source().sendMessage(
            Component.text("══════ Активные модули ══════")
                .color(NamedTextColor.GOLD)
        );

        sendModuleStatus(invocation, "Проверка клиентов", config.isClientCheckEnabled());
        sendModuleStatus(invocation, "VPN/Proxy детекция", config.isVpnCheckEnabled());
        sendModuleStatus(invocation, "GeoIP блокировка", config.isGeoBlockEnabled());
        sendModuleStatus(invocation, "Проверка никнеймов", config.isNameCheckEnabled());
        sendModuleStatus(invocation, "Строгий режим", config.isStrictModeEnabled());
    }

    private void sendModuleStatus(Invocation invocation, String module, boolean enabled) {
        invocation.source().sendMessage(
            Component.text("  " + module + ": ")
                .color(NamedTextColor.GRAY)
                .append(Component.text(enabled ? "ВКЛ" : "ВЫКЛ")
                    .color(enabled ? NamedTextColor.GREEN : NamedTextColor.RED))
        );
    }

    private void sendStats(Invocation invocation) {
        invocation.source().sendMessage(Component.empty());
        invocation.source().sendMessage(
            Component.text("══════ Статистика обнаружений ══════")
                .color(NamedTextColor.GOLD)
        );

        invocation.source().sendMessage(
            Component.text("Заблокировано подключений: ")
                .color(NamedTextColor.WHITE)
                .append(Component.text(String.valueOf(plugin.getTotalBlockedConnections()))
                    .color(NamedTextColor.RED))
        );

        invocation.source().sendMessage(
            Component.text("Обнаружено читов: ")
                .color(NamedTextColor.WHITE)
                .append(Component.text(String.valueOf(plugin.getTotalCheatDetections()))
                    .color(NamedTextColor.LIGHT_PURPLE))
        );

        invocation.source().sendMessage(
            Component.text("Обнаружено VPN/Proxy: ")
                .color(NamedTextColor.WHITE)
                .append(Component.text(String.valueOf(plugin.getTotalVPNDetections()))
                    .color(NamedTextColor.GOLD))
        );

        invocation.source().sendMessage(
            Component.text("Обнаружено ботов: ")
                .color(NamedTextColor.WHITE)
                .append(Component.text(String.valueOf(plugin.getTotalBotDetections()))
                    .color(NamedTextColor.DARK_RED))
        );

        invocation.source().sendMessage(
            Component.text("Отслеживается игроков: ")
                .color(NamedTextColor.WHITE)
                .append(Component.text(String.valueOf(plugin.getBehaviorAnalyzer().getTotalTrackedPlayers()))
                    .color(NamedTextColor.AQUA))
        );
    }

    private void sendBlockedList(Invocation invocation) {
        Map<String, Long> blocked = plugin.getBlockedIps();
        
        if (blocked.isEmpty()) {
            invocation.source().sendMessage(
                Component.text("Нет заблокированных IP адресов.")
                    .color(NamedTextColor.GREEN)
            );
            return;
        }

        invocation.source().sendMessage(
            Component.text("══════ Заблокированные IP (" + blocked.size() + ") ══════")
                .color(NamedTextColor.GOLD)
        );

        long now = System.currentTimeMillis();
        blocked.forEach((ip, until) -> {
            long remainingMinutes = (until - now) / 60000;
            invocation.source().sendMessage(
                Component.text("  " + ip)
                    .color(NamedTextColor.RED)
                    .append(Component.text(" - осталось " + remainingMinutes + " мин")
                        .color(NamedTextColor.GRAY))
                    .clickEvent(ClickEvent.suggestCommand("/antibot unblock " + ip))
            );
        });
    }

    private void handleWhitelist(Invocation invocation, String[] args) {
        if (args.length < 4) {
            invocation.source().sendMessage(
                Component.text("Использование: /antibot whitelist <add/remove> <ip/player> <value>")
                    .color(NamedTextColor.RED)
            );
            return;
        }

        String action = args[1].toLowerCase();
        String type = args[2].toLowerCase();
        String value = args[3];

        switch (action) {
            case "add":
                if (type.equals("ip")) {
                    plugin.addWhitelistedIP(value);
                    invocation.source().sendMessage(
                        Component.text("IP " + value + " добавлен в вайтлист!")
                            .color(NamedTextColor.GREEN)
                    );
                } else if (type.equals("player")) {
                    plugin.addWhitelistedPlayer(value);
                    invocation.source().sendMessage(
                        Component.text("Игрок " + value + " добавлен в вайтлист!")
                            .color(NamedTextColor.GREEN)
                    );
                }
                break;

            case "remove":
                if (type.equals("ip")) {
                    plugin.removeWhitelistedIP(value);
                    invocation.source().sendMessage(
                        Component.text("IP " + value + " удален из вайтлиста!")
                            .color(NamedTextColor.YELLOW)
                    );
                } else if (type.equals("player")) {
                    plugin.removeWhitelistedPlayer(value);
                    invocation.source().sendMessage(
                        Component.text("Игрок " + value + " удален из вайтлиста!")
                            .color(NamedTextColor.YELLOW)
                    );
                }
                break;

            default:
                invocation.source().sendMessage(
                    Component.text("Неизвестное действие: " + action)
                        .color(NamedTextColor.RED)
                );
        }
    }

    private void sendBehaviorStats(Invocation invocation) {
        BehaviorAnalyzer analyzer = plugin.getBehaviorAnalyzer();
        Map<String, BehaviorAnalyzer.PlayerBehavior> behaviors = analyzer.getAllBehaviors();

        invocation.source().sendMessage(
            Component.text("══════ Анализ поведения (" + behaviors.size() + " игроков) ══════")
                .color(NamedTextColor.GOLD)
        );

        behaviors.values().stream()
            .sorted((a, b) -> Integer.compare(b.getSuspiciousActions(), a.getSuspiciousActions()))
            .limit(10)
            .forEach(behavior -> {
                NamedTextColor color = behavior.getSuspiciousActions() > 5 ? NamedTextColor.RED :
                    behavior.getSuspiciousActions() > 2 ? NamedTextColor.YELLOW : NamedTextColor.GREEN;
                
                invocation.source().sendMessage(
                    Component.text("  " + behavior.getPlayerName())
                        .color(color)
                        .append(Component.text(" - подозрительных действий: " + behavior.getSuspiciousActions())
                            .color(NamedTextColor.GRAY))
                        .clickEvent(ClickEvent.runCommand("/antibot behavior " + behavior.getPlayerName()))
                );
            });
    }

    private void sendPlayerBehavior(Invocation invocation, String playerName) {
        plugin.getServer().getPlayer(playerName).ifPresentOrElse(player -> {
            String ip = player.getRemoteAddress().getAddress().getHostAddress();
            BehaviorAnalyzer.PlayerBehavior behavior = 
                plugin.getBehaviorAnalyzer().getOrCreateBehavior(playerName, ip);

            invocation.source().sendMessage(Component.empty());
            invocation.source().sendMessage(
                Component.text("══════ Поведение игрока " + playerName + " ══════")
                    .color(NamedTextColor.GOLD)
            );

            invocation.source().sendMessage(
                Component.text("IP: ").color(NamedTextColor.WHITE)
                    .append(Component.text(ip).color(NamedTextColor.AQUA))
            );
            invocation.source().sendMessage(
                Component.text("Всего подключений: ").color(NamedTextColor.WHITE)
                    .append(Component.text(String.valueOf(behavior.getTotalConnections())).color(NamedTextColor.YELLOW))
            );
            invocation.source().sendMessage(
                Component.text("Быстрых переподключений: ").color(NamedTextColor.WHITE)
                    .append(Component.text(String.valueOf(behavior.getFastReconnects())).color(NamedTextColor.GOLD))
            );
            invocation.source().sendMessage(
                Component.text("Подозрительных действий: ").color(NamedTextColor.WHITE)
                    .append(Component.text(String.valueOf(behavior.getSuspiciousActions())).color(NamedTextColor.RED))
            );
            invocation.source().sendMessage(
                Component.text("Использованные ники: ").color(NamedTextColor.WHITE)
                    .append(Component.text(String.join(", ", behavior.getUsedNames())).color(NamedTextColor.GRAY))
            );
            invocation.source().sendMessage(
                Component.text("Верифицирован: ").color(NamedTextColor.WHITE)
                    .append(Component.text(behavior.isVerified() ? "Да" : "Нет")
                        .color(behavior.isVerified() ? NamedTextColor.GREEN : NamedTextColor.RED))
            );

        }, () -> {
            invocation.source().sendMessage(
                Component.text("Игрок " + playerName + " не найден онлайн.")
                    .color(NamedTextColor.RED)
            );
        });
    }

    private void checkTarget(Invocation invocation, String target) {
        plugin.getServer().getPlayer(target).ifPresentOrElse(player -> {
            String ip = player.getRemoteAddress().getAddress().getHostAddress();
            String clientBrand = player.getClientBrand();

            invocation.source().sendMessage(Component.empty());
            invocation.source().sendMessage(
                Component.text("══════ Проверка игрока " + target + " ══════")
                    .color(NamedTextColor.GOLD)
            );

            invocation.source().sendMessage(
                Component.text("IP: ").color(NamedTextColor.WHITE)
                    .append(Component.text(ip).color(NamedTextColor.AQUA))
            );
            invocation.source().sendMessage(
                Component.text("Клиент: ").color(NamedTextColor.WHITE)
                    .append(Component.text(clientBrand != null ? clientBrand : "Неизвестно")
                        .color(NamedTextColor.YELLOW))
            );

            if (clientBrand != null) {
                var result = plugin.getClientBrandDetector().analyze(clientBrand);
                invocation.source().sendMessage(
                    Component.text("Статус клиента: ").color(NamedTextColor.WHITE)
                        .append(Component.text(result.isDetected() ? "ПОДОЗРИТЕЛЬНЫЙ" : "Чистый")
                            .color(result.isDetected() ? NamedTextColor.RED : NamedTextColor.GREEN))
                );
            }

            var vpnResult = plugin.getVpnProxyDetector().checkIP(ip, plugin.getConfigManager().getVpnApiKey());
            invocation.source().sendMessage(
                Component.text("VPN/Proxy: ").color(NamedTextColor.WHITE)
                    .append(Component.text(vpnResult.isSuspicious() ? "Обнаружен" : "Не обнаружен")
                        .color(vpnResult.isSuspicious() ? NamedTextColor.RED : NamedTextColor.GREEN))
            );

            var geoData = plugin.getGeoIPChecker().lookup(ip);
            invocation.source().sendMessage(
                Component.text("Страна: ").color(NamedTextColor.WHITE)
                    .append(Component.text(geoData.getCountryName() + " (" + geoData.getCountryCode() + ")")
                        .color(NamedTextColor.AQUA))
            );
            invocation.source().sendMessage(
                Component.text("Провайдер: ").color(NamedTextColor.WHITE)
                    .append(Component.text(geoData.getIsp()).color(NamedTextColor.GRAY))
            );

        }, () -> {
            invocation.source().sendMessage(
                Component.text("Игрок " + target + " не найден. Проверяю как IP...")
                    .color(NamedTextColor.YELLOW)
            );

            var vpnResult = plugin.getVpnProxyDetector().checkIP(target, plugin.getConfigManager().getVpnApiKey());
            var geoData = plugin.getGeoIPChecker().lookup(target);

            invocation.source().sendMessage(
                Component.text("VPN/Proxy: ").color(NamedTextColor.WHITE)
                    .append(Component.text(vpnResult.isSuspicious() ? "Обнаружен" : "Не обнаружен")
                        .color(vpnResult.isSuspicious() ? NamedTextColor.RED : NamedTextColor.GREEN))
            );
            invocation.source().sendMessage(
                Component.text("Страна: ").color(NamedTextColor.WHITE)
                    .append(Component.text(geoData.getCountryName() + " (" + geoData.getCountryCode() + ")")
                        .color(NamedTextColor.AQUA))
            );
        });
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] args = invocation.arguments();
        List<String> suggestions = new ArrayList<>();

        if (args.length <= 1) {
            suggestions.addAll(List.of("reload", "status", "stats", "blocked", "unblock", "whitelist", "behavior", "check"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "whitelist":
                    suggestions.addAll(List.of("add", "remove"));
                    break;
                case "unblock":
                    suggestions.addAll(plugin.getBlockedIps().keySet());
                    break;
                case "behavior":
                case "check":
                    suggestions.addAll(plugin.getServer().getAllPlayers().stream()
                        .map(Player::getUsername)
                        .collect(Collectors.toList()));
                    break;
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("whitelist")) {
            suggestions.addAll(List.of("ip", "player"));
        }

        String prefix = args.length > 0 ? args[args.length - 1].toLowerCase() : "";
        return CompletableFuture.completedFuture(
            suggestions.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix))
                .collect(Collectors.toList())
        );
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("antibot.admin");
    }
}
