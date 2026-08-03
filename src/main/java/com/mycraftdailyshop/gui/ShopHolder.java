package com.mycraftdailyshop.gui;

import com.mycraftdailyshop.model.ShopConfig;
import com.mycraftdailyshop.model.ShopSnapshot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class ShopHolder implements InventoryHolder {
    private final ShopConfig shop;
    private final ShopSnapshot snapshot;
    private final boolean catalog;
    private Inventory inventory;
    private int page;
    private SortMode sort = SortMode.INDEX;
    private boolean descending;
    private boolean trading;
    private long renderVersion;

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
    public boolean isTrading() { return trading; }
    public void setTrading(boolean trading) { this.trading = trading; }
    public long nextRenderVersion() { return ++renderVersion; }
    public long getRenderVersion() { return renderVersion; }
}
