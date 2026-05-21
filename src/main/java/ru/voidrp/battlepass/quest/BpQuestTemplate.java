package ru.voidrp.battlepass.quest;

public final class BpQuestTemplate {

    private final String id;
    private final String displayName;
    private final String description;
    private final BpQuestType type;
    private final String target;
    private final int required;
    private final int xpReward;
    private final boolean premium;

    public BpQuestTemplate(String id, String displayName, String description,
                           BpQuestType type, String target, int required,
                           int xpReward, boolean premium) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.type = type;
        this.target = target;
        this.required = required;
        this.xpReward = xpReward;
        this.premium = premium;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public BpQuestType getType() { return type; }
    public String getTarget() { return target; }
    public int getRequired() { return required; }
    public int getXpReward() { return xpReward; }
    public boolean isPremium() { return premium; }
}
