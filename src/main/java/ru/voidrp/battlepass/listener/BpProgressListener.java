package ru.voidrp.battlepass.listener;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import ru.voidrp.battlepass.data.BackendSyncClient;
import ru.voidrp.battlepass.data.BattlePassData;
import ru.voidrp.battlepass.data.BattlePassStorage;
import ru.voidrp.battlepass.data.PremiumStorage;
import ru.voidrp.battlepass.quest.BpActiveQuest;
import ru.voidrp.battlepass.quest.BpQuestStorage;
import ru.voidrp.battlepass.quest.BpQuestType;
import ru.voidrp.battlepass.season.BpReward;
import ru.voidrp.battlepass.season.SeasonRewards;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class BpProgressListener implements Listener {

    private static final Set<String> VANILLA_BOSSES = Set.of(
            "minecraft:wither",
            "minecraft:ender_dragon",
            "minecraft:elder_guardian"
    );

    private static final Set<String> MODDED_BOSSES = Set.of(
            "iceandfire:fire_dragon",
            "iceandfire:ice_dragon",
            "iceandfire:sea_serpent",
            "twilightforest:naga",
            "twilightforest:lich",
            "twilightforest:hydra",
            "twilightforest:ur_ghast",
            "cataclysm:lich",
            "cataclysm:harbinger_of_doom",
            "mowziesmobs:frostmaw",
            "mowziesmobs:barako"
    );

    private final BattlePassStorage storage;
    private final PremiumStorage premiumStorage;
    private final BpQuestStorage questStorage;
    private final SeasonRewards seasonRewards;
    private final Economy economy;
    private Plugin plugin;
    private BackendSyncClient backendClient;

    public BpProgressListener(BattlePassStorage storage, PremiumStorage premiumStorage,
                               BpQuestStorage questStorage, SeasonRewards seasonRewards,
                               Economy economy) {
        this.storage = storage;
        this.premiumStorage = premiumStorage;
        this.questStorage = questStorage;
        this.seasonRewards = seasonRewards;
        this.economy = economy;
    }

    public void setPlugin(Plugin plugin) { this.plugin = plugin; }
    public void setBackendClient(BackendSyncClient client) { this.backendClient = client; }

    // ── Login / Quit ──────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        if (plugin == null) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            premiumStorage.syncFromBackend(uuid, name);
            pushProgressAsync(uuid);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        premiumStorage.evict(e.getPlayer().getUniqueId());
    }

    // ── Entity Death ──────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        Entity entity = event.getEntity();
        String entityKey = entity.getType().getKey().toString();

        // XP for kills
        long xpGain;
        if (VANILLA_BOSSES.contains(entityKey)) {
            xpGain = 500;
        } else if (MODDED_BOSSES.contains(entityKey)) {
            xpGain = 150;
        } else {
            xpGain = 10;
        }

        addXpAndNotify(killer, xpGain);

        // Quest progress: KILL quests
        tickQuestProgress(killer, BpQuestType.KILL, entityKey, 1);
    }

    // ── Block Break ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        String blockKey = event.getBlock().getType().name();

        // Quest progress: MINE quests
        tickQuestProgress(player, BpQuestType.MINE, blockKey, 1);
    }

    // ── Item Pickup ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        Player player = event.getPlayer();
        String materialKey = event.getItem().getItemStack().getType().name();
        int amount = event.getItem().getItemStack().getAmount();

        // Quest progress: COLLECT quests
        tickQuestProgress(player, BpQuestType.COLLECT, materialKey, amount);
    }

    // ── Fishing ───────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player player = event.getPlayer();

        // Quest progress: FISH quests (ANY target)
        tickQuestProgress(player, BpQuestType.FISH, "ANY", 1);
    }

    // ── Advancement ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        Advancement adv = event.getAdvancement();
        // Skip recipe unlocks
        if (adv.getKey().getKey().startsWith("recipes/")) return;
        addXpAndNotify(event.getPlayer(), 150);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void addXpAndNotify(Player player, long amount) {
        UUID uuid = player.getUniqueId();
        int oldLevel = storage.addXp(uuid, amount);
        BattlePassData data = storage.get(uuid);
        int newLevel = data.getLevel();
        if (newLevel > oldLevel) {
            for (int lvl = oldLevel + 1; lvl <= newLevel; lvl++) {
                final int displayLvl = lvl;
                Bukkit.getScheduler().runTask(
                        Bukkit.getPluginManager().getPlugin("VoidRpBattlePass"),
                        () -> {
                            player.sendMessage("§6§l✦ §eБаттл Пасс: Уровень " + displayLvl + "! §6/bp для наград");
                            if (displayLvl >= 120) {
                                player.sendTitle("§6§l✦ УРОВЕНЬ МАКСИМУМ!", "§eBattle Pass — все награды доступны!", 10, 80, 20);
                            } else {
                                player.sendTitle("§6§l✦ Уровень " + displayLvl + "!", "§eBattle Pass — §7/bp для наград", 10, 60, 20);
                            }
                            spawnLevelUpFirework(player);
                        });
            }
            // Push updated progress to backend
            if (backendClient != null && backendClient.isConfigured() && plugin != null) {
                final int finalLevel = newLevel;
                final long finalXp = data.getXp();
                final String nick = player.getName();
                Bukkit.getScheduler().runTaskAsynchronously(plugin,
                        () -> backendClient.pushProgress(uuid.toString(), nick, ru.voidrp.battlepass.season.Season.currentKey(), finalLevel, finalXp));
            }
        }
    }

    private void pushProgressAsync(UUID uuid) {
        if (backendClient == null || !backendClient.isConfigured()) return;
        BattlePassData data = storage.get(uuid);
        org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        String nick = op.getName() != null ? op.getName() : uuid.toString();
        backendClient.pushProgress(uuid.toString(), nick, ru.voidrp.battlepass.season.Season.currentKey(), data.getLevel(), data.getXp());
    }

    private void tickQuestProgress(Player player, BpQuestType type, String key, int amount) {
        UUID uuid = player.getUniqueId();
        boolean hasPremium = premiumStorage.hasPremium(uuid);
        BpQuestStorage.PlayerQuestRecord record = questStorage.getToday(uuid);

        List<BpActiveQuest> allQuests = new java.util.ArrayList<>(record.getFreeQuests());
        if (hasPremium) allQuests.addAll(record.getPremiumQuests());

        for (BpActiveQuest quest : allQuests) {
            if (quest.isRewardClaimed()) continue;
            if (quest.getType() != type) continue;
            if (!matchesTarget(quest.getTarget(), key, type)) continue;

            boolean wasDone = quest.isCompleted();
            quest.addProgress(amount);
            if (!wasDone && quest.isCompleted()) {
                // Quest just completed — award XP
                int xpReward = quest.getXpReward();
                quest.setRewardClaimed(true);
                questStorage.save(uuid);

                addXpAndNotify(player, xpReward);
                player.sendMessage("§b⭐ §aBP квест выполнен: §f" + quest.getDisplayName()
                        + " §b+" + xpReward + " XP");
            }
        }
    }

    private boolean matchesTarget(String questTarget, String eventKey, BpQuestType type) {
        if (type == BpQuestType.FISH) {
            // FISH quests always use "ANY"
            return questTarget.equalsIgnoreCase("ANY");
        }
        if (questTarget.equalsIgnoreCase("any")) {
            return true;
        }
        return questTarget.equalsIgnoreCase(eventKey);
    }

    /** Called externally when a level reward is claimed to give the item/money/exp. */
    public void giveReward(Player player, BpReward reward) {
        switch (reward.getType()) {
            case MONEY -> {
                if (economy != null) {
                    economy.depositPlayer(player, reward.getAmount());
                    player.sendMessage("§6💰 Получено §e" + (long) reward.getAmount() + " монет§6!");
                }
            }
            case EXP -> {
                player.giveExp((int) reward.getAmount());
                player.sendMessage("§a✨ Получено §e" + (int) reward.getAmount() + " опыта§a!");
            }
            case ITEM -> {
                org.bukkit.Material mat;
                try {
                    mat = org.bukkit.Material.valueOf(reward.getMaterial().toUpperCase());
                } catch (IllegalArgumentException e) {
                    mat = org.bukkit.Material.PAPER;
                }
                org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(mat, Math.max(1, reward.getCount()));
                player.getInventory().addItem(item);
                player.sendMessage("§b📦 Получено: §f" + (reward.getDisplayName() != null ? reward.getDisplayName() : mat.name()) + "§b!");
            }
            case COMMAND -> {
                String cmd = reward.getCommand().replace("{player}", player.getName());
                // Strip leading slash if present
                if (cmd.startsWith("/")) cmd = cmd.substring(1);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                player.sendMessage("§d🎁 Получена особая награда: §f"
                        + (reward.getDisplayName() != null ? reward.getDisplayName() : "Предмет") + "§d!");
            }
        }
    }

    private void spawnLevelUpFirework(Player player) {
        org.bukkit.Location loc = player.getLocation().add(0, 1, 0);
        org.bukkit.entity.Firework fw = (org.bukkit.entity.Firework)
                loc.getWorld().spawnEntity(loc, org.bukkit.entity.EntityType.FIREWORK_ROCKET);
        org.bukkit.inventory.meta.FireworkMeta meta = fw.getFireworkMeta();
        meta.setPower(0);
        meta.addEffect(org.bukkit.FireworkEffect.builder()
                .with(org.bukkit.FireworkEffect.Type.BALL_LARGE)
                .withColor(org.bukkit.Color.YELLOW, org.bukkit.Color.ORANGE)
                .withFade(org.bukkit.Color.WHITE)
                .flicker(true)
                .trail(true)
                .build());
        fw.setFireworkMeta(meta);
        fw.detonate();
    }
}
