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
    public interface TradeCallback { void complete(UsageResult.Status status, boolean overflow); }

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

    private List<Offer> generate(ShopConfig shop) {
        List<Offer> result = new ArrayList<>();
        synchronized (random) {
            for (ProductConfig product : shop.getProducts()) {
                if (random.nextDouble() > product.getChance()) continue;
                int amount = Math.max(1, product.getAmount().sampleInteger(random, false));
                BigDecimal unit = product.getMoney().sampleMoney(random);
                BigDecimal total = unit.multiply(BigDecimal.valueOf(amount)).setScale(2, RoundingMode.HALF_UP);
                int personal = product.getPersonalLimit().sampleInteger(random, true);
                int server = shop.getScene() == ShopScene.SERVER ? product.getServerLimit().sampleInteger(random, true) : -1;
                result.add(new Offer(null, product.getIndex(), product.getProvider(), product.getItemId(), unit, total, amount, personal, server, product.getChance()));
            }
        }
        return result;
    }

    public CompletableFuture<int[]> usage(Offer offer, Player player) {
        String playerUuid = player.getUniqueId().toString();
        return CompletableFuture.supplyAsync(() -> {
            try { return database.getUsage(offer, playerUuid); }
            catch (SQLException ex) { throw new CompletionException(ex); }
        }, executor);
    }

    public void trade(Player player, ShopConfig shop, ShopSnapshot snapshot, Offer offer, TradeCallback callback) {
        RefreshCycle current = registry.getRefreshCalculator().current(shop, System.currentTimeMillis());
        if (!current.getKey().equals(snapshot.getCycleKey())) { callback.complete(UsageResult.Status.STALE, false); return; }
        ItemProvider provider = providers.get(offer.getProvider());
        if (provider == null || !provider.exists(offer.getItemId())) { callback.complete(UsageResult.Status.ERROR, false); return; }
        if (shop.getType() == ShopType.SELL && !economy.has(player, offer.getTotalMoney())) { callback.complete(UsageResult.Status.ERROR, false); return; }
        if (shop.getType() == ShopType.BUY && !provider.has(player, offer.getItemId(), offer.getAmount())) { callback.complete(UsageResult.Status.ERROR, false); return; }

        String playerUuid = player.getUniqueId().toString();
        String playerName = player.getName();
        CompletableFuture.supplyAsync(() -> {
            try { return database.reserve(offer, playerUuid); }
            catch (SQLException ex) { throw new CompletionException(ex); }
        }, executor).whenComplete((reservation, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null) { log(error); callback.complete(UsageResult.Status.ERROR, false); return; }
            if (reservation.getStatus() != UsageResult.Status.SUCCESS) { callback.complete(reservation.getStatus(), false); return; }
            TradeExecution execution = executeMainThread(player, shop, offer, provider);
            if (!execution.success) {
                CompletableFuture.runAsync(() -> { try { database.release(offer, playerUuid); } catch (SQLException ex) { log(ex); } }, executor);
                callback.complete(UsageResult.Status.ERROR, execution.overflow);
                return;
            }
            CompletableFuture.runAsync(() -> {
                try { database.record(playerUuid, playerName, shop.getId(), shop.getType().name(), offer); }
                catch (SQLException ex) { log(ex); }
            }, executor);
            callback.complete(UsageResult.Status.SUCCESS, execution.overflow);
        }));
    }

    private TradeExecution executeMainThread(Player player, ShopConfig shop, Offer offer, ItemProvider provider) {
        if (!player.isOnline()) return new TradeExecution(false, false);
        if (shop.getType() == ShopType.SELL) {
            if (!economy.has(player, offer.getTotalMoney()) || !economy.withdraw(player, offer.getTotalMoney())) return new TradeExecution(false, false);
            try {
                return new TradeExecution(true, giveOrDrop(player, provider, offer.getItemId(), offer.getAmount()));
            } catch (RuntimeException ex) {
                economy.deposit(player, offer.getTotalMoney()); log(ex); return new TradeExecution(false, false);
            }
        }
        if (!provider.has(player, offer.getItemId(), offer.getAmount()) || !provider.take(player, offer.getItemId(), offer.getAmount())) return new TradeExecution(false, false);
        if (economy.deposit(player, offer.getTotalMoney())) return new TradeExecution(true, false);
        boolean overflow = giveOrDrop(player, provider, offer.getItemId(), offer.getAmount());
        return new TradeExecution(false, overflow);
    }

    private boolean giveOrDrop(Player player, ItemProvider provider, String itemId, int amount) {
        int remaining = amount;
        boolean dropped = false;
        while (remaining > 0) {
            int batch = Math.min(remaining, 64);
            ItemStack item = provider.create(player, itemId, batch);
            if (item == null) throw new IllegalStateException("物品不存在: " + itemId);
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
    public ItemStack createDisplay(Player player, String providerName, String itemId) {
        ItemProvider provider = providers.get(providerName);
        return provider == null ? null : provider.create(player, itemId, 1);
    }
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
