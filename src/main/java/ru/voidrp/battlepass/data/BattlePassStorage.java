package ru.voidrp.battlepass.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ru.voidrp.battlepass.season.Season;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class BattlePassStorage {

    private final File baseDir;
    private final Logger log;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /** season key → (uuid → data) */
    private final ConcurrentHashMap<String, ConcurrentHashMap<UUID, BattlePassData>> cache
            = new ConcurrentHashMap<>();

    public BattlePassStorage(File baseDir, Logger log) {
        this.baseDir = baseDir;
        baseDir.mkdirs();
        this.log = log;
    }

    public BattlePassData get(UUID uuid) {
        String season = Season.currentKey();
        return cache
                .computeIfAbsent(season, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(uuid, id -> load(id, season));
    }

    /** Adds XP for the player, returns old level so caller can detect level-up. */
    public int addXp(UUID uuid, long amount) {
        BattlePassData data = get(uuid);
        int oldLevel = data.getLevel();
        data.addXp(amount);
        return oldLevel;
    }

    /** Sets xp to (level-1)*1000, clamped. */
    public void setLevel(UUID uuid, int level) {
        BattlePassData data = get(uuid);
        int clamped = Math.max(1, Math.min(120, level));
        data.setXp((long) (clamped - 1) * 1000L);
    }

    public void save(UUID uuid) {
        String season = Season.currentKey();
        BattlePassData data = cache.getOrDefault(season, new ConcurrentHashMap<>()).get(uuid);
        if (data != null) saveToFile(uuid, season, data);
    }

    public void saveAll() {
        for (Map.Entry<String, ConcurrentHashMap<UUID, BattlePassData>> seasonEntry : cache.entrySet()) {
            for (Map.Entry<UUID, BattlePassData> playerEntry : seasonEntry.getValue().entrySet()) {
                saveToFile(playerEntry.getKey(), seasonEntry.getKey(), playerEntry.getValue());
            }
        }
    }

    /** Clears the in-memory cache for old seasons (data is on disk). */
    public void clearOldSeasonCache(String currentSeason) {
        cache.keySet().removeIf(k -> !k.equals(currentSeason));
    }

    private BattlePassData load(UUID uuid, String season) {
        File file = playerFile(uuid, season);
        if (!file.exists()) return new BattlePassData(season);
        try (FileReader reader = new FileReader(file)) {
            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
            long xp = obj.has("xp") ? obj.get("xp").getAsLong() : 0L;
            Set<Integer> claimedFree = parseIntSet(obj, "claimedFree");
            Set<Integer> claimedPremium = parseIntSet(obj, "claimedPremium");
            return new BattlePassData(season, xp, claimedFree, claimedPremium);
        } catch (Exception e) {
            log.warning("[BattlePass] Failed to load data for " + uuid + " season " + season + ": " + e.getMessage());
            return new BattlePassData(season);
        }
    }

    private void saveToFile(UUID uuid, String season, BattlePassData data) {
        File file = playerFile(uuid, season);
        file.getParentFile().mkdirs();
        JsonObject obj = new JsonObject();
        obj.addProperty("season", data.getSeason());
        obj.addProperty("xp", data.getXp());
        JsonArray freeArr = new JsonArray();
        for (int lvl : data.getClaimedFree()) freeArr.add(lvl);
        obj.add("claimedFree", freeArr);
        JsonArray premArr = new JsonArray();
        for (int lvl : data.getClaimedPremium()) premArr.add(lvl);
        obj.add("claimedPremium", premArr);
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(obj, writer);
        } catch (IOException e) {
            log.warning("[BattlePass] Failed to save data for " + uuid + ": " + e.getMessage());
        }
    }

    private Set<Integer> parseIntSet(JsonObject obj, String key) {
        Set<Integer> set = new HashSet<>();
        if (obj.has(key) && obj.get(key).isJsonArray()) {
            for (var el : obj.getAsJsonArray(key)) {
                set.add(el.getAsInt());
            }
        }
        return set;
    }

    private File playerFile(UUID uuid, String season) {
        return new File(baseDir, "seasons" + File.separator + season + File.separator + uuid + ".json");
    }
}
