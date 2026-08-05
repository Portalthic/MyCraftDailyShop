package com.mycraftdailyshop.gui;

public enum SortMode {
    INDEX("index"), CHANCE("chance"), MONEY("money"), AMOUNT("amount");
    private final String key;
    SortMode(String key) { this.key = key; }
    public String getKey() { return key; }
    public SortMode next() { return values()[(ordinal() + 1) % values().length]; }
}
