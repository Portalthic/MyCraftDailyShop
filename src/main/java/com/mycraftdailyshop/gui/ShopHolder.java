package com.mycraftdailyshop.gui;

import com.mycraftdailyshop.model.ShopConfig;
import com.mycraftdailyshop.model.ShopSnapshot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;

public final class ShopHolder implements InventoryHolder {
    private final ShopConfig shop;
    private final ShopSnapshot snapshot;
    private final boolean catalog;
    private Inventory inventory;
    private int page;
    private SortMode sort = SortMode.INDEX;
    private boolean descending;
    private boolean trading;
    private boolean usageRefreshing;
    private boolean snapshotRefreshing;
    private final Map<Integer, int[]> usage = new HashMap<>();

    public ShopHolder(ShopConfig shop, ShopSnapshot snapshot, boolean catalog) {
        this.shop = shop; this.snapshot = snapshot; this.catalog = catalog;
    }
    @Override public Inventory getInventory() { return inventory; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }
    public ShopConfig getShop() { return shop; }
    public ShopSnapshot getSnapshot() { return snapshot; }
    public boolean isCatalog() { return catalog; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public SortMode getSort() { return sort; }
    public void nextSort() { sort = sort.next(); }
    public boolean isDescending() { return descending; }
    public void toggleDescending() { descending = !descending; }
    public void copyViewStateFrom(ShopHolder other) { page = other.page; sort = other.sort; descending = other.descending; }
    public boolean isTrading() { return trading; }
    public void setTrading(boolean trading) { this.trading = trading; }
    public boolean isUsageRefreshing() { return usageRefreshing; }
    public void setUsageRefreshing(boolean usageRefreshing) { this.usageRefreshing = usageRefreshing; }
    public boolean isSnapshotRefreshing() { return snapshotRefreshing; }
    public void setSnapshotRefreshing(boolean snapshotRefreshing) { this.snapshotRefreshing = snapshotRefreshing; }
    public int[] getUsage(int offerIndex) { return usage.getOrDefault(offerIndex, new int[]{0, 0}); }
    public void putUsage(int offerIndex, int personal, int server) { usage.put(offerIndex, new int[]{personal, server}); }
    public void putAllUsage(Map<Integer, int[]> values) { usage.putAll(values); }
    public boolean replaceUsage(Map<Integer, int[]> values) {
        boolean changed = usage.size() != values.size();
        if (!changed) for (Map.Entry<Integer, int[]> entry : values.entrySet()) {
            if (!Arrays.equals(usage.get(entry.getKey()), entry.getValue())) { changed = true; break; }
        }
        if (changed) { usage.clear(); usage.putAll(values); }
        return changed;
    }
}
