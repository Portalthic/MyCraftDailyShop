package com.mycraftdailyshop.model;

public final class EnchantmentLevelConfig {
    private final int level;
    private final double weight;
    private final ValueSpec premium;
    public EnchantmentLevelConfig(int level, double weight, ValueSpec premium) { this.level = level; this.weight = weight; this.premium = premium; }
    public int getLevel() { return level; }
    public double getWeight() { return weight; }
    public ValueSpec getPremium() { return premium; }
}
