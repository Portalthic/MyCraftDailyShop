package com.mycraftdailyshop.integration;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface ItemProvider {
    String namespace();
    boolean exists(String itemId);
    ItemStack create(Player player, String itemId, int amount);
    boolean has(Player player, String itemId, int amount);
    boolean take(Player player, String itemId, int amount);
}
