package ru.voidrp.battlepass.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.voidrp.battlepass.data.BattlePassData;
import ru.voidrp.battlepass.data.BattlePassStorage;
import ru.voidrp.battlepass.data.PremiumStorage;
import ru.voidrp.battlepass.gui.BattlePassGui;
import ru.voidrp.battlepass.gui.BpQuestGui;
import ru.voidrp.battlepass.season.Season;
import ru.voidrp.battlepass.season.SeasonRewards;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public final class BattlePassCommand implements CommandExecutor, TabCompleter {

    private final BattlePassGui battlePassGui;
    private final BpQuestGui questGui;
    private final BattlePassStorage storage;
    private final PremiumStorage premiumStorage;
    private final SeasonRewards seasonRewards;

    public BattlePassCommand(BattlePassGui battlePassGui, BpQuestGui questGui,
                              BattlePassStorage storage, PremiumStorage premiumStorage,
                              SeasonRewards seasonRewards) {
        this.battlePassGui = battlePassGui;
        this.questGui = questGui;
        this.storage = storage;
        this.premiumStorage = premiumStorage;
        this.seasonRewards = seasonRewards;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmdName = command.getName().toLowerCase();

        if (cmdName.equals("battlepass") || cmdName.equals("bp") || cmdName.equals("баттлпасс")) {
            return handleBp(sender, args);
        } else if (cmdName.equals("bpadmin")) {
            return handleBpAdmin(sender, args);
        }
        return false;
    }

    // ── /bp ──────────────────────────────────────────────────────────────────

    private boolean handleBp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cТолько для игроков.");
            return true;
        }

        if (args.length == 0) {
            battlePassGui.open(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "quests" -> questGui.open(player);
            case "info" -> sendBpInfo(player, player.getUniqueId(), player.getName());
            default -> {
                player.sendMessage("§cИспользование: /bp [quests|info]");
            }
        }
        return true;
    }

    private void sendBpInfo(CommandSender sender, UUID uuid, String name) {
        BattlePassData data = storage.get(uuid);
        boolean hasPremium = premiumStorage.hasPremium(uuid);
        int level = data.getLevel();
        long xp = data.getXp();
        long toNext = data.xpToNextLevel();
        String premiumStatus;
        if (hasPremium) {
            long expiry = premiumStorage.getExpiry(uuid);
            String expiryStr = Instant.ofEpochMilli(expiry)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
            premiumStatus = "§a✔ Активен §7(до §e" + expiryStr + "§7)";
        } else {
            premiumStatus = "§c✗ Нет";
        }
        sender.sendMessage("§6§l✦ Battle Pass — §e" + name);
        sender.sendMessage("§7Сезон: §e" + Season.currentKey());
        sender.sendMessage("§7Уровень: §e" + level + "§7/120");
        sender.sendMessage("§7XP: §e" + xp + (level < 120 ? " §7(до сл. уровня: §e" + toNext + "§7)" : ""));
        sender.sendMessage("§7Premium: " + premiumStatus);
    }

    // ── /bpadmin ─────────────────────────────────────────────────────────────

    private boolean handleBpAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("voidrp.battlepass.admin")) {
            sender.sendMessage("§cНет доступа.");
            return true;
        }

        if (args.length < 1) {
            sendAdminHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "premium" -> handleAdminPremium(sender, args);
            case "xp" -> handleAdminXp(sender, args);
            case "level" -> handleAdminLevel(sender, args);
            case "info" -> handleAdminInfo(sender, args);
            case "season" -> handleAdminSeason(sender, args);
            case "reload" -> {
                seasonRewards.reload();
                sender.sendMessage("§aRewards перезагружены.");
            }
            default -> sendAdminHelp(sender);
        }
        return true;
    }

    private void handleAdminPremium(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cИспользование: /bpadmin premium <give|remove> <игрок>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        UUID targetUuid;
        String targetName;
        if (target != null) {
            targetUuid = target.getUniqueId();
            targetName = target.getName();
        } else {
            // Offline player lookup
            @SuppressWarnings("deprecation")
            var offlinePlayer = Bukkit.getOfflinePlayer(args[2]);
            if (offlinePlayer.getUniqueId() == null) {
                sender.sendMessage("§cИгрок §e" + args[2] + " §cне найден.");
                return;
            }
            targetUuid = offlinePlayer.getUniqueId();
            targetName = offlinePlayer.getName() != null ? offlinePlayer.getName() : args[2];
        }

        switch (args[1].toLowerCase()) {
            case "give" -> {
                premiumStorage.grantPremium(targetUuid, targetName, 30, "admin:" + sender.getName());
                sender.sendMessage("§aPremium выдан игроку §e" + targetName + " §aна 30 дней.");
                if (target != null) target.sendMessage("§b✦ §aВы получили §bPremium Battle Pass§a!");
            }
            case "remove" -> {
                premiumStorage.revokePremium(targetUuid);
                sender.sendMessage("§cPremium отозван у игрока §e" + targetName + "§c.");
                if (target != null) target.sendMessage("§c✦ Ваш Premium Battle Pass был отозван.");
            }
            default -> sender.sendMessage("§cИспользование: /bpadmin premium <give|remove> <игрок>");
        }
    }

    private void handleAdminXp(CommandSender sender, String[] args) {
        if (args.length < 4 || !args[1].equalsIgnoreCase("give")) {
            sender.sendMessage("§cИспользование: /bpadmin xp give <игрок> <количество>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        UUID targetUuid;
        String targetName;
        if (target != null) {
            targetUuid = target.getUniqueId();
            targetName = target.getName();
        } else {
            @SuppressWarnings("deprecation")
            var offlinePlayer = Bukkit.getOfflinePlayer(args[2]);
            if (offlinePlayer.getUniqueId() == null) {
                sender.sendMessage("§cИгрок §e" + args[2] + " §cне найден.");
                return;
            }
            targetUuid = offlinePlayer.getUniqueId();
            targetName = offlinePlayer.getName() != null ? offlinePlayer.getName() : args[2];
        }
        long amount;
        try {
            amount = Long.parseLong(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cНеверное число: §e" + args[3]);
            return;
        }
        int oldLevel = storage.addXp(targetUuid, amount);
        BattlePassData data = storage.get(targetUuid);
        int newLevel = data.getLevel();
        storage.save(targetUuid);
        sender.sendMessage("§aДобавлено §e" + amount + " XP §aигроку §e" + targetName
                + "§a. Новый уровень: §e" + newLevel);
        if (target != null && newLevel > oldLevel) {
            target.sendMessage("§6§l✦ §eАдмин добавил XP! Уровень: §e" + newLevel);
        }
    }

    private void handleAdminLevel(CommandSender sender, String[] args) {
        if (args.length < 4 || !args[1].equalsIgnoreCase("set")) {
            sender.sendMessage("§cИспользование: /bpadmin level set <игрок> <уровень>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        UUID targetUuid;
        String targetName;
        if (target != null) {
            targetUuid = target.getUniqueId();
            targetName = target.getName();
        } else {
            @SuppressWarnings("deprecation")
            var offlinePlayer = Bukkit.getOfflinePlayer(args[2]);
            if (offlinePlayer.getUniqueId() == null) {
                sender.sendMessage("§cИгрок §e" + args[2] + " §cне найден.");
                return;
            }
            targetUuid = offlinePlayer.getUniqueId();
            targetName = offlinePlayer.getName() != null ? offlinePlayer.getName() : args[2];
        }
        int level;
        try {
            level = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cНеверное число: §e" + args[3]);
            return;
        }
        storage.setLevel(targetUuid, level);
        storage.save(targetUuid);
        int clamped = Math.max(1, Math.min(120, level));
        sender.sendMessage("§aУровень игрока §e" + targetName + " §aустановлен на §e" + clamped + "§a.");
        if (target != null) target.sendMessage("§6§l✦ §eВаш уровень Battle Pass установлен на §e" + clamped + "§6!");
    }

    private void handleAdminInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /bpadmin info <игрок>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        UUID targetUuid;
        String targetName;
        if (target != null) {
            targetUuid = target.getUniqueId();
            targetName = target.getName();
        } else {
            @SuppressWarnings("deprecation")
            var offlinePlayer = Bukkit.getOfflinePlayer(args[1]);
            if (offlinePlayer.getUniqueId() == null) {
                sender.sendMessage("§cИгрок §e" + args[1] + " §cне найден.");
                return;
            }
            targetUuid = offlinePlayer.getUniqueId();
            targetName = offlinePlayer.getName() != null ? offlinePlayer.getName() : args[1];
        }
        sendBpInfo(sender, targetUuid, targetName);
    }

    private void handleAdminSeason(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("reset")) {
            sender.sendMessage("§cИспользование: /bpadmin season reset");
            return;
        }
        storage.clearOldSeasonCache(Season.currentKey());
        sender.sendMessage("§aКэш данных старых сезонов очищен.");
    }

    // ── Tab completion ────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        String cmdName = command.getName().toLowerCase();

        if (cmdName.equals("battlepass") || cmdName.equals("bp") || cmdName.equals("баттлпасс")) {
            if (args.length == 1) return filter(List.of("quests", "info"), args[0]);
            return List.of();
        }

        if (cmdName.equals("bpadmin")) {
            if (!sender.hasPermission("voidrp.battlepass.admin")) return List.of();

            if (args.length == 1) {
                return filter(List.of("premium", "xp", "level", "info", "season", "reload"), args[0]);
            }

            return switch (args[0].toLowerCase()) {
                case "premium" -> {
                    if (args.length == 2) yield filter(List.of("give", "remove"), args[1]);
                    if (args.length == 3) yield onlinePlayers(args[2]);
                    yield List.of();
                }
                case "xp" -> {
                    if (args.length == 2) yield filter(List.of("give"), args[1]);
                    if (args.length == 3) yield onlinePlayers(args[2]);
                    if (args.length == 4) yield List.of("<количество>");
                    yield List.of();
                }
                case "level" -> {
                    if (args.length == 2) yield filter(List.of("set"), args[1]);
                    if (args.length == 3) yield onlinePlayers(args[2]);
                    if (args.length == 4) yield filter(List.of("1","10","20","30","40","50","60","70","80","90","100","110","120"), args[3]);
                    yield List.of();
                }
                case "info" -> {
                    if (args.length == 2) yield onlinePlayers(args[1]);
                    yield List.of();
                }
                case "season" -> {
                    if (args.length == 2) yield filter(List.of("reset"), args[1]);
                    yield List.of();
                }
                default -> List.of();
            };
        }

        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        if (prefix.isEmpty()) return new ArrayList<>(options);
        String lower = prefix.toLowerCase();
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }

    private List<String> onlinePlayers(String prefix) {
        String lower = prefix.toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }

    private void sendAdminHelp(CommandSender sender) {
        sender.sendMessage("§6§lBattle Pass Admin:");
        sender.sendMessage("§e/bpadmin premium give <игрок>");
        sender.sendMessage("§e/bpadmin premium remove <игрок>");
        sender.sendMessage("§e/bpadmin xp give <игрок> <кол-во>");
        sender.sendMessage("§e/bpadmin level set <игрок> <уровень>");
        sender.sendMessage("§e/bpadmin info <игрок>");
        sender.sendMessage("§e/bpadmin season reset");
        sender.sendMessage("§e/bpadmin reload");
    }
}
