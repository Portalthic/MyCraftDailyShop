package com.mycraftdailyshop.listener;

import com.mycraftdailyshop.service.ShopService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class PlayerListener implements Listener {
    private final ShopService service;
    public PlayerListener(ShopService service) { this.service = service; }
    @EventHandler public void join(PlayerJoinEvent event) { service.remember(event.getPlayer()); }
}
