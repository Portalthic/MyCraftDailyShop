package com.mycraftdailyshop.model;

import java.math.BigDecimal;

public final class EnchantmentRoll {
    private final String id;
    private final int level;
    private final BigDecimal premium;
    public EnchantmentRoll(String id, int level, BigDecimal premium) { this.id = id; this.level = level; this.premium = premium; }
    public String getId() { return id; }
    public int getLevel() { return level; }
    public BigDecimal getPremium() { return premium; }
}
