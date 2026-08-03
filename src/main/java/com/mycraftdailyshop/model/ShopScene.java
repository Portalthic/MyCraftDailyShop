package com.mycraftdailyshop.model;

public enum ShopScene {
    PERSONAL, SERVER;

    public static ShopScene parse(String value) {
        return valueOf(value.trim().toUpperCase());
    }
}
