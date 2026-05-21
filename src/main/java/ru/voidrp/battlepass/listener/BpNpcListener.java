package ru.voidrp.battlepass.listener;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import ru.voidrp.battlepass.gui.BattlePassGui;

import java.util.List;

public final class BpNpcListener implements Listener {

    private final BattlePassGui battlePassGui;
    private final List<String> npcNames;

    public BpNpcListener(BattlePassGui battlePassGui, List<String> npcNames) {
        this.battlePassGui = battlePassGui;
        this.npcNames = npcNames;
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (entity.getCustomName() == null) return;

        String name = entity.getCustomName();
        if (!npcNames.contains(name)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        battlePassGui.open(player);
    }
}
