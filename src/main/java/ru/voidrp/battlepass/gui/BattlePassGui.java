package ru.voidrp.battlepass.gui;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.voidrp.battlepass.data.BattlePassData;
import ru.voidrp.battlepass.data.BattlePassStorage;
import ru.voidrp.battlepass.data.PremiumStorage;
import ru.voidrp.battlepass.season.BpReward;
import ru.voidrp.battlepass.season.Season;
import ru.voidrp.battlepass.season.SeasonRewards;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BattlePassGui {

    private static final int LEVELS_PER_PAGE = 9;
    private static final int TOTAL_LEVELS = 120;

    /** Tracks which page each player currently has open. */
    public static final ConcurrentHashMap<UUID, Integer> PLAYER_PAGE = new ConcurrentHashMap<>();

    private final BattlePassStorage storage;
    private final PremiumStorage premiumStorage;
    private final SeasonRewards seasonRewards;
    private final Economy economy;

    public BattlePassGui(BattlePassStorage storage, PremiumStorage premiumStorage,
                         SeasonRewards seasonRewards, Economy economy) {
        this.storage = storage;
        this.premiumStorage = premiumStorage;
        this.seasonRewards = seasonRewards;
        this.economy = economy;
    }

    public void open(Player player) {
        int page = PLAYER_PAGE.getOrDefault(player.getUniqueId(), 0);
        open(player, page);
    }

    public void open(Player player, int page) {
        String season = Season.currentKey();
        String title = "§6§l✦ Battle Pass — §e" + season;
        Inventory inv = Bukkit.createInventory(null, 54, title);

        BattlePassData data = storage.get(player.getUniqueId());
        boolean hasPremium = premiumStorage.hasPremium(player.getUniqueId());
        int totalPages = (int) Math.ceil((double) TOTAL_LEVELS / LEVELS_PER_PAGE); // 14 pages (0..13)
        int clampedPage = Math.max(0, Math.min(page, totalPages - 1));
        PLAYER_PAGE.put(player.getUniqueId(), clampedPage);

        int firstLevel = clampedPage * LEVELS_PER_PAGE + 1;
        int lastLevel = Math.min(firstLevel + LEVELS_PER_PAGE - 1, TOTAL_LEVELS);

        // ── Row 0: navigation & info ──────────────────────────────────────────

        // Slot 0: prev page
        if (clampedPage > 0) {
            inv.setItem(0, named(Material.ARROW, "§7◄ Назад"));
        } else {
            inv.setItem(0, named(Material.GRAY_STAINED_GLASS_PANE, "§7◄ Назад"));
        }

        // Slots 1-3: XP info
        int level = data.getLevel();
        long xpInLevel = data.xpInCurrentLevel();
        long toNext = data.xpToNextLevel();
        ItemStack xpItem = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta xpMeta = xpItem.getItemMeta();
        xpMeta.setDisplayName("§aОпыт Battle Pass");
        List<String> xpLore = new ArrayList<>();
        xpLore.add("§7Всего XP: §e" + data.getXp());
        xpLore.add("§7Уровень: §e" + level + "§7/§e120");
        if (level < 120) {
            xpLore.add("§7Прогресс: §e" + xpInLevel + "§7/§e1000 XP");
            xpLore.add("§7До следующего: §e" + toNext + " XP");
        } else {
            xpLore.add("§6✦ §aМаксимальный уровень!");
        }
        xpMeta.setLore(xpLore);
        xpItem.setItemMeta(xpMeta);
        inv.setItem(1, xpItem);
        inv.setItem(2, xpItem.clone());
        inv.setItem(3, xpItem.clone());

        // Slot 4: Season info
        ItemStack seasonItem = new ItemStack(Material.CLOCK);
        ItemMeta seasonMeta = seasonItem.getItemMeta();
        seasonMeta.setDisplayName("§6Сезон §e" + season);
        List<String> seasonLore = new ArrayList<>();
        seasonLore.add("§7До сброса: §e" + Season.daysUntilReset() + " §7дн.");
        seasonMeta.setLore(seasonLore);
        seasonItem.setItemMeta(seasonMeta);
        inv.setItem(4, seasonItem);

        // Slots 5-7: Premium status
        Material premMat = hasPremium ? Material.DIAMOND : Material.IRON_INGOT;
        ItemStack premItem = new ItemStack(premMat);
        ItemMeta premMeta = premItem.getItemMeta();
        if (hasPremium) {
            premMeta.setDisplayName("§b§lPremium §a✔");
            List<String> premLore = new ArrayList<>();
            long expiry = premiumStorage.getExpiry(player.getUniqueId());
            String expiryStr = Instant.ofEpochMilli(expiry)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
            premLore.add("§7Истекает: §e" + expiryStr);
            premMeta.setLore(premLore);
        } else {
            long expiry = premiumStorage.getExpiry(player.getUniqueId());
            boolean wasActive = expiry > 0;
            premMeta.setDisplayName(wasActive ? "§c§lPremium истёк" : "§7Нет Premium");
            List<String> premLore = new ArrayList<>();
            if (wasActive) {
                String expiryStr = java.time.Instant.ofEpochMilli(expiry)
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                premLore.add("§7Истёк: §c" + expiryStr);
                premLore.add("§7Уже полученные награды сохранены.");
                premLore.add("§7Продлите Premium для продолжения.");
            } else {
                premLore.add("§7Приобретите Premium для доступа");
                premLore.add("§7к дополнительным наградам!");
            }
            premMeta.setLore(premLore);
        }
        premItem.setItemMeta(premMeta);
        inv.setItem(5, premItem);
        inv.setItem(6, premItem.clone());
        inv.setItem(7, premItem.clone());

        // Slot 8: next page
        if (clampedPage < totalPages - 1) {
            inv.setItem(8, named(Material.ARROW, "§7Вперёд ►"));
        } else {
            inv.setItem(8, named(Material.GRAY_STAINED_GLASS_PANE, "§7Вперёд ►"));
        }

        // ── Rows 1-3: level content ───────────────────────────────────────────
        int playerLevel = data.getLevel();

        for (int i = 0; i < LEVELS_PER_PAGE; i++) {
            int lvl = firstLevel + i;
            if (lvl > TOTAL_LEVELS) break;

            // Row 1 (slots 9-17): level indicators
            Material indicatorMat;
            if (data.isFreeClaimed(lvl) || data.isPremiumClaimed(lvl)) {
                indicatorMat = Material.LIME_STAINED_GLASS_PANE;
            } else if (playerLevel == lvl) {
                indicatorMat = Material.YELLOW_STAINED_GLASS_PANE;
            } else if (playerLevel > lvl) {
                indicatorMat = Material.LIME_STAINED_GLASS_PANE;
            } else {
                indicatorMat = Material.GRAY_STAINED_GLASS_PANE;
            }
            inv.setItem(9 + i, named(indicatorMat, "§fУровень " + lvl));

            // Row 2 (slots 18-26): free rewards
            BpReward freeReward = seasonRewards.getFreeReward(lvl);
            if (freeReward != null) {
                boolean reachable = playerLevel >= lvl;
                boolean claimed = data.isFreeClaimed(lvl);
                ItemStack rewardItem = buildRewardItem(freeReward, lvl, reachable, claimed, false);
                inv.setItem(18 + i, rewardItem);
            } else {
                inv.setItem(18 + i, named(Material.GRAY_STAINED_GLASS_PANE, "§8—"));
            }

            // Row 3 (slots 27-35): premium rewards
            BpReward premReward = seasonRewards.getPremiumReward(lvl);
            if (premReward != null) {
                boolean reachable = playerLevel >= lvl;
                boolean claimed = data.isPremiumClaimed(lvl);
                if (hasPremium || claimed) {
                    ItemStack rewardItem = buildRewardItem(premReward, lvl, reachable, claimed, true);
                    inv.setItem(27 + i, rewardItem);
                } else {
                    ItemStack locked = new ItemStack(Material.RED_STAINED_GLASS_PANE);
                    ItemMeta lm = locked.getItemMeta();
                    lm.setDisplayName("§c🔒 Premium");
                    List<String> ll = new ArrayList<>();
                    ll.add("§7Требуется активный Premium");
                    lm.setLore(ll);
                    locked.setItemMeta(lm);
                    inv.setItem(27 + i, locked);
                }
            } else {
                inv.setItem(27 + i, named(Material.GRAY_STAINED_GLASS_PANE, "§8—"));
            }
        }

        // ── Row 4 (slots 36-44): separator ────────────────────────────────────
        for (int i = 36; i <= 44; i++) {
            inv.setItem(i, named(Material.PURPLE_STAINED_GLASS_PANE, " "));
        }

        // ── Row 5 ─────────────────────────────────────────────────────────────
        // Slot 45: BP Quests button
        ItemStack questsBtn = new ItemStack(Material.BOOK);
        ItemMeta qm = questsBtn.getItemMeta();
        qm.setDisplayName("§e§lЕжедневные квесты BP");
        List<String> ql = new ArrayList<>();
        ql.add("§7Нажми для просмотра");
        qm.setLore(ql);
        questsBtn.setItemMeta(qm);
        inv.setItem(45, questsBtn);

        // Slots 46-52: filler
        ItemStack filler = named(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 46; i <= 52; i++) {
            inv.setItem(i, filler);
        }

        // Slot 53: current level display
        Material levelMat = (playerLevel >= 120) ? Material.NETHER_STAR : Material.GOLD_INGOT;
        ItemStack levelItem = new ItemStack(levelMat);
        ItemMeta lm = levelItem.getItemMeta();
        lm.setDisplayName("§6Уровень §e" + playerLevel + "§6/120");
        List<String> ll2 = new ArrayList<>();
        ll2.add("§7XP: §e" + data.getXp());
        lm.setLore(ll2);
        levelItem.setItemMeta(lm);
        inv.setItem(53, levelItem);

        player.openInventory(inv);
    }

    private ItemStack buildRewardItem(BpReward reward, int level, boolean reachable, boolean claimed, boolean isPremium) {
        Material mat = rewardIconMaterial(reward);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        String trackPrefix = isPremium ? "§b[Premium] " : "§a[Free] ";
        String suffix;
        if (claimed) {
            suffix = "§m§7Уровень " + level + " §a✔";
        } else if (reachable) {
            suffix = "§aУровень " + level + " §7— §eНажми для получения!";
        } else {
            suffix = "§7Уровень " + level;
        }
        meta.setDisplayName(trackPrefix + suffix);

        List<String> lore = buildRewardLore(reward, claimed);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private List<String> buildRewardLore(BpReward reward, boolean claimed) {
        List<String> lore = new ArrayList<>();
        switch (reward.getType()) {
            case MONEY -> lore.add("§6💰 " + (long) reward.getAmount() + " монет");
            case EXP -> lore.add("§a✨ " + (int) reward.getAmount() + " опыта");
            case ITEM -> lore.add("§b📦 " + reward.getDisplayName()
                    + (reward.getCount() > 1 ? " ×" + reward.getCount() : ""));
            case COMMAND -> lore.add("§d🎁 " + (reward.getDisplayName() != null ? reward.getDisplayName() : "Особая награда"));
        }
        if (claimed) lore.add("§a§lПолучено!");
        return lore;
    }

    private Material rewardIconMaterial(BpReward reward) {
        return switch (reward.getType()) {
            case MONEY -> Material.GOLD_NUGGET;
            case EXP -> Material.EXPERIENCE_BOTTLE;
            case ITEM -> {
                if (reward.getMaterial() != null) {
                    try {
                        yield Material.valueOf(reward.getMaterial().toUpperCase());
                    } catch (IllegalArgumentException e) {
                        yield Material.BOOK;
                    }
                }
                yield Material.BOOK;
            }
            case COMMAND -> Material.CHEST;
        };
    }

    private ItemStack named(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }
}
