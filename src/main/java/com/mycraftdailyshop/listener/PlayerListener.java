package com.mycraftdailyshop.listener;

import com.mycraftdailyshop.service.ShopService;
import com.mycraftdailyshop.gui.ShopGui;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class PlayerListener implements Listener {
    private final ShopService service;
    private final ShopGui gui;
    public PlayerListener(ShopService service, ShopGui gui) { this.service = service; this.gui = gui; }
    @EventHandler public void join(PlayerJoinEvent event) { service.remember(event.getPlayer()); }
    @EventHandler public void quit(org.bukkit.event.player.PlayerQuitEvent event) { gui.forget(event.getPlayer()); }
}
