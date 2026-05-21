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

        // ── Commands ──────────────────────────────────────────────────────────
        BattlePassCommand cmd = new BattlePassCommand(battlePassGui, questGui, storage, premiumStorage, seasonRewards);
        getCommand("battlepass").setExecutor(cmd);
        getCommand("bpadmin").setExecutor(cmd);

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
}
