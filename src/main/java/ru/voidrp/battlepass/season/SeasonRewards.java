package ru.voidrp.battlepass.season;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public final class SeasonRewards {

    private Map<Integer, BpReward> freeRewards = new HashMap<>();
    private Map<Integer, BpReward> premiumRewards = new HashMap<>();

    private final JavaPlugin plugin;
    private final Logger log;

    public SeasonRewards(JavaPlugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "rewards.yml");
        if (!file.exists()) {
            plugin.saveResource("rewards.yml", false);
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        freeRewards = loadTrack(cfg, "free");
        premiumRewards = loadTrack(cfg, "premium");
        log.info("[BattlePass] Loaded " + freeRewards.size() + " free rewards and "
                + premiumRewards.size() + " premium rewards.");
    }

    private Map<Integer, BpReward> loadTrack(YamlConfiguration cfg, String section) {
        Map<Integer, BpReward> map = new HashMap<>();
        if (!cfg.isConfigurationSection(section)) return map;
        for (String key : cfg.getConfigurationSection(section).getKeys(false)) {
            int level;
            try {
                level = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                log.warning("[BattlePass] Invalid reward level key '" + key + "' in section '" + section + "'");
                continue;
            }
            String path = section + "." + key;
            String typeStr = cfg.getString(path + ".type", "MONEY").toUpperCase();
            BpRewardType type;
            try {
                type = BpRewardType.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                log.warning("[BattlePass] Unknown reward type '" + typeStr + "' at level " + level);
                continue;
            }
            BpReward reward = switch (type) {
                case MONEY -> new BpReward(BpRewardType.MONEY, cfg.getDouble(path + ".amount", 0));
                case EXP -> new BpReward(BpRewardType.EXP, cfg.getDouble(path + ".amount", 0));
                case ITEM -> {
                    String mat = cfg.getString(path + ".material", "PAPER");
                    int count = cfg.getInt(path + ".count", 1);
                    String name = cfg.getString(path + ".displayName", mat);
                    yield new BpReward(mat, count, name);
                }
                case COMMAND -> {
                    String cmd = cfg.getString(path + ".command", "");
                    String name = cfg.getString(path + ".displayName", "Награда");
                    yield new BpReward(cmd, name);
                }
            };
            map.put(level, reward);
        }
        return Collections.unmodifiableMap(map);
    }

    public BpReward getFreeReward(int level) {
        return freeRewards.get(level);
    }

    public BpReward getPremiumReward(int level) {
        return premiumRewards.get(level);
    }

    public Map<Integer, BpReward> getAllFreeRewards() {
        return freeRewards;
    }

    public Map<Integer, BpReward> getAllPremiumRewards() {
        return premiumRewards;
    }
}
