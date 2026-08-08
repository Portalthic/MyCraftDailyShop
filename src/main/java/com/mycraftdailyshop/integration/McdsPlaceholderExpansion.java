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
    private static final String NEXT_REFRESH_REMAINING_PREFIX = "next_refresh_remaining_";
    private static final String NEXT_REFRESH_TIMESTAMP_PREFIX = "next_refresh_timestamp_";

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
        if (parameters == null) return null;
        String lower = parameters.toLowerCase(Locale.ROOT);
        String prefix;
        int mode;
        if (lower.startsWith(NEXT_REFRESH_PREFIX)) { prefix = NEXT_REFRESH_PREFIX; mode = 0; }
        else if (lower.startsWith(NEXT_REFRESH_REMAINING_PREFIX)) { prefix = NEXT_REFRESH_REMAINING_PREFIX; mode = 1; }
        else if (lower.startsWith(NEXT_REFRESH_TIMESTAMP_PREFIX)) { prefix = NEXT_REFRESH_TIMESTAMP_PREFIX; mode = 2; }
        else return null;
        String shopId = parameters.substring(prefix.length());
        ShopConfig shop = findShop(shopId);
        if (shop == null) return "";

        RefreshCycle cycle = registry.getRefreshCalculator().current(shop, System.currentTimeMillis());
        if (mode == 1) return formatRemaining(Math.max(0L, cycle.getEnd() - System.currentTimeMillis()));
        if (mode == 2) return Long.toString(Math.floorDiv(cycle.getEnd(), 1000L));
        String pattern = plugin.getConfig().getString("placeholder.next-refresh-time-format", "yyyy-MM-dd HH:mm:ss");
        ZoneId zone = ZoneId.of(plugin.getConfig().getString("timezone", "Asia/Shanghai"));
        try {
            return Instant.ofEpochMilli(cycle.getEnd()).atZone(zone).format(DateTimeFormatter.ofPattern(pattern));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("placeholder.next-refresh-time-format 格式无效，已使用默认格式: " + pattern);
            return Instant.ofEpochMilli(cycle.getEnd()).atZone(zone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
    }

    private String formatRemaining(long millis) {
        long seconds = (millis + 999L) / 1000L;
        long days = seconds / 86400L;
        seconds %= 86400L;
        long hours = seconds / 3600L;
        seconds %= 3600L;
        long minutes = seconds / 60L;
        seconds %= 60L;
        String clock = String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds);
        return days > 0L ? days + "天 " + clock : clock;
    }

    private ShopConfig findShop(String id) {
        ShopConfig exact = registry.get(id);
        if (exact != null) return exact;
        for (ShopConfig shop : registry.all()) if (shop.getId().equalsIgnoreCase(id)) return shop;
        return null;
    }
}
