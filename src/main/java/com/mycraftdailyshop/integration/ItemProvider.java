package com.mycraftdailyshop.integration;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import com.mycraftdailyshop.model.EnchantmentRoll;
import java.util.List;

public interface ItemProvider {
    String namespace();
    boolean exists(String itemId);
    ItemStack create(Player player, String itemId, int amount);
    default ItemStack create(Player player, String itemId, int amount, List<EnchantmentRoll> enchantments) { return create(player, itemId, amount); }
    boolean has(Player player, String itemId, int amount);
    boolean take(Player player, String itemId, int amount);
}
