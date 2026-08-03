package com.mycraftdailyshop.model;

public enum IconType {
    ITEM, SHOPS, PREVIOUS_PAGE, NEXT_PAGE, SORT;

    public static IconType parse(String value) {
        return valueOf(value.trim().toUpperCase());
    }
}
