package com.mycraftdailyshop.service;

import com.mycraftdailyshop.database.ShopDatabase;
import com.mycraftdailyshop.database.UsageResult;
import com.mycraftdailyshop.integration.EconomyService;
import com.mycraftdailyshop.integration.ItemProvider;
import com.mycraftdailyshop.model.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;

public final class ShopService implements AutoCloseable {
    public interface TradeCallback { void complete(UsageResult result, boolean overflow); }

    private final JavaPlugin plugin;
    private final ShopRegistry registry;
    private final ShopDatabase database;
    private final EconomyService economy;
    private final Map<String, ItemProvider> providers;
    private final ExecutorService executor;
    private final Random random = new Random();

    public ShopService(JavaPlugin plugin, ShopRegistry registry, ShopDatabase database, EconomyService economy, Collection<ItemProvider> providers) {
        this.plugin = plugin;
        this.registry = registry;
        this.database = database;
        this.economy = economy;
        this.providers = new HashMap<>();
        for (ItemProvider provider : providers) this.providers.put(provider.namespace(), provider);
        this.executor = Executors.newFixedThreadPool(Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors())), runnable -> {
            Thread thread = new Thread(runnable, "MyCraftDailyShop-Database"); thread.setDaemon(true); return thread;
        });
    }

    public CompletableFuture<ShopSnapshot> snapshot(Player player, ShopConfig shop) {
        RefreshCycle cycle = registry.getRefreshCalculator().current(shop, System.currentTimeMillis());
        String scope = shop.getScene() == ShopScene.SERVER ? "SERVER" : player.getUniqueId().toString();
        return CompletableFuture.supplyAsync(() -> {
            try {
                ShopSnapshot existing = database.findSnapshot(shop.getId(), scope, cycle.getKey());
                if (existing != null) return existing;
                return database.createSnapshot(shop.getId(), scope, cycle.getKey(), cycle.getEnd(), generate(shop));
            } catch (SQLException ex) { throw new CompletionException(ex); }
        }, executor);
    }

    public boolean isCurrent(ShopConfig shop, ShopSnapshot snapshot) {
        return snapshot != null && registry.getRefreshCalculator().current(shop, System.currentTimeMillis()).getKey().equals(snapshot.getCycleKey());
    }

    private List<Offer> generate(ShopConfig shop) {
        List<Offer> result = new ArrayList<>();
        synchronized (random) {
            for (ProductConfig product : shop.getProducts()) {
                if (random.nextDouble() > product.getChance()) continue;
                int amount = Math.max(1, product.getAmount().sampleInteger(random, false));
                BigDecimal base = product.getMoney().sampleMoney(random);
                List<EnchantmentRoll> rolls = new ArrayList<>();
                for (EnchantmentGroupConfig group : product.getEnchantments()) {
                    if (random.nextDouble() >= group.getChance()) continue;
                    EnchantmentChoiceConfig choice = weighted(group.getChoices(), random);
                    EnchantmentLevelConfig level = weighted(choice.getLevels(), random);
                    BigDecimal premium = level.getPremium().sampleDecimal(random).max(BigDecimal.ZERO);
                    rolls.add(new EnchantmentRoll(choice.getId(), level.getLevel(), premium));
                }
                BigDecimal multiplier = BigDecimal.ONE;
                for (EnchantmentRoll roll : rolls) multiplier = multiplier.multiply(BigDecimal.ONE.add(roll.getPremium()));
                BigDecimal unit = base.multiply(multiplier).setScale(2, RoundingMode.DOWN);
                BigDecimal total = unit.multiply(BigDecimal.valueOf(amount)).setScale(2, RoundingMode.DOWN);
                int personal = product.getPersonalLimit().sampleInteger(random, true);
                int server = shop.getScene() == ShopScene.SERVER ? product.getServerLimit().sampleInteger(random, true) : -1;
                BigDecimal totalPremium = multiplier.subtract(BigDecimal.ONE);
                result.add(new Offer(null, product.getIndex(), product.getProvider(), product.getItemId(), unit, total, amount, personal, server, product.getChance(), base, totalPremium, rolls));
            }
        }
        return result;
    }

    private <T> T weighted(List<T> values, Random random) {
        double total = 0; for (T value : values) total += value instanceof EnchantmentChoiceConfig ? ((EnchantmentChoiceConfig) value).getWeight() : ((EnchantmentLevelConfig) value).getWeight();
        double cursor = random.nextDouble() * total;
        for (T value : values) { cursor -= value instanceof EnchantmentChoiceConfig ? ((EnchantmentChoiceConfig) value).getWeight() : ((EnchantmentLevelConfig) value).getWeight(); if (cursor < 0) return value; }
        return values.get(values.size() - 1);
    }

    public CompletableFuture<Map<Integer, int[]>> usageBatch(ShopSnapshot snapshot, Player player) {
        String playerUuid = player.getUniqueId().toString();
        return CompletableFuture.supplyAsync(() -> {
            try { return database.getUsage(snapshot.getCycleId(), playerUuid); }
            catch (SQLException ex) { throw new CompletionException(ex); }
        }, executor);
    }

    public void trade(Player player, ShopConfig shop, ShopSnapshot snapshot, Offer offer, TradeCallback callback) {
        RefreshCycle current = registry.getRefreshCalculator().current(shop, System.currentTimeMillis());
        if (!current.getKey().equals(snapshot.getCycleKey())) { callback.complete(new UsageResult(UsageResult.Status.STALE, 0, 0), false); return; }
        ItemProvider provider = providers.get(offer.getProvider());
        if (provider == null || !provider.exists(offer.getItemId())) { callback.complete(new UsageResult(UsageResult.Status.ERROR, 0, 0), false); return; }
        if (shop.getType() == ShopType.SELL && !economy.has(player, offer.getTotalMoney())) { callback.complete(new UsageResult(UsageResult.Status.ERROR, 0, 0), false); return; }
        if (shop.getType() == ShopType.BUY && !provider.has(player, offer.getItemId(), offer.getAmount())) { callback.complete(new UsageResult(UsageResult.Status.ERROR, 0, 0), false); return; }

        String playerUuid = player.getUniqueId().toString();
        String playerName = player.getName();
        CompletableFuture.supplyAsync(() -> {
            try { return database.reserve(offer, playerUuid); }
            catch (SQLException ex) { throw new CompletionException(ex); }
        }, executor).whenComplete((reservation, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null) { log(error); callback.complete(new UsageResult(UsageResult.Status.ERROR, 0, 0), false); return; }
            if (reservation.getStatus() != UsageResult.Status.SUCCESS) { callback.complete(reservation, false); return; }
            if (!isCurrent(shop, snapshot)) {
                CompletableFuture.runAsync(() -> {
                    try { database.release(offer, playerUuid); } catch (SQLException ex) { log(ex); }
                }, executor);
                callback.complete(new UsageResult(UsageResult.Status.STALE, 0, 0), false);
                return;
            }
            TradeExecution execution = executeMainThread(player, shop, offer, provider);
            if (!execution.success) {
                CompletableFuture.runAsync(() -> { try { database.release(offer, playerUuid); } catch (SQLException ex) { log(ex); } }, executor);
                callback.complete(new UsageResult(UsageResult.Status.ERROR, 0, 0), execution.overflow);
                return;
            }
            CompletableFuture.runAsync(() -> {
                try { database.record(playerUuid, playerName, shop.getId(), shop.getType().name(), offer); }
                catch (SQLException ex) { log(ex); }
            }, executor);
            callback.complete(reservation, execution.overflow);
        }));
    }

    private TradeExecution executeMainThread(Player player, ShopConfig shop, Offer offer, ItemProvider provider) {
        if (!player.isOnline()) return new TradeExecution(false, false);
        if (shop.getType() == ShopType.SELL) {
            if (!economy.has(player, offer.getTotalMoney()) || !economy.withdraw(player, offer.getTotalMoney())) return new TradeExecution(false, false);
            try {
                return new TradeExecution(true, giveOrDrop(player, provider, offer));
            } catch (RuntimeException ex) {
                economy.deposit(player, offer.getTotalMoney()); log(ex); return new TradeExecution(false, false);
            }
        }
        if (!provider.has(player, offer.getItemId(), offer.getAmount()) || !provider.take(player, offer.getItemId(), offer.getAmount())) return new TradeExecution(false, false);
        if (economy.deposit(player, offer.getTotalMoney())) return new TradeExecution(true, false);
        boolean overflow = giveOrDrop(player, provider, offer);
        return new TradeExecution(false, overflow);
    }

    private boolean giveOrDrop(Player player, ItemProvider provider, Offer offer) {
        int remaining = offer.getAmount();
        boolean dropped = false;
        while (remaining > 0) {
            int batch = Math.min(remaining, 64);
            ItemStack item = provider.create(player, offer.getItemId(), batch, offer.getEnchantments());
            if (item == null) throw new IllegalStateException("物品不存在: " + offer.getItemId());
            item.setAmount(Math.min(batch, item.getMaxStackSize()));
            remaining -= item.getAmount();
            for (ItemStack overflow : player.getInventory().addItem(item).values()) { player.getWorld().dropItemNaturally(player.getLocation(), overflow); dropped = true; }
        }
        return dropped;
    }

    private static final class TradeExecution {
        private final boolean success;
        private final boolean overflow;
        private TradeExecution(boolean success, boolean overflow) { this.success = success; this.overflow = overflow; }
    }

    public CompletableFuture<Void> resetPlayer(String playerUuid, String shopId) { return run(() -> database.resetPlayerUsage(playerUuid, shopId)); }
    public CompletableFuture<Void> resetServer(String shopId) { return run(() -> database.resetServerUsage(shopId)); }
    public CompletableFuture<Void> invalidate(String shopId, String scope) { return run(() -> database.invalidate(shopId, scope)); }
    public CompletableFuture<String> findPlayerUuid(String value) { return CompletableFuture.supplyAsync(() -> { try { return database.findPlayerUuid(value); } catch (SQLException ex) { throw new CompletionException(ex); } }, executor); }
    public void remember(Player player) { remember(player.getUniqueId(), player.getName()); }
    public void remember(UUID uuid, String name) { if (uuid != null && name != null) CompletableFuture.runAsync(() -> { try { database.rememberPlayer(uuid.toString(), name); } catch (SQLException ex) { log(ex); } }, executor); }
    public boolean providerExists(ProductConfig product) { ItemProvider provider=providers.get(product.getProvider()); return provider != null && provider.exists(product.getItemId()); }
    public ItemStack createDisplay(String providerName, String itemId) {
        ItemProvider provider = providers.get(providerName);
        return provider == null ? null : provider.createDisplay(itemId, 1);
    }
    public ItemStack createOfferDisplay(Offer offer) { ItemProvider provider = providers.get(offer.getProvider()); return provider == null ? null : provider.createDisplay(offer.getItemId(), 1, offer.getEnchantments()); }
    public boolean hasMoney(Player player, BigDecimal amount) { return economy.has(player, amount); }
    public boolean hasItem(Player player, String providerName, String itemId, int amount) {
        ItemProvider provider = providers.get(providerName);
        return provider != null && provider.has(player, itemId, amount);
    }

    private CompletableFuture<Void> run(SqlRunnable runnable) { return CompletableFuture.runAsync(() -> { try { runnable.run(); } catch (SQLException ex) { throw new CompletionException(ex); } }, executor); }
    private interface SqlRunnable { void run() throws SQLException; }
    private void log(Throwable error) { plugin.getLogger().log(Level.SEVERE, "数据库或交易操作失败", error instanceof CompletionException && error.getCause()!=null ? error.getCause() : error); }
    @Override public void close() { executor.shutdown(); try { executor.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); } }
}
