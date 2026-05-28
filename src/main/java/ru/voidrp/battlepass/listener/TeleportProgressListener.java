package ru.voidrp.battlepass.listener;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shows a boss bar while a teleport command loads chunks.
 * Hooks at the Bukkit level so it catches EssentialsX async teleports
 * that bypass the NeoForge TeleportInterceptMixin.
 */
public final class TeleportProgressListener implements Listener {

    private static final int MAX_TICKS = 300;              // 15 sec safety cap
    private static final double MIN_DISTANCE_SQ = 50.0 * 50.0;

    // Prefix-matched commands that should show the bar (lower-case, no slash)
    private static final String[] TELEPORT_COMMANDS = {
            "home", "h", "spawn", "back", "tpa", "tpaccept", "tp",
            "warp", "rtp", "f home", "nation home", "n home"
    };

    private final Plugin plugin;
    // uuid → active boss bar (bar is removed from this map as soon as teleport fires)
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();

    public TeleportProgressListener(Plugin plugin) {
        this.plugin = plugin;
    }

    // ── Command intercept ─────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage();
        if (msg == null || msg.isEmpty()) return;
        String cmd = msg.startsWith("/") ? msg.substring(1).toLowerCase().trim() : msg.toLowerCase().trim();

        for (String prefix : TELEPORT_COMMANDS) {
            if (cmd.equals(prefix) || cmd.startsWith(prefix + " ")) {
                showBar(event.getPlayer());
                return;
            }
        }
    }

    // ── Teleport complete ─────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (!bars.containsKey(uuid)) return;

        if (event.isCancelled()) {
            removeBar(uuid);
            return;
        }

        boolean crossWorld = event.getFrom().getWorld() != event.getTo().getWorld();
        boolean farAway = crossWorld || event.getFrom().distanceSquared(event.getTo()) >= MIN_DISTANCE_SQ;
        if (!farAway) {
            removeBar(uuid);
            return;
        }

        // Remove from map first — the running timer will see it gone and self-cancel
        BossBar bar = bars.remove(uuid);
        if (bar != null) {
            bar.setColor(BarColor.GREEN);
            bar.setTitle("§a✔ Телепортация выполнена!");
            bar.setProgress(1.0);
            Bukkit.getScheduler().runTaskLater(plugin, bar::removeAll, 40L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeBar(event.getPlayer().getUniqueId());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void showBar(Player player) {
        UUID uuid = player.getUniqueId();
        removeBar(uuid);

        BossBar bar = Bukkit.createBossBar("§e⏳ Прогружаю чанки...", BarColor.YELLOW, BarStyle.SOLID);
        bar.setProgress(0.0);
        bar.addPlayer(player);
        bars.put(uuid, bar);

        int[] ticks = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            // Bar was removed (teleport fired or player quit) — stop the timer
            if (!bars.containsKey(uuid) || bars.get(uuid) != bar) {
                task.cancel();
                return;
            }
            ticks[0]++;
            bar.setProgress(Math.min(1.0, (double) ticks[0] / MAX_TICKS));
            if (ticks[0] >= MAX_TICKS) {
                removeBar(uuid);
                task.cancel();
            }
        }, 1L, 1L);
    }

    private void removeBar(UUID uuid) {
        BossBar bar = bars.remove(uuid);
        if (bar != null) bar.removeAll();
    }
}
