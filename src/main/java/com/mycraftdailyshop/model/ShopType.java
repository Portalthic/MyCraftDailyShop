package com.mycraftdailyshop.model;

public enum ShopType {
    SELL, BUY;

    public static ShopType parse(String value) {
        return valueOf(value.trim().toUpperCase());
    }
}
