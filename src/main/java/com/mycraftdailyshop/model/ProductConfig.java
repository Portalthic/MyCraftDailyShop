package com.mycraftdailyshop.model;

import java.util.Collections;
import java.util.List;

public final class ProductConfig {
    private final int index;
    private final String provider;
    private final String itemId;
    private final ValueSpec money;
    private final ValueSpec amount;
    private final ValueSpec personalLimit;
    private final ValueSpec serverLimit;
    private final double chance;
    private final List<EnchantmentGroupConfig> enchantments;

    public ProductConfig(int index, String namespacedId, ValueSpec money, ValueSpec amount,
                         ValueSpec personalLimit, ValueSpec serverLimit, double chance) {
        this(index, namespacedId, money, amount, personalLimit, serverLimit, chance, Collections.emptyList());
    }

    public ProductConfig(int index, String namespacedId, ValueSpec money, ValueSpec amount,
                         ValueSpec personalLimit, ValueSpec serverLimit, double chance, List<EnchantmentGroupConfig> enchantments) {
        int colon = namespacedId.indexOf(':');
        if (colon < 1 || colon == namespacedId.length() - 1) throw new IllegalArgumentException("商品 ID 必须使用 provider:item 格式");
        if (Double.isNaN(chance) || Double.isInfinite(chance) || chance < 0D || chance > 1D) throw new IllegalArgumentException("chance 必须在 0 到 1 之间");
        this.index = index;
        this.provider = namespacedId.substring(0, colon).toLowerCase();
        this.itemId = namespacedId.substring(colon + 1);
        this.money = money;
        this.amount = amount;
        this.personalLimit = personalLimit;
        this.serverLimit = serverLimit;
        this.chance = chance;
        this.enchantments = Collections.unmodifiableList(enchantments);
    }

    public int getIndex() { return index; }
    public String getProvider() { return provider; }
    public String getItemId() { return itemId; }
    public ValueSpec getMoney() { return money; }
    public ValueSpec getAmount() { return amount; }
    public ValueSpec getPersonalLimit() { return personalLimit; }
    public ValueSpec getServerLimit() { return serverLimit; }
    public double getChance() { return chance; }
    public List<EnchantmentGroupConfig> getEnchantments() { return enchantments; }
}
