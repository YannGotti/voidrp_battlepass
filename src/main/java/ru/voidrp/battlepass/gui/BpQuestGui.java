package ru.voidrp.battlepass.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.voidrp.battlepass.data.PremiumStorage;
import ru.voidrp.battlepass.quest.BpActiveQuest;
import ru.voidrp.battlepass.quest.BpQuestStorage;
import ru.voidrp.battlepass.quest.BpQuestType;

import java.util.ArrayList;
import java.util.List;

public final class BpQuestGui {

    private final BpQuestStorage questStorage;
    private final PremiumStorage premiumStorage;

    public BpQuestGui(BpQuestStorage questStorage, PremiumStorage premiumStorage) {
        this.questStorage = questStorage;
        this.premiumStorage = premiumStorage;
    }

    public void open(Player player) {
        String title = "§b§l⭐ Квесты Battle Pass";
        Inventory inv = Bukkit.createInventory(null, 27, title);

        boolean hasPremium = premiumStorage.hasPremium(player.getUniqueId());
        BpQuestStorage.PlayerQuestRecord record = questStorage.getToday(player.getUniqueId());

        // ── Fill background ───────────────────────────────────────────────────
        ItemStack filler = named(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        // ── Free quests: slots 10, 13, 16 ────────────────────────────────────
        int[] freeSlots = {10, 13, 16};
        List<BpActiveQuest> freeQuests = record.getFreeQuests();
        for (int i = 0; i < freeSlots.length; i++) {
            if (i < freeQuests.size()) {
                inv.setItem(freeSlots[i], buildQuestItem(freeQuests.get(i), false));
            }
        }

        // ── Premium quests: slots 1, 4, 7 ────────────────────────────────────
        int[] premSlots = {1, 4, 7};
        List<BpActiveQuest> premQuests = record.getPremiumQuests();
        for (int i = 0; i < premSlots.length; i++) {
            if (i < premQuests.size()) {
                if (hasPremium) {
                    inv.setItem(premSlots[i], buildQuestItem(premQuests.get(i), true));
                } else {
                    ItemStack locked = new ItemStack(Material.RED_STAINED_GLASS_PANE);
                    ItemMeta lm = locked.getItemMeta();
                    lm.setDisplayName("§c🔒 Premium квест");
                    List<String> ll = new ArrayList<>();
                    ll.add("§7Требуется Premium для доступа");
                    ll.add("§7к Premium заданиям.");
                    lm.setLore(ll);
                    locked.setItemMeta(lm);
                    inv.setItem(premSlots[i], locked);
                }
            }
        }

        // ── Slot 22: info/close ───────────────────────────────────────────────
        ItemStack info = new ItemStack(Material.BARRIER);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName("§cЗакрыть");
        List<String> il = new ArrayList<>();
        il.add("§7XP начисляется автоматически");
        il.add("§7при выполнении задания.");
        im.setLore(il);
        info.setItemMeta(im);
        inv.setItem(22, info);

        player.openInventory(inv);
    }

    private ItemStack buildQuestItem(BpActiveQuest quest, boolean isPremium) {
        Material mat = questMaterial(quest.getType());
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        String prefix = isPremium ? "§b[Premium] §e" : "§a[Free] §e";
        if (quest.isRewardClaimed()) {
            meta.setDisplayName(prefix + "§m" + quest.getDisplayName() + " §a✔");
        } else if (quest.isCompleted()) {
            meta.setDisplayName(prefix + quest.getDisplayName() + " §a(выполнено)");
        } else {
            meta.setDisplayName(prefix + quest.getDisplayName());
        }

        List<String> lore = new ArrayList<>();
        lore.add("§7" + quest.getDescription());
        lore.add("");
        // Progress bar
        int progress = quest.getProgress();
        int required = quest.getRequired();
        String bar = buildProgressBar(progress, required, 15);
        lore.add("§f" + bar + " §e" + progress + "§7/§e" + required);
        lore.add("");
        lore.add("§6Награда: §e+" + quest.getXpReward() + " XP");
        if (quest.isRewardClaimed()) {
            lore.add("§a§l✔ Получено");
        } else if (quest.isCompleted()) {
            lore.add("§aXP будет начислен автоматически.");
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String buildProgressBar(int current, int max, int bars) {
        if (max <= 0) return "§a" + "█".repeat(bars);
        int filled = (int) Math.round((double) current / max * bars);
        filled = Math.max(0, Math.min(filled, bars));
        return "§a" + "█".repeat(filled) + "§8" + "░".repeat(bars - filled);
    }

    private Material questMaterial(BpQuestType type) {
        return switch (type) {
            case KILL -> Material.IRON_SWORD;
            case MINE -> Material.IRON_PICKAXE;
            case COLLECT -> Material.CHEST;
            case FISH -> Material.FISHING_ROD;
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
