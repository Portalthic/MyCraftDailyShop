package com.mycraftdailyshop.service;

import com.mycraftdailyshop.database.ShopDatabase;
import com.mycraftdailyshop.model.ShopConfig;
import com.mycraftdailyshop.model.ShopScene;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.time.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class MaintenanceScheduler {
    private final JavaPlugin plugin;
    private final ShopRegistry registry;
    private final ShopService service;
    private final ShopDatabase database;
    private final MessageService messages;
    private final Map<String, String> cycleKeys = new HashMap<>();
    private LocalDate lastCleanup;

    public MaintenanceScheduler(JavaPlugin plugin, ShopRegistry registry, ShopService service, ShopDatabase database, MessageService messages) {
        this.plugin=plugin;this.registry=registry;this.service=service;this.database=database;this.messages=messages;
    }

    public void start() {
        for (ShopConfig shop : registry.all()) cycleKeys.put(shop.getId(), registry.getRefreshCalculator().current(shop, System.currentTimeMillis()).getKey());
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickRefresh, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickCleanup, 20L, 1200L);
    }

    public void reloadCycles() {
        cycleKeys.clear();
        for (ShopConfig shop : registry.all()) cycleKeys.put(shop.getId(), registry.getRefreshCalculator().current(shop, System.currentTimeMillis()).getKey());
    }

    private void tickRefresh() {
        for (ShopConfig shop : registry.all()) {
            String current=registry.getRefreshCalculator().current(shop,System.currentTimeMillis()).getKey();
            String old=cycleKeys.put(shop.getId(),current);
            if(old!=null&&!old.equals(current)) announce(shop);
        }
    }

    public void announce(ShopConfig shop) {
        if (shop.getScene()==ShopScene.SERVER) {
            service.snapshot(null,shop).whenComplete((snapshot,error)->Bukkit.getScheduler().runTask(plugin,()->{
                String text=snapshot!=null&&snapshot.getOffers().isEmpty()?shop.getNoShopsMessage():shop.getRestockMessage();broadcast(text);
            }));
        } else broadcast(shop.getRestockMessage());
    }

    private void broadcast(String text) {
        if(text==null||text.isEmpty())return;
        Bukkit.getConsoleSender().sendMessage(messages.format(null,text,null));
        Bukkit.getOnlinePlayers().forEach(player->player.sendMessage(messages.format(player,text,null)));
    }

    private void tickCleanup() {
        FileConfiguration config=plugin.getConfig();if(!config.getBoolean("database.cleanup.enabled",true))return;
        ZoneId zone=ZoneId.of(config.getString("timezone","Asia/Shanghai"));ZonedDateTime now=ZonedDateTime.now(zone);LocalTime runAt;
        try{runAt=LocalTime.parse(config.getString("database.cleanup.run-at","04:00:00"));}catch(Exception ex){return;}
        if(now.toLocalTime().isBefore(runAt)||now.toLocalTime().isAfter(runAt.plusMinutes(1))||now.toLocalDate().equals(lastCleanup))return;
        lastCleanup=now.toLocalDate();int cycles=config.getInt("database.cleanup.expired-cycles-days",7),history=config.getInt("database.cleanup.transaction-history-days",-1);
        CompletableFuture.runAsync(()->{try{database.cleanup(cycles,history);}catch(SQLException ex){plugin.getLogger().log(Level.SEVERE,"清理数据库失败",ex);}});
    }
}
