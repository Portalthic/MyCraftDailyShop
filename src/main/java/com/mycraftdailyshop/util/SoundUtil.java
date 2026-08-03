package com.mycraftdailyshop.util;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

public final class SoundUtil {
    private SoundUtil() { }

    public static void play(Player player, String configured) {
        if (configured == null || configured.trim().isEmpty() || configured.equalsIgnoreCase("null")) return;
        String name = configured.trim().replace('.', '_').toUpperCase();
        try { player.playSound(player.getLocation(), Sound.valueOf(name), 1F, 1F); }
        catch (IllegalArgumentException ignored) { }
    }
}
