package ru.voidrp.battlepass.season;

public final class BpReward {

    private final BpRewardType type;
    private final double amount;
    private final String material;
    private final int count;
    private final String displayName;
    private final String command;

    /** Constructor for MONEY / EXP rewards. */
    public BpReward(BpRewardType type, double amount) {
        this.type = type;
        this.amount = amount;
        this.material = null;
        this.count = 0;
        this.displayName = null;
        this.command = null;
    }

    /** Constructor for ITEM rewards. */
    public BpReward(String material, int count, String displayName) {
        this.type = BpRewardType.ITEM;
        this.amount = 0;
        this.material = material;
        this.count = count;
        this.displayName = displayName;
        this.command = null;
    }

    /** Constructor for COMMAND rewards. */
    public BpReward(String command, String displayName) {
        this.type = BpRewardType.COMMAND;
        this.amount = 0;
        this.material = null;
        this.count = 0;
        this.displayName = displayName;
        this.command = command;
    }

    public BpRewardType getType() { return type; }
    public double getAmount() { return amount; }
    public String getMaterial() { return material; }
    public int getCount() { return count; }
    public String getDisplayName() { return displayName; }
    public String getCommand() { return command; }
}
