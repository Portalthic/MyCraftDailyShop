package com.mycraftdailyshop.integration;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import com.mycraftdailyshop.model.EnchantmentRoll;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.ItemMeta;

public final class ZaphkielItemProvider implements ItemProvider {
    private final Object itemManager;
    private final Method getItem;
    private final Method generateItemStack;

    public ZaphkielItemProvider() throws ReflectiveOperationException {
        Class<?> zaphkiel = Class.forName("ink.ptms.zaphkiel.Zaphkiel");
        Field instanceField = zaphkiel.getField("INSTANCE");
        Object instance = instanceField.get(null);
        Object api = zaphkiel.getMethod("api").invoke(instance);
        itemManager = api.getClass().getMethod("getItemManager").invoke(api);
        getItem = itemManager.getClass().getMethod("getItem", String.class);
        generateItemStack = itemManager.getClass().getMethod("generateItemStack", String.class, Player.class);
    }

    @Override public String namespace() { return "zaphkiel"; }

    @Override public boolean exists(String itemId) {
        try { return getItem.invoke(itemManager, itemId) != null; }
        catch (ReflectiveOperationException ex) { return false; }
    }

    @Override public ItemStack create(Player player, String itemId, int amount) {
        try {
            ItemStack item = (ItemStack) generateItemStack.invoke(itemManager, itemId, player);
            if (item == null) return null;
            item.setAmount(Math.max(1, Math.min(amount, item.getMaxStackSize())));
            return item;
        } catch (ReflectiveOperationException ex) { throw new IllegalStateException("无法生成 Zaphkiel 物品 " + itemId, ex); }
    }

    @Override public ItemStack create(Player player, String itemId, int amount, List<EnchantmentRoll> enchantments) {
        ItemStack item = create(player, itemId, amount); if (item == null || enchantments.isEmpty()) return item;
        ItemMeta meta = item.getItemMeta(); if (meta == null) return item;
        for (EnchantmentRoll roll : enchantments) { Enchantment enchantment = Enchantment.getByName(roll.getId()); if (enchantment != null) meta.addEnchant(enchantment, roll.getLevel(), true); }
        item.setItemMeta(meta); return item;
    }

    @Override public boolean has(Player player, String itemId, int amount) {
        Object item = item(itemId);
        try { return item != null && (boolean) item.getClass().getMethod("hasItem", Player.class, int.class).invoke(item, player, amount); }
        catch (ReflectiveOperationException ex) { throw new IllegalStateException("无法检查 Zaphkiel 物品", ex); }
    }

    @Override public boolean take(Player player, String itemId, int amount) {
        Object item = item(itemId);
        try { return item != null && (boolean) item.getClass().getMethod("takeItem", Player.class, int.class).invoke(item, player, amount); }
        catch (ReflectiveOperationException ex) { throw new IllegalStateException("无法扣除 Zaphkiel 物品", ex); }
    }

    private Object item(String id) {
        try { return getItem.invoke(itemManager, id); }
        catch (ReflectiveOperationException ex) { throw new IllegalStateException("无法读取 Zaphkiel 物品", ex); }
    }
}
