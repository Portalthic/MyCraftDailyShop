package com.mycraftdailyshop.integration;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;

public final class EconomyService {
    private final Economy economy;

    public EconomyService(JavaPlugin plugin) {
        RegisteredServiceProvider<Economy> registration = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (registration == null || registration.getProvider() == null) throw new IllegalStateException("Vault 没有找到可用的经济服务");
        economy = registration.getProvider();
    }

    public boolean has(Player player, BigDecimal amount) { return economy.has(player, amount.doubleValue()); }
    public boolean withdraw(Player player, BigDecimal amount) { return economy.withdrawPlayer(player, amount.doubleValue()).type == EconomyResponse.ResponseType.SUCCESS; }
    public boolean deposit(Player player, BigDecimal amount) { return economy.depositPlayer(player, amount.doubleValue()).type == EconomyResponse.ResponseType.SUCCESS; }
}
