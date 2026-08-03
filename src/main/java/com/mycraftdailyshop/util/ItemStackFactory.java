package com.mycraftdailyshop.util;

import com.mycraftdailyshop.service.MessageService;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class ItemStackFactory {
    private ItemStackFactory() { }

    public static ItemStack fromConfig(Player player, ConfigurationSection display, MessageService messages, Map<String, String> vars) {
        Material material = Material.matchMaterial(display.getString("material", "STONE").toUpperCase());
        if (material == null) material = Material.STONE;
        int amount = Math.max(1, Math.min(64, display.getInt("amount", 1)));
        ItemStack item = new ItemStack(material, amount, (short) display.getInt("data", 0));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (display.contains("name")) meta.setDisplayName(messages.format(player, display.getString("name"), vars));
            List<String> lore = new ArrayList<>();
            for (String line : display.getStringList("lore")) lore.add(messages.format(player, line, vars));
            if (!lore.isEmpty()) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        ConfigurationSection nbt = display.getConfigurationSection("nbt");
        return nbt == null ? item : applyNbt(item, nbt);
    }

    public static ItemStack appendLore(ItemStack source, List<String> extra) {
        ItemStack item = source.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.addAll(extra);
        meta.setLore(lore);
        item.setItemMeta(meta);
        item.setAmount(1);
        return item;
    }

    private static ItemStack applyNbt(ItemStack item, ConfigurationSection section) {
        try {
            Class<?> craft = Class.forName("org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack");
            Class<?> nmsItem = Class.forName("net.minecraft.server.v1_12_R1.ItemStack");
            Class<?> compound = Class.forName("net.minecraft.server.v1_12_R1.NBTTagCompound");
            Object nms = craft.getMethod("asNMSCopy", ItemStack.class).invoke(null, item);
            Method getTag = nmsItem.getMethod("getTag");
            Object tag = getTag.invoke(nms);
            if (tag == null) { Constructor<?> constructor = compound.getConstructor(); tag = constructor.newInstance(); }
            Method setString = compound.getMethod("setString", String.class, String.class);
            for (String key : section.getKeys(false)) setString.invoke(tag, key, String.valueOf(section.get(key)));
            nmsItem.getMethod("setTag", compound).invoke(nms, tag);
            return (ItemStack) craft.getMethod("asBukkitCopy", nmsItem).invoke(null, nms);
        } catch (ReflectiveOperationException ex) {
            return item;
        }
    }
}
