package ru.voidrp.battlepass.quest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class BpQuestStorage {

    private static final int FREE_PER_DAY = 3;
    private static final int PREMIUM_PER_DAY = 3;

    private final File dir;
    private final Logger log;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /** uuid → player quest record for today */
    private final ConcurrentHashMap<UUID, PlayerQuestRecord> cache = new ConcurrentHashMap<>();

    public BpQuestStorage(File dir, Logger log) {
        this.dir = dir;
        dir.mkdirs();
        this.log = log;
    }

    /** Returns today's quests for the player, generating them if needed. */
    public PlayerQuestRecord getToday(UUID uuid) {
        return cache.computeIfAbsent(uuid, id -> {
            PlayerQuestRecord rec = loadFromFile(id);
            String today = LocalDate.now().toString();
            if (rec == null || !rec.getDate().equals(today)) {
                rec = generateForToday(today);
                saveToFile(id, rec);
            }
            return rec;
        }).ensureDate(uuid, this);
    }

    PlayerQuestRecord generateForToday(String date) {
        long seed = LocalDate.now().toEpochDay(); // same for all players
        List<BpQuestTemplate> freeTemplates = BpQuestPool.pickFree(seed, FREE_PER_DAY);
        List<BpQuestTemplate> premTemplates = BpQuestPool.pickPremium(seed + 9999L, PREMIUM_PER_DAY);

        List<BpActiveQuest> freeQuests = new ArrayList<>();
        for (BpQuestTemplate t : freeTemplates) {
            freeQuests.add(new BpActiveQuest(t.getId(), t.getDisplayName(), t.getDescription(),
                    t.getType(), t.getTarget(), t.getRequired(), t.getXpReward(), false));
        }
        List<BpActiveQuest> premQuests = new ArrayList<>();
        for (BpQuestTemplate t : premTemplates) {
            premQuests.add(new BpActiveQuest(t.getId(), t.getDisplayName(), t.getDescription(),
                    t.getType(), t.getTarget(), t.getRequired(), t.getXpReward(), true));
        }
        return new PlayerQuestRecord(date, freeQuests, premQuests);
    }

    public void save(UUID uuid) {
        PlayerQuestRecord rec = cache.get(uuid);
        if (rec != null) saveToFile(uuid, rec);
    }

    public void saveAll() {
        for (Map.Entry<UUID, PlayerQuestRecord> entry : cache.entrySet()) {
            saveToFile(entry.getKey(), entry.getValue());
        }
    }

    private PlayerQuestRecord loadFromFile(UUID uuid) {
        File file = playerFile(uuid);
        if (!file.exists()) return null;
        try (FileReader reader = new FileReader(file)) {
            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
            String date = obj.has("date") ? obj.get("date").getAsString() : "";
            List<BpActiveQuest> freeQ = parseQuests(obj, "freeQuests", false);
            List<BpActiveQuest> premQ = parseQuests(obj, "premiumQuests", true);
            return new PlayerQuestRecord(date, freeQ, premQ);
        } catch (Exception e) {
            log.warning("[BattlePass] Failed to load bp-quests for " + uuid + ": " + e.getMessage());
            return null;
        }
    }

    private List<BpActiveQuest> parseQuests(JsonObject obj, String key, boolean premium) {
        List<BpActiveQuest> list = new ArrayList<>();
        if (!obj.has(key) || !obj.get(key).isJsonArray()) return list;
        for (JsonElement el : obj.getAsJsonArray(key)) {
            JsonObject q = el.getAsJsonObject();
            try {
                String templateId = q.get("templateId").getAsString();
                String displayName = q.get("displayName").getAsString();
                String description = q.get("description").getAsString();
                BpQuestType type = BpQuestType.valueOf(q.get("type").getAsString());
                String target = q.get("target").getAsString();
                int required = q.get("required").getAsInt();
                int progress = q.has("progress") ? q.get("progress").getAsInt() : 0;
                int xpReward = q.get("xpReward").getAsInt();
                boolean claimed = q.has("rewardClaimed") && q.get("rewardClaimed").getAsBoolean();
                list.add(new BpActiveQuest(templateId, displayName, description,
                        type, target, required, progress, xpReward, claimed, premium));
            } catch (Exception e) {
                log.warning("[BattlePass] Skipping malformed quest entry: " + e.getMessage());
            }
        }
        return list;
    }

    void saveToFile(UUID uuid, PlayerQuestRecord rec) {
        File file = playerFile(uuid);
        file.getParentFile().mkdirs();
        JsonObject obj = new JsonObject();
        obj.addProperty("date", rec.getDate());
        obj.add("freeQuests", serializeQuests(rec.getFreeQuests()));
        obj.add("premiumQuests", serializeQuests(rec.getPremiumQuests()));
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(obj, writer);
        } catch (IOException e) {
            log.warning("[BattlePass] Failed to save bp-quests for " + uuid + ": " + e.getMessage());
        }
    }

    private JsonArray serializeQuests(List<BpActiveQuest> quests) {
        JsonArray arr = new JsonArray();
        for (BpActiveQuest q : quests) {
            JsonObject o = new JsonObject();
            o.addProperty("templateId", q.getTemplateId());
            o.addProperty("displayName", q.getDisplayName());
            o.addProperty("description", q.getDescription());
            o.addProperty("type", q.getType().name());
            o.addProperty("target", q.getTarget());
            o.addProperty("required", q.getRequired());
            o.addProperty("progress", q.getProgress());
            o.addProperty("xpReward", q.getXpReward());
            o.addProperty("rewardClaimed", q.isRewardClaimed());
            arr.add(o);
        }
        return arr;
    }

    private File playerFile(UUID uuid) {
        return new File(dir, uuid + ".json");
    }

    // ── Inner record class ───────────────────────────────────────────────────

    public final class PlayerQuestRecord {
        private final String date;
        private final List<BpActiveQuest> freeQuests;
        private final List<BpActiveQuest> premiumQuests;

        public PlayerQuestRecord(String date, List<BpActiveQuest> freeQuests, List<BpActiveQuest> premiumQuests) {
            this.date = date;
            this.freeQuests = freeQuests;
            this.premiumQuests = premiumQuests;
        }

        /** Checks if date changed (day rollover) and regenerates if so. */
        public PlayerQuestRecord ensureDate(UUID uuid, BpQuestStorage storage) {
            String today = LocalDate.now().toString();
            if (!today.equals(date)) {
                PlayerQuestRecord fresh = storage.generateForToday(today);
                storage.cache.put(uuid, fresh);
                storage.saveToFile(uuid, fresh);
                return fresh;
            }
            return this;
        }

        public String getDate() { return date; }
        public List<BpActiveQuest> getFreeQuests() { return freeQuests; }
        public List<BpActiveQuest> getPremiumQuests() { return premiumQuests; }
    }
}
