package com.mycraftdailyshop.model;

import java.util.Collections;
import java.util.List;

public final class EnchantmentChoiceConfig {
    private final String id;
    private final double weight;
    private final List<EnchantmentLevelConfig> levels;
    public EnchantmentChoiceConfig(String id, double weight, List<EnchantmentLevelConfig> levels) { this.id = id; this.weight = weight; this.levels = Collections.unmodifiableList(levels); }
    public String getId() { return id; }
    public double getWeight() { return weight; }
    public List<EnchantmentLevelConfig> getLevels() { return levels; }
}
