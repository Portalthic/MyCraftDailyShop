package com.mycraftdailyshop.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

public final class Offer {
    private final String cycleId;
    private final int index;
    private final String provider;
    private final String itemId;
    private final BigDecimal money;
    private final BigDecimal totalMoney;
    private final int amount;
    private final int personalLimit;
    private final int serverLimit;
    private final double chance;
    private final BigDecimal baseMoney;
    private final BigDecimal premium;
    private final List<EnchantmentRoll> enchantments;

    public Offer(String cycleId, int index, String provider, String itemId, BigDecimal money, BigDecimal totalMoney,
                 int amount, int personalLimit, int serverLimit, double chance) {
        this(cycleId, index, provider, itemId, money, totalMoney, amount, personalLimit, serverLimit, chance, money, BigDecimal.ZERO, Collections.emptyList());
    }
    public Offer(String cycleId, int index, String provider, String itemId, BigDecimal money, BigDecimal totalMoney,
                 int amount, int personalLimit, int serverLimit, double chance, BigDecimal baseMoney, BigDecimal premium, List<EnchantmentRoll> enchantments) {
        this.cycleId = cycleId;
        this.index = index;
        this.provider = provider;
        this.itemId = itemId;
        this.money = money;
        this.totalMoney = totalMoney;
        this.amount = amount;
        this.personalLimit = personalLimit;
        this.serverLimit = serverLimit;
        this.chance = chance;
        this.baseMoney = baseMoney;
        this.premium = premium;
        this.enchantments = Collections.unmodifiableList(enchantments);
    }

    public String getCycleId() { return cycleId; }
    public int getIndex() { return index; }
    public String getProvider() { return provider; }
    public String getItemId() { return itemId; }
    public BigDecimal getMoney() { return money; }
    public BigDecimal getTotalMoney() { return totalMoney; }
    public int getAmount() { return amount; }
    public int getPersonalLimit() { return personalLimit; }
    public int getServerLimit() { return serverLimit; }
    public double getChance() { return chance; }
    public BigDecimal getBaseMoney() { return baseMoney; }
    public BigDecimal getPremium() { return premium; }
    public List<EnchantmentRoll> getEnchantments() { return enchantments; }
}
