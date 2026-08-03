package com.mycraftdailyshop.model;

import java.math.BigDecimal;

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

    public Offer(String cycleId, int index, String provider, String itemId, BigDecimal money, BigDecimal totalMoney,
                 int amount, int personalLimit, int serverLimit, double chance) {
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
}
