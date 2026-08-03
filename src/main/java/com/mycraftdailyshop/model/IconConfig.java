package com.mycraftdailyshop.model;

import org.bukkit.configuration.ConfigurationSection;

public final class IconConfig {
    private final IconType type;
    private final ConfigurationSection display;

    public IconConfig(IconType type, ConfigurationSection display) {
        this.type = type;
        this.display = display;
    }

    public IconType getType() { return type; }
    public ConfigurationSection getDisplay() { return display; }
}
