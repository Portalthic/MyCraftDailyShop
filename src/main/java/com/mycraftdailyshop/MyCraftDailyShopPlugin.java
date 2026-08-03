package com.mycraftdailyshop;

import com.mycraftdailyshop.command.DailyShopCommand;
import com.mycraftdailyshop.database.ShopDatabase;
import com.mycraftdailyshop.gui.ShopGui;
import com.mycraftdailyshop.integration.*;
import com.mycraftdailyshop.listener.PlayerListener;
import com.mycraftdailyshop.service.*;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collections;
import java.util.logging.Level;

public final class MyCraftDailyShopPlugin extends JavaPlugin {
    private ShopDatabase database;
    private ShopService shopService;

    @Override public void onEnable() {
        try {
            saveDefaultConfig();
            saveIfMissing("message.yml");
            saveIfMissing("shop/default.yml");
            MessageService messages=new MessageService(this);messages.load();
            ShopRegistry registry=new ShopRegistry(this);int loaded=registry.load();
            database=new ShopDatabase(this);database.open();
            EconomyService economy=new EconomyService(this);
            ZaphkielItemProvider zaphkiel=new ZaphkielItemProvider();
            shopService=new ShopService(this,registry,database,economy,Collections.singletonList(zaphkiel));
            ShopGui gui=new ShopGui(this,shopService,messages);
            MaintenanceScheduler scheduler=new MaintenanceScheduler(this,registry,shopService,database,messages);scheduler.start();
            getServer().getPluginManager().registerEvents(gui,this);
            getServer().getPluginManager().registerEvents(new PlayerListener(shopService),this);
            PluginCommand command=getCommand("mycraftdailyshop");DailyShopCommand executor=new DailyShopCommand(this,registry,shopService,gui,messages,scheduler);command.setExecutor(executor);command.setTabCompleter(executor);
            for(Player player:getServer().getOnlinePlayers())shopService.remember(player);
            for(OfflinePlayer player:getServer().getOfflinePlayers())shopService.remember(player.getUniqueId(),player.getName());
            getLogger().info("MyCraftDailyShop " + getDescription().getVersion() + " 已启用，加载了 " + loaded + " 个商店。");
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE,"MyCraftDailyShop 启动失败",ex);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void saveIfMissing(String path){if(!new File(getDataFolder(),path).exists())saveResource(path,false);}
    @Override public void onDisable(){if(shopService!=null)shopService.close();if(database!=null)database.close();}
}
