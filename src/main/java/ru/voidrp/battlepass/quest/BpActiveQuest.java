package ru.voidrp.battlepass.quest;

public final class BpActiveQuest {

    private final String templateId;
    private final String displayName;
    private final String description;
    private final BpQuestType type;
    private final String target;
    private final int required;
    private int progress;
    private final int xpReward;
    private boolean rewardClaimed;
    private final boolean isPremium;

    public BpActiveQuest(String templateId, String displayName, String description,
                         BpQuestType type, String target, int required,
                         int xpReward, boolean isPremium) {
        this.templateId = templateId;
        this.displayName = displayName;
        this.description = description;
        this.type = type;
        this.target = target;
        this.required = required;
        this.progress = 0;
        this.xpReward = xpReward;
        this.rewardClaimed = false;
        this.isPremium = isPremium;
    }

    public BpActiveQuest(String templateId, String displayName, String description,
                         BpQuestType type, String target, int required,
                         int progress, int xpReward, boolean rewardClaimed, boolean isPremium) {
        this.templateId = templateId;
        this.displayName = displayName;
        this.description = description;
        this.type = type;
        this.target = target;
        this.required = required;
        this.progress = progress;
        this.xpReward = xpReward;
        this.rewardClaimed = rewardClaimed;
        this.isPremium = isPremium;
    }

    public boolean isCompleted() { return progress >= required; }

    public void addProgress(int amount) {
        if (!rewardClaimed) {
            progress = Math.min(progress + amount, required);
        }
    }

    public String getTemplateId() { return templateId; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public BpQuestType getType() { return type; }
    public String getTarget() { return target; }
    public int getRequired() { return required; }
    public int getProgress() { return progress; }
    public int getXpReward() { return xpReward; }
    public boolean isRewardClaimed() { return rewardClaimed; }
    public boolean isPremium() { return isPremium; }

    public void setRewardClaimed(boolean rewardClaimed) { this.rewardClaimed = rewardClaimed; }
}
