package com.mycraftdailyshop.model;

import java.util.Collections;
import java.util.List;

public final class EnchantmentGroupConfig {
    private final double chance;
    private final List<EnchantmentChoiceConfig> choices;
    public EnchantmentGroupConfig(double chance, List<EnchantmentChoiceConfig> choices) { this.chance = chance; this.choices = Collections.unmodifiableList(choices); }
    public double getChance() { return chance; }
    public List<EnchantmentChoiceConfig> getChoices() { return choices; }
}
