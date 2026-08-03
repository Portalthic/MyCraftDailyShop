package com.mycraftdailyshop.gui;

public enum SortMode {
    INDEX("索引"), CHANCE("概率"), MONEY("价格"), AMOUNT("数量");
    private final String display;
    SortMode(String display) { this.display = display; }
    public String getDisplay() { return display; }
    public SortMode next() { return values()[(ordinal() + 1) % values().length]; }
}
