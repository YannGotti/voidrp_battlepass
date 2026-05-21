package ru.voidrp.battlepass.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
            // Premium badge — non-premium players get a clickable shop link
            case 6, 7, 8 -> {
                if (!premiumStorage.hasPremium(uuid)) {
                    player.closeInventory();
                    Component msg = Component.empty()
                            .append(Component.text("✦ Premium Battle Pass: ", NamedTextColor.GOLD))
                            .append(Component.text("[Купить на сайте]")
                                    .color(NamedTextColor.YELLOW)
                                    .decorate(TextDecoration.UNDERLINED)
                                    .clickEvent(ClickEvent.openUrl("https://void-rp.ru/shop"))
                                    .hoverEvent(HoverEvent.showText(
                                            Component.text("Нажмите чтобы открыть магазин в браузере"))));
                    player.sendMessage(msg);
                }
            }
            // Prev page
            case 45 -> {
                if (page > 0) {
                    battlePassGui.open(player, page - 1);
                }
            }
            // Quests button
            case 48 -> questGui.open(player);
            // Info book
            case 50 -> sendBattlePassInfo(player);
            // Next page
            case 53 -> {
                if (page < totalPages - 1) {
                    battlePassGui.open(player, page + 1);
                }
            }
            default -> {
                // Free reward row (18-26)
                if (slot >= 18 && slot <= 26) {
                    int index = slot - 18;
                    int level = page * LEVELS_PER_PAGE + index + 1;
                    if (level >= 1 && level <= 120) {
                        tryClaimFree(player, level);
                    }
                // Premium reward row (27-35)
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

    private void sendBattlePassInfo(Player player) {
        player.sendMessage("§6§l══════════ Battle Pass — Инструкция ══════════");
        player.sendMessage("§e§l📌 Источники XP:");
        player.sendMessage("§7  • §aЕжедневный квест §8— §e600 XP");
        player.sendMessage("§7  • §aБосс-квест §8— §e2000 XP");
        player.sendMessage("§7  • §aКвест доставки §8— §e3000 XP");
        player.sendMessage("§7  • §6Активности на сервере §8— §eразличное кол-во XP");
        player.sendMessage(" ");
        player.sendMessage("§b§l⭐ Преимущества Premium:");
        player.sendMessage("§7  • §bВторая полоса наград §7на каждом уровне");
        player.sendMessage("§7  • §bБольше монет §7и редкие предметы");
        player.sendMessage("§7  • §bЭксклюзивные §7предметы из модов");
        player.sendMessage("§7  • §bТотемы бессмертия §7и §bслитки незерита");
        player.sendMessage(" ");

        Component shopLink = Component.empty()
                .append(Component.text("§6▶ Купить Premium: "))
                .append(Component.text("[void-rp.ru/shop]")
                        .color(NamedTextColor.YELLOW)
                        .decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl("https://void-rp.ru/shop"))
                        .hoverEvent(HoverEvent.showText(
                                Component.text("Нажмите чтобы открыть магазин"))));
        player.sendMessage(shopLink);
        player.sendMessage("§6§l══════════════════════════════════════════════");
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
