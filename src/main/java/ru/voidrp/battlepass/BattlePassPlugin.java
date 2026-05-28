package ru.voidrp.battlepass;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import ru.voidrp.battlepass.command.BattlePassCommand;
import ru.voidrp.battlepass.data.BackendSyncClient;
import ru.voidrp.battlepass.data.BattlePassStorage;
import ru.voidrp.battlepass.data.PremiumStorage;
import ru.voidrp.battlepass.gui.BattlePassGui;
import ru.voidrp.battlepass.gui.BpQuestGui;
import ru.voidrp.battlepass.listener.BpGuiListener;
import ru.voidrp.battlepass.listener.BpNpcListener;
import ru.voidrp.battlepass.listener.BpProgressListener;
import ru.voidrp.battlepass.quest.BpQuestStorage;
import ru.voidrp.battlepass.season.Season;
import ru.voidrp.battlepass.season.SeasonRewards;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

public final class BattlePassPlugin extends JavaPlugin {

    private BattlePassStorage storage;
    private PremiumStorage premiumStorage;
    private BpQuestStorage questStorage;
    private SeasonRewards seasonRewards;
    private Economy economy;

    private String lastKnownSeason;

    @Override
    public void onEnable() {
        // ── Data folder setup ─────────────────────────────────────────────────
        getDataFolder().mkdirs();
        saveDefaultConfig();

        // ── Season dates ──────────────────────────────────────────────────────
        String startStr = getConfig().getString("season-start", "");
        String endStr   = getConfig().getString("season-end", "");
        if (!startStr.isBlank() && !endStr.isBlank()) {
            try {
                LocalDate seasonStart = LocalDate.parse(startStr);
                LocalDate seasonEnd   = LocalDate.parse(endStr);
                if (seasonEnd.isBefore(seasonStart)) {
                    getLogger().warning("[BattlePass] season-end is before season-start — dates ignored.");
                } else {
                    Season.configure(seasonStart, seasonEnd);
                    getLogger().info("[BattlePass] Season dates: " + startStr + " → " + endStr);
                }
            } catch (DateTimeParseException e) {
                getLogger().warning("[BattlePass] Invalid season date format (expected yyyy-MM-dd): " + e.getMessage());
            }
        } else {
            getLogger().info("[BattlePass] season-start/season-end not set — using month-based season.");
        }

        File seasonsDir  = new File(getDataFolder(), "seasons");
        File premiumDir  = new File(getDataFolder(), "premium");
        File bpQuestsDir = new File(getDataFolder(), "bp-quests");
        seasonsDir.mkdirs();
        premiumDir.mkdirs();
        bpQuestsDir.mkdirs();

        // ── Init storages ─────────────────────────────────────────────────────
        storage        = new BattlePassStorage(getDataFolder(), getLogger());
        premiumStorage = new PremiumStorage(premiumDir, getLogger());
        questStorage   = new BpQuestStorage(bpQuestsDir, getLogger());
        seasonRewards  = new SeasonRewards(this);
        lastKnownSeason = Season.currentKey();

        // ── Backend sync ──────────────────────────────────────────────────────
        String backendUrl  = getConfig().getString("backend-url", "");
        String authSecret  = getConfig().getString("game-auth-secret", "");
        BackendSyncClient backendClient = null;
        if (!authSecret.isBlank()) {
            backendClient = new BackendSyncClient(backendUrl, authSecret, getLogger());
            premiumStorage.setBackend(backendClient);
            getLogger().info("[BattlePass] Backend sync enabled: " + backendUrl);
        } else {
            getLogger().info("[BattlePass] Backend sync disabled (game-auth-secret not set).");
        }
        final BackendSyncClient finalBackendClient = backendClient;

        // ── Vault ─────────────────────────────────────────────────────────────
        economy = setupEconomy();
        if (economy == null) {
            getLogger().warning("[BattlePass] Vault not found — money rewards disabled.");
        }

        // ── GUI ───────────────────────────────────────────────────────────────
        String seasonDisplayName = getConfig().getString("season-name", Season.currentKey());
        BattlePassGui battlePassGui = new BattlePassGui(storage, premiumStorage, seasonRewards, economy, seasonDisplayName);
        BpQuestGui questGui = new BpQuestGui(questStorage, premiumStorage);

        // ── Listeners ─────────────────────────────────────────────────────────
        BpProgressListener progressListener =
                new BpProgressListener(storage, premiumStorage, questStorage, seasonRewards, economy);
        progressListener.setPlugin(this);
        if (finalBackendClient != null) progressListener.setBackendClient(finalBackendClient);

        getServer().getPluginManager().registerEvents(progressListener, this);
        getServer().getPluginManager().registerEvents(
                new BpGuiListener(battlePassGui, questGui, storage, premiumStorage, seasonRewards, progressListener), this);
        getServer().getPluginManager().registerEvents(
                new BpNpcListener(battlePassGui, getConfig().getStringList("battlepass-npc-names")), this);
        getServer().getPluginManager().registerEvents(
                new ru.voidrp.battlepass.listener.TeleportProgressListener(this), this);

        // ── Commands ──────────────────────────────────────────────────────────
        BattlePassCommand cmd = new BattlePassCommand(battlePassGui, questGui, storage, premiumStorage, seasonRewards);
        cmd.setPlugin(this);
        cmd.setBackendClient(finalBackendClient);
        attachProxy("battlepass", cmd);
        attachProxy("bp", cmd);
        attachProxy("баттлпасс", cmd);
        attachProxy("bpadmin", cmd);

        // Rebuild Paper's brigadier dispatcher so PlugMan hot-reload works.
        // Without this, the old BukkitCommandNode still holds a reference to
        // the previous (disabled) PluginCommand and throws "plugin is disabled".
        try {
            getServer().getClass().getMethod("syncCommands").invoke(getServer());
            getLogger().info("[BattlePass] syncCommands() called — brigadier tree rebuilt.");
        } catch (Exception e) {
            getLogger().warning("[BattlePass] syncCommands() failed: " + e.getMessage());
        }

        // ── Periodic backend sync (every 10 min) ──────────────────────────────
        if (finalBackendClient != null) {
            long periodTicks = 20L * 60 * 10; // 10 minutes
            getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                for (org.bukkit.entity.Player p : getServer().getOnlinePlayers()) {
                    ru.voidrp.battlepass.data.BattlePassData d = storage.get(p.getUniqueId());
                    finalBackendClient.pushProgress(p.getUniqueId().toString(), p.getName(), Season.currentKey(), d.getLevel(), d.getXp());
                }
            }, periodTicks, periodTicks);
        }

        // ── Hooks for voidrp_daily_quests ─────────────────────────────────────
        if (getServer().getPluginManager().getPlugin("VoidRpDailyQuests") != null) {
            BattlePassHooks.onDailyQuestClaim  = p -> addXpSafe(p, 600);
            BattlePassHooks.onBossQuestClaim   = p -> addXpSafe(p, 2000);
            BattlePassHooks.onDeliveryQuestClaim = p -> addXpSafe(p, 3000);
            getLogger().info("[BattlePass] VoidRpDailyQuests detected — hooks registered.");
        } else {
            getLogger().info("[BattlePass] VoidRpDailyQuests not found — hooks not registered.");
        }

        // ── Scheduler: every minute ───────────────────────────────────────────
        new BukkitRunnable() {
            @Override
            public void run() {
                String currentSeason = Season.currentKey();
                if (!currentSeason.equals(lastKnownSeason)) {
                    getLogger().info("[BattlePass] Season changed: " + lastKnownSeason + " → " + currentSeason);
                    storage.clearOldSeasonCache(currentSeason);
                    lastKnownSeason = currentSeason;
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendMessage("§6§l✦ §eНовый сезон Battle Pass: §6" + currentSeason + "§e! /bp");
                    }
                }
            }
        }.runTaskTimerAsynchronously(this, 1200L, 1200L);

        // ── Auto-save every 5 minutes ─────────────────────────────────────────
        new BukkitRunnable() {
            @Override
            public void run() {
                storage.saveAll();
                questStorage.saveAll();
            }
        }.runTaskTimerAsynchronously(this, 6000L, 6000L);

        getLogger().info("[BattlePass] VoidRp Battle Pass enabled — season: " + Season.currentKey());
    }

    @Override
    public void onDisable() {
        if (storage != null) storage.saveAll();
        if (questStorage != null) questStorage.saveAll();
        getLogger().info("[BattlePass] VoidRp Battle Pass disabled — all data saved.");
    }

    private void addXpSafe(Player player, long amount) {
        if (!player.isOnline()) return;
        int oldLevel = storage.addXp(player.getUniqueId(), amount);
        int newLevel = storage.get(player.getUniqueId()).getLevel();
        if (newLevel > oldLevel) {
            for (int lvl = oldLevel + 1; lvl <= newLevel; lvl++) {
                final int displayLvl = lvl;
                Bukkit.getScheduler().runTask(this,
                        () -> player.sendMessage("§6§l✦ §eБаттл Пасс: Уровень " + displayLvl + "! §6/bp для наград"));
            }
        }
    }

    private Economy setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return null;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : rsp.getProvider();
    }

    /**
     * Injects a long-lived BpProxyCommand (plain Command, no owningPlugin check) into
     * knownCommands under the given name. On PlugMan hot-reload the proxy object survives
     * in the CommandMap; we locate it by class name (cross-classloader string comparison)
     * and update only its executor reference via reflection — no owningPlugin patching needed.
     */
    @SuppressWarnings("unchecked")
    private void attachProxy(String name, BattlePassCommand executor) {
        try {
            java.lang.reflect.Field kf = findKnownCommandsField(getServer().getCommandMap().getClass());
            kf.setAccessible(true);
            var known = (java.util.Map<String, org.bukkit.command.Command>) kf.get(getServer().getCommandMap());

            var existing = known.get(name);

            // Reload path: proxy from a previous classloader load is still in the map.
            // We can't cast across classloader boundary, but we can invoke update() by name.
            if (existing != null && existing.getClass().getName().equals(
                    "ru.voidrp.battlepass.BattlePassPlugin$BpProxyCommand")) {
                existing.getClass()
                        .getMethod("update",
                                org.bukkit.command.CommandExecutor.class,
                                org.bukkit.command.TabCompleter.class)
                        .invoke(existing, executor, executor);
                getLogger().info("[BattlePass] Updated proxy /" + name + " (PlugMan reload).");
                return;
            }

            // First load (or proxy was somehow lost) — create and inject a fresh proxy.
            BpProxyCommand proxy = new BpProxyCommand(name);
            proxy.update(executor, executor);
            known.put(name, proxy);
            known.put("voidrpbattlepass:" + name, proxy);
            getLogger().info("[BattlePass] Registered proxy /" + name + ".");

        } catch (Exception e) {
            getLogger().warning("[BattlePass] attachProxy failed for /" + name + ": " + e.getMessage());
        }
    }

    private static java.lang.reflect.Field findKnownCommandsField(Class<?> cls) throws NoSuchFieldException {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try { return c.getDeclaredField("knownCommands"); } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException("knownCommands not found in hierarchy of " + cls.getName());
    }

    /**
     * A plain Command (not PluginCommand) that delegates to a hot-swappable executor.
     * Because it does not extend PluginCommand there is no owningPlugin.isEnabled() guard —
     * making it safe to keep permanently in the CommandMap across PlugMan reloads.
     */
    private static final class BpProxyCommand extends org.bukkit.command.Command {

        private volatile org.bukkit.command.CommandExecutor executor;
        private volatile org.bukkit.command.TabCompleter completer;

        BpProxyCommand(String name) {
            super(name);
            setDescription("VoidRp Battle Pass");
            setUsage("/" + name);
        }

        public void update(org.bukkit.command.CommandExecutor exec, org.bukkit.command.TabCompleter comp) {
            this.executor = exec;
            this.completer = comp;
        }

        @Override
        public boolean execute(org.bukkit.command.CommandSender sender, String label, String[] args) {
            if (executor == null) return false;
            try {
                return executor.onCommand(sender, this, label, args);
            } catch (Exception e) {
                sender.sendMessage("§cОшибка при выполнении команды.");
                return false;
            }
        }

        @Override
        public java.util.List<String> tabComplete(org.bukkit.command.CommandSender sender, String alias, String[] args) {
            if (completer == null) return java.util.List.of();
            try {
                java.util.List<String> result = completer.onTabComplete(sender, this, alias, args);
                return result != null ? result : java.util.List.of();
            } catch (Exception e) {
                return java.util.List.of();
            }
        }
    }
}
