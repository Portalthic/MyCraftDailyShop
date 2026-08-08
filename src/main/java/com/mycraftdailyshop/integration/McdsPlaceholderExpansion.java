package com.mycraftdailyshop.integration;

import com.mycraftdailyshop.model.ShopConfig;
import com.mycraftdailyshop.service.RefreshCycle;
import com.mycraftdailyshop.service.ShopRegistry;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class McdsPlaceholderExpansion extends PlaceholderExpansion {
    private static final String NEXT_REFRESH_PREFIX = "next_refresh_time_";

    private final JavaPlugin plugin;
    private final ShopRegistry registry;

    public McdsPlaceholderExpansion(JavaPlugin plugin, ShopRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    @Override public String getIdentifier() { return "mcds"; }
    @Override public String getAuthor() { return plugin.getDescription().getAuthors().isEmpty() ? "MyCraft" : plugin.getDescription().getAuthors().get(0); }
    @Override public String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override public String onPlaceholderRequest(Player player, String parameters) {
        if (parameters == null || !parameters.toLowerCase(Locale.ROOT).startsWith(NEXT_REFRESH_PREFIX)) return null;
        String shopId = parameters.substring(NEXT_REFRESH_PREFIX.length());
        ShopConfig shop = findShop(shopId);
        if (shop == null) return "";

        RefreshCycle cycle = registry.getRefreshCalculator().current(shop, System.currentTimeMillis());
        String pattern = plugin.getConfig().getString("placeholder.next-refresh-time-format", "yyyy-MM-dd HH:mm:ss");
        ZoneId zone = ZoneId.of(plugin.getConfig().getString("timezone", "Asia/Shanghai"));
        try {
            return Instant.ofEpochMilli(cycle.getEnd()).atZone(zone).format(DateTimeFormatter.ofPattern(pattern));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("placeholder.next-refresh-time-format 格式无效，已使用默认格式: " + pattern);
            return Instant.ofEpochMilli(cycle.getEnd()).atZone(zone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
    }

    private ShopConfig findShop(String id) {
        ShopConfig exact = registry.get(id);
        if (exact != null) return exact;
        for (ShopConfig shop : registry.all()) if (shop.getId().equalsIgnoreCase(id)) return shop;
        return null;
    }
}
