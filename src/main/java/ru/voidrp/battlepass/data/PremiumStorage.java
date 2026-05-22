package ru.voidrp.battlepass.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class PremiumStorage {

    private static final long THIRTY_DAYS_MS = 30L * 24L * 60L * 60L * 1000L;

    private final File dir;
    private final Logger log;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private BackendSyncClient backend;

    /** uuid → expiresAt millis (0 = no premium) */
    private final ConcurrentHashMap<UUID, Long> cache = new ConcurrentHashMap<>();

    public PremiumStorage(File dir, Logger log) {
        this.dir = dir;
        dir.mkdirs();
        this.log = log;
    }

    public void setBackend(BackendSyncClient backend) {
        this.backend = backend;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    public boolean hasPremium(UUID uuid) {
        return getExpiry(uuid) > System.currentTimeMillis();
    }

    /** Called async on player join — syncs from backend and updates local cache/file. */
    public void syncFromBackend(UUID uuid, String nickname) {
        if (backend == null || !backend.isConfigured()) return;
        long expiry = backend.fetchPremiumExpiry(uuid.toString());
        if (expiry < 0) return; // backend unreachable — keep local value
        cache.put(uuid, expiry);
        saveToFile(uuid, expiry);
    }

    /** Grant premium for 30 days. Calls backend if configured, then saves locally. */
    public void grantPremium(UUID uuid, String nickname) {
        grantPremium(uuid, nickname, 30, null);
    }

    public void grantPremium(UUID uuid, String nickname, int days, String note) {
        // Persist locally immediately so the server thread never blocks
        long localExpiry = System.currentTimeMillis() + (long) days * 24 * 60 * 60 * 1000L;
        cache.put(uuid, localExpiry);
        saveToFile(uuid, localExpiry);

        // Sync to backend async; update cache if backend returns a different expiry
        if (backend != null && backend.isConfigured()) {
            CompletableFuture.supplyAsync(() -> backend.grantPremium(uuid.toString(), nickname, days, note))
                    .thenAccept(backendExpiry -> {
                        if (backendExpiry > 0 && backendExpiry != localExpiry) {
                            cache.put(uuid, backendExpiry);
                            saveToFile(uuid, backendExpiry);
                        }
                    });
        }
    }

    /** Revoke premium. Clears locally immediately, notifies backend async. */
    public void revokePremium(UUID uuid) {
        cache.put(uuid, 0L);
        saveToFile(uuid, 0L);
        if (backend != null && backend.isConfigured()) {
            CompletableFuture.runAsync(() -> backend.revokePremium(uuid.toString()));
        }
    }

    /** Returns expiry timestamp in millis, or 0 if no premium. Uses local cache/file. */
    public long getExpiry(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::loadFromFile);
    }

    /** Directly set expiry (used by RCON sync from backend — no backend call). */
    public void setExpiry(UUID uuid, long expiresAt) {
        cache.put(uuid, expiresAt);
        saveToFile(uuid, expiresAt);
    }

    public void evict(UUID uuid) {
        cache.remove(uuid);
    }

    // ── Internal ───────────────────────────────────────────────────────────────

    private long loadFromFile(UUID uuid) {
        File file = playerFile(uuid);
        if (!file.exists()) return 0L;
        try (FileReader reader = new FileReader(file)) {
            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
            return obj.has("expiresAt") ? obj.get("expiresAt").getAsLong() : 0L;
        } catch (Exception e) {
            log.warning("[BattlePass] Failed to load premium for " + uuid + ": " + e.getMessage());
            return 0L;
        }
    }

    private void saveToFile(UUID uuid, long expiresAt) {
        File file = playerFile(uuid);
        file.getParentFile().mkdirs();
        JsonObject obj = new JsonObject();
        obj.addProperty("expiresAt", expiresAt);
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(obj, writer);
        } catch (IOException e) {
            log.warning("[BattlePass] Failed to save premium for " + uuid + ": " + e.getMessage());
        }
    }

    private File playerFile(UUID uuid) {
        return new File(dir, uuid + ".json");
    }
}
