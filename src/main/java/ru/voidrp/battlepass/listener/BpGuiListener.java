package ru.voidrp.battlepass.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import ru.voidrp.battlepass.data.BattlePassData;
import ru.voidrp.battlepass.data.BattlePassStorage;
import ru.voidrp.battlepass.data.PremiumStorage;
import ru.voidrp.battlepass.gui.BattlePassGui;
import ru.voidrp.battlepass.gui.BpQuestGui;
import ru.voidrp.battlepass.season.BpReward;
import ru.voidrp.battlepass.season.SeasonRewards;

import java.util.UUID;

public final class BpGuiListener implements Listener {

    private static final int LEVELS_PER_PAGE = 9;

    private final BattlePassGui battlePassGui;
    private final BpQuestGui questGui;
    private final BattlePassStorage storage;
    private final PremiumStorage premiumStorage;
    private final SeasonRewards seasonRewards;
    private final BpProgressListener progressListener;

    public BpGuiListener(BattlePassGui battlePassGui, BpQuestGui questGui,
                         BattlePassStorage storage, PremiumStorage premiumStorage,
                         SeasonRewards seasonRewards, BpProgressListener progressListener) {
        this.battlePassGui = battlePassGui;
        this.questGui = questGui;
        this.storage = storage;
        this.premiumStorage = premiumStorage;
        this.seasonRewards = seasonRewards;
        this.progressListener = progressListener;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        if (title.startsWith("§6§l✦ Battle Pass")) {
            event.setCancelled(true);
            handleBattlePassClick(player, event.getRawSlot());
        } else if (title.equals("§b§l⭐ Квесты Battle Pass")) {
            event.setCancelled(true);
            handleQuestGuiClick(player, event.getRawSlot());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        // Clean up page tracking when player closes the BP GUI
        String title = event.getView().getTitle();
        if (!title.startsWith("§6§l✦ Battle Pass")) {
            BattlePassGui.PLAYER_PAGE.remove(player.getUniqueId());
        }
    }

    private void handleBattlePassClick(Player player, int slot) {
        UUID uuid = player.getUniqueId();
        int page = BattlePassGui.PLAYER_PAGE.getOrDefault(uuid, 0);
        int totalPages = (int) Math.ceil(120.0 / LEVELS_PER_PAGE);

        switch (slot) {
            case 0 -> {
                // Prev page
                if (page > 0) {
                    battlePassGui.open(player, page - 1);
                }
            }
            case 8 -> {
                // Next page
                if (page < totalPages - 1) {
                    battlePassGui.open(player, page + 1);
                }
            }
            case 45 -> {
                // Open quest GUI
                questGui.open(player);
            }
            default -> {
                // Check free reward row (18-26) or premium reward row (27-35)
                if (slot >= 18 && slot <= 26) {
                    int index = slot - 18;
                    int level = page * LEVELS_PER_PAGE + index + 1;
                    if (level >= 1 && level <= 120) {
                        tryClaimFree(player, level);
                    }
                } else if (slot >= 27 && slot <= 35) {
                    int index = slot - 27;
                    int level = page * LEVELS_PER_PAGE + index + 1;
                    if (level >= 1 && level <= 120) {
                        tryClaimPremium(player, level);
                    }
                }
            }
        }
    }

    private void tryClaimFree(Player player, int level) {
        UUID uuid = player.getUniqueId();
        BattlePassData data = storage.get(uuid);
        if (data.getLevel() < level) {
            player.sendMessage("§c✦ Необходим уровень §e" + level + " §cдля получения этой награды.");
            return;
        }
        if (data.isFreeClaimed(level)) {
            player.sendMessage("§c✦ Эта награда уже получена.");
            return;
        }
        BpReward reward = seasonRewards.getFreeReward(level);
        if (reward == null) {
            player.sendMessage("§c✦ Нет награды для этого уровня.");
            return;
        }
        data.claimFree(level);
        storage.save(uuid);
        progressListener.giveReward(player, reward);
        battlePassGui.open(player, BattlePassGui.PLAYER_PAGE.getOrDefault(uuid, 0));
    }

    private void tryClaimPremium(Player player, int level) {
        UUID uuid = player.getUniqueId();
        if (!premiumStorage.hasPremium(uuid)) {
            player.sendMessage("§c✦ Требуется Premium для получения этой награды.");
            return;
        }
        BattlePassData data = storage.get(uuid);
        if (data.getLevel() < level) {
            player.sendMessage("§c✦ Необходим уровень §e" + level + " §cдля получения этой награды.");
            return;
        }
        if (data.isPremiumClaimed(level)) {
            player.sendMessage("§c✦ Эта Premium награда уже получена.");
            return;
        }
        BpReward reward = seasonRewards.getPremiumReward(level);
        if (reward == null) {
            player.sendMessage("§c✦ Нет Premium награды для этого уровня.");
            return;
        }
        data.claimPremium(level);
        storage.save(uuid);
        progressListener.giveReward(player, reward);
        battlePassGui.open(player, BattlePassGui.PLAYER_PAGE.getOrDefault(uuid, 0));
    }

    private void handleQuestGuiClick(Player player, int slot) {
        if (slot == 22) {
            player.closeInventory();
        }
    }
}
