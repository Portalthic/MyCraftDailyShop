package com.mycraftdailyshop.model;

import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class ShopConfig {
    private final String id;
    private final ShopType type;
    private final ShopScene scene;
    private final String refreshType;
    private final LocalTime refreshTime;
    private final long refreshIntervalMillis;
    private final String restockMessage;
    private final String noShopsMessage;
    private final String openSound;
    private final String successSound;
    private final String failSound;
    private final String title;
    private final List<String> layout;
    private final Map<Character, IconConfig> icons;
    private final List<ProductConfig> products;

    public ShopConfig(String id, ShopType type, ShopScene scene, String refreshType, LocalTime refreshTime, long refreshIntervalMillis,
                      String restockMessage, String noShopsMessage, String openSound, String successSound,
                      String failSound, String title, List<String> layout, Map<Character, IconConfig> icons,
                      List<ProductConfig> products) {
        this.id = id;
        this.type = type;
        this.scene = scene;
        this.refreshType = refreshType;
        this.refreshTime = refreshTime;
        this.refreshIntervalMillis = refreshIntervalMillis;
        this.restockMessage = restockMessage;
        this.noShopsMessage = noShopsMessage;
        this.openSound = openSound;
        this.successSound = successSound;
        this.failSound = failSound;
        this.title = title;
        this.layout = Collections.unmodifiableList(layout);
        this.icons = Collections.unmodifiableMap(icons);
        this.products = Collections.unmodifiableList(products);
    }

    public String getId() { return id; }
    public ShopType getType() { return type; }
    public ShopScene getScene() { return scene; }
    public String getRefreshType() { return refreshType; }
    public LocalTime getRefreshTime() { return refreshTime; }
    public long getRefreshIntervalMillis() { return refreshIntervalMillis; }
    public String getRestockMessage() { return restockMessage; }
    public String getNoShopsMessage() { return noShopsMessage; }
    public String getOpenSound() { return openSound; }
    public String getSuccessSound() { return successSound; }
    public String getFailSound() { return failSound; }
    public String getTitle() { return title; }
    public List<String> getLayout() { return layout; }
    public Map<Character, IconConfig> getIcons() { return icons; }
    public List<ProductConfig> getProducts() { return products; }
    public int getSize() { return layout.size() * 9; }
    public int getProductSlotsPerPage() {
        int count = 0;
        for (String row : layout) for (int i = 0; i < row.length(); i++) {
            IconConfig icon = icons.get(row.charAt(i));
            if (icon != null && icon.getType() == IconType.SHOPS) count++;
        }
        return count;
    }
}
