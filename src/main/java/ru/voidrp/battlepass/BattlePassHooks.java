package ru.voidrp.battlepass;

import org.bukkit.entity.Player;

import java.util.function.Consumer;

/**
 * Static hooks for integration with other plugins (e.g. voidrp_daily_quests).
 * Set these consumers in BattlePassPlugin.onEnable after checking for the dependency.
 */
public final class BattlePassHooks {

    /** Called when a player completes a daily quest in VoidRpDailyQuests. Awards 600 XP. */
    public static Consumer<Player> onDailyQuestClaim;

    /** Called when a player completes a boss quest in VoidRpDailyQuests. Awards 2000 XP. */
    public static Consumer<Player> onBossQuestClaim;

    /** Called when a player completes a delivery quest in VoidRpDailyQuests. Awards 3000 XP. */
    public static Consumer<Player> onDeliveryQuestClaim;

    private BattlePassHooks() {}
}
