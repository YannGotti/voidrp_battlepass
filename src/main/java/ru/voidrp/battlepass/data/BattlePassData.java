package ru.voidrp.battlepass.data;

import java.util.HashSet;
import java.util.Set;

public final class BattlePassData {

    private static final int MAX_LEVEL = 120;
    private static final long XP_PER_LEVEL = 1000L;

    private String season;
    private long xp;
    private Set<Integer> claimedFree;
    private Set<Integer> claimedPremium;

    public BattlePassData(String season) {
        this.season = season;
        this.xp = 0;
        this.claimedFree = new HashSet<>();
        this.claimedPremium = new HashSet<>();
    }

    public BattlePassData(String season, long xp, Set<Integer> claimedFree, Set<Integer> claimedPremium) {
        this.season = season;
        this.xp = xp;
        this.claimedFree = new HashSet<>(claimedFree);
        this.claimedPremium = new HashSet<>(claimedPremium);
    }

    public String getSeason() { return season; }

    public long getXp() { return xp; }

    public void setXp(long xp) {
        this.xp = Math.max(0, xp);
    }

    /** Adds XP and returns the new level (1-120). */
    public int addXp(long amount) {
        this.xp += amount;
        return getLevel();
    }

    /** Returns current level 1-120 derived from XP. */
    public int getLevel() {
        int level = (int) (xp / XP_PER_LEVEL) + 1;
        return Math.min(level, MAX_LEVEL);
    }

    /** Returns XP needed to reach the next level, or 0 if at max level. */
    public long xpToNextLevel() {
        int level = getLevel();
        if (level >= MAX_LEVEL) return 0;
        long nextLevelXp = (long) level * XP_PER_LEVEL;
        return nextLevelXp - xp;
    }

    /** Returns XP progress within the current level (0 to XP_PER_LEVEL-1). */
    public long xpInCurrentLevel() {
        return xp % XP_PER_LEVEL;
    }

    public Set<Integer> getClaimedFree() { return claimedFree; }
    public Set<Integer> getClaimedPremium() { return claimedPremium; }

    public boolean isFreeClaimed(int level) { return claimedFree.contains(level); }
    public boolean isPremiumClaimed(int level) { return claimedPremium.contains(level); }

    public void claimFree(int level) { claimedFree.add(level); }
    public void claimPremium(int level) { claimedPremium.add(level); }
}
