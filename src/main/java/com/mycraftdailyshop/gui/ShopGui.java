package com.mycraftdailyshop.gui;

import com.mycraftdailyshop.database.UsageResult;
import com.mycraftdailyshop.model.*;
import com.mycraftdailyshop.service.MessageService;
import com.mycraftdailyshop.service.ShopService;
import com.mycraftdailyshop.util.ItemStackFactory;
import com.mycraftdailyshop.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.RoundingMode;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ShopGui implements Listener {
    private final JavaPlugin plugin;
    private final ShopService service;
    private final MessageService messages;
    private final Map<UUID, Long> clickTimes = new ConcurrentHashMap<>();

    public ShopGui(JavaPlugin plugin, ShopService service, MessageService messages) {
        this.plugin = plugin; this.service = service; this.messages = messages;
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshOpenServerShops, 20L, 20L);
    }

    public void open(Player player, ShopConfig shop) {
        messages.send(player, "shop.loading", Collections.emptyMap());
        service.snapshot(player, shop).whenComplete((snapshot, error) -> {
            if (error != null) { Bukkit.getScheduler().runTask(plugin, () -> messages.send(player, "shop.transaction-failed", Collections.emptyMap())); return; }
            service.usageBatch(snapshot, player).whenComplete((usage, usageError) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (usageError != null || !player.isOnline()) { if (usageError != null && player.isOnline()) messages.send(player, "shop.transaction-failed", Collections.emptyMap()); return; }
                if (snapshot.getOffers().isEmpty()) {
                    if (shop.getNoShopsMessage() == null || shop.getNoShopsMessage().isEmpty()) messages.send(player, "shop.empty", Collections.emptyMap());
                    else player.sendMessage(messages.format(player, shop.getNoShopsMessage(), Collections.emptyMap()));
                }
                ShopHolder holder = new ShopHolder(shop, snapshot, false);
                holder.putAllUsage(usage);
                openHolder(player, holder);
            }));
        });
    }

    public void show(Player player, ShopConfig shop) { openHolder(player, new ShopHolder(shop, null, true)); }

    public void forget(Player player) { if (player != null) clickTimes.remove(player.getUniqueId()); }

    private boolean isOpen(Player player, ShopHolder holder) {
        return player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() == holder;
    }

    private void refreshHolder(Player player, ShopHolder oldHolder) {
        if (oldHolder.isCatalog() || oldHolder.isSnapshotRefreshing() || !isOpen(player, oldHolder)) return;
        oldHolder.setSnapshotRefreshing(true);
        service.snapshot(player, oldHolder.getShop()).whenComplete((snapshot, error) -> {
            if (error != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    oldHolder.setSnapshotRefreshing(false);
                    if (isOpen(player, oldHolder)) messages.send(player, "shop.transaction-failed", Collections.emptyMap());
                });
                return;
            }
            service.usageBatch(snapshot, player).whenComplete((usage, usageError) -> Bukkit.getScheduler().runTask(plugin, () -> {
                oldHolder.setSnapshotRefreshing(false);
                if (usageError != null || !isOpen(player, oldHolder)) return;
                ShopHolder holder = new ShopHolder(oldHolder.getShop(), snapshot, false);
                holder.copyViewStateFrom(oldHolder);
                holder.putAllUsage(usage);
                openHolder(player, holder);
            }));
        });
    }

    private void openHolder(Player player, ShopHolder holder) {
        String title = messages.format(player, holder.getShop().getTitle(), vars(player, holder, pageCount(holder)));
        if (title.length() > 32) title = title.substring(0, 32);
        Inventory inventory = Bukkit.createInventory(holder, holder.getShop().getSize(), title);
        holder.setInventory(inventory);
        render(player, holder);
        player.openInventory(inventory);
        SoundUtil.play(player, holder.getShop().getOpenSound());
    }

    private void render(Player player, ShopHolder holder) {
        Inventory inventory = holder.getInventory();
        inventory.clear();
        List<?> entries = sorted(holder);
        int perPage = holder.getShop().getProductSlotsPerPage();
        int pages = Math.max(1, (entries.size() + perPage - 1) / perPage);
        if (holder.getPage() >= pages) holder.setPage(0);
        int entryOffset = holder.getPage() * perPage;
        int productSlot = 0;
        Map<String, String> vars = vars(player, holder, pages);
        List<String> layout = holder.getShop().getLayout();
        for (int row = 0; row < layout.size(); row++) for (int col = 0; col < 9; col++) {
            int slot = row * 9 + col;
            IconConfig icon = holder.getShop().getIcons().get(layout.get(row).charAt(col));
            if (icon == null) continue;
            if (icon.getType() == IconType.SHOPS) {
                int index = entryOffset + productSlot++;
                if (index < entries.size()) {
                    Object entry = entries.get(index);
                    inventory.setItem(slot, holder.isCatalog() ? catalogItem(player, holder.getShop(), (ProductConfig) entry) : offerItem(player, holder, (Offer) entry, holder.getUsage(((Offer) entry).getIndex())));
                }
            } else inventory.setItem(slot, ItemStackFactory.fromConfig(player, icon.getDisplay(), messages, vars));
        }
    }

    private void reopen(Player player, ShopHolder holder) {
        String title = messages.format(player, holder.getShop().getTitle(), vars(player, holder, pageCount(holder)));
        if (title.length() > 32) title = title.substring(0, 32);
        Inventory inventory = Bukkit.createInventory(holder, holder.getShop().getSize(), title);
        holder.setInventory(inventory);
        render(player, holder);
        player.openInventory(inventory);
    }

    private ItemStack catalogItem(Player player, ShopConfig shop, ProductConfig product) {
        ItemStack base = service.createDisplay(product.getProvider(), product.getItemId());
        if (base == null) base = missing(product.getProvider() + ":" + product.getItemId());
        Map<String, String> vars = new HashMap<>();
        vars.put("minMoney", product.getMoney().getMin().setScale(2, RoundingMode.HALF_UP).toPlainString());
        vars.put("maxMoney", product.getMoney().getMax().setScale(2, RoundingMode.HALF_UP).toPlainString());
        vars.put("minAmount", product.getAmount().getMin().setScale(0, RoundingMode.HALF_UP).toPlainString());
        vars.put("maxAmount", product.getAmount().getMax().setScale(0, RoundingMode.HALF_UP).toPlainString());
        vars.put("personalLimit", displayLimit(product.getPersonalLimit()));
        vars.put("serverLimit", shop.getScene() == ShopScene.SERVER ? displayLimit(product.getServerLimit()) : "∞");
        vars.put("chance", String.format(Locale.US, "%.2f", product.getChance() * 100D));
        String loreKey = shop.getType() == ShopType.SELL ? "sell-catalog" : "buy-catalog";
        String personalKey = shop.getScene() == ShopScene.PERSONAL ? loreKey + "-personal" : null;
        List<String> lore = messages.list(player, "lore." + (personalKey == null ? loreKey : personalKey), personalKey == null ? null : "lore." + loreKey, vars);
        return ItemStackFactory.appendLore(base, expandCatalogEnchantments(player, lore, product));
    }

    private ItemStack offerItem(Player player, ShopHolder holder, Offer offer, int[] usage) {
        ItemStack base = service.createOfferDisplay(offer);
        if (base == null) base = missing(offer.getProvider() + ":" + offer.getItemId());
        int personalUsed = usage == null ? 0 : usage[0], serverUsed = usage == null ? 0 : usage[1];
        Map<String, String> vars = new HashMap<>();
        vars.put("money", offer.getMoney().toPlainString()); vars.put("totalMoney", offer.getTotalMoney().toPlainString()); vars.put("amount", Integer.toString(offer.getAmount()));
        vars.put("number", remaining(offer.getPersonalLimit(), personalUsed)); vars.put("allNumber", remaining(offer.getServerLimit(), serverUsed)); vars.put("premium", offer.getPremium().multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.DOWN).stripTrailingZeros().toPlainString() + "%");
        String loreKey = holder.getShop().getType() == ShopType.SELL ? "sell-offer" : "buy-offer";
        String personalKey = holder.getShop().getScene() == ShopScene.PERSONAL ? loreKey + "-personal" : null;
        return ItemStackFactory.appendLore(base, messages.list(player, "lore." + (personalKey == null ? loreKey : personalKey), personalKey == null ? null : "lore." + loreKey, vars));
    }

    private List<String> expandCatalogEnchantments(Player player, List<String> lore, ProductConfig product) {
        List<String> summaries = new ArrayList<>();
        for (EnchantmentGroupConfig group : product.getEnchantments()) {
            String split = messages.text(player, "enchantments.enchantmentSplit", "/"); StringBuilder names = new StringBuilder(); int minLevel=Integer.MAX_VALUE,maxLevel=0; BigDecimal minPremium=null,maxPremium=null;
            for (EnchantmentChoiceConfig choice : group.getChoices()) { if(names.length()>0)names.append(split);names.append(messages.text(player,"enchantments.enchantmentName."+choice.getId(),choice.getId())); for(EnchantmentLevelConfig level:choice.getLevels()){minLevel=Math.min(minLevel,level.getLevel());maxLevel=Math.max(maxLevel,level.getLevel());BigDecimal min=level.getPremium().getMin(),max=level.getPremium().getMax();minPremium=minPremium==null||min.compareTo(minPremium)<0?min:minPremium;maxPremium=maxPremium==null||max.compareTo(maxPremium)>0?max:maxPremium;} }
            Map<String,String> vars=new HashMap<>();vars.put("enchantmentGroupDisplay",names.toString());vars.put("minLevel",roman(minLevel));vars.put("maxLevel",roman(maxLevel));vars.put("enchantmentChance",percent(BigDecimal.valueOf(group.getChance())));vars.put("minPremium",percent(minPremium));vars.put("maxPremium",percent(maxPremium));summaries.addAll(messages.list(player,"enchantments.enchantment",vars));
        }
        List<String> block=new ArrayList<>(); if(!summaries.isEmpty()) for(String line:messages.list(player,"enchantments.preLore",Collections.emptyMap())) { if(line.equals("[enchantmentDisplay]"))block.addAll(summaries);else block.add(line); }
        List<String> result = new ArrayList<>();
        for (String line : lore) { if (line.equals("[enchantments]")) result.addAll(block); else result.add(line); }
        return result;
    }
    private String percent(BigDecimal value){return value.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString()+"%";}
    private String roman(int value) { String[] n={"","I","II","III","IV","V","VI","VII","VIII","IX","X"}; return value >= 0 && value < n.length ? n[value] : Integer.toString(value); }

    private String displayLimit(ValueSpec spec) {
        return spec.getMin().signum() < 0 && spec.getMax().signum() < 0 ? "∞" : spec.describeInteger();
    }
    private String remaining(int limit, int used) { return limit < 0 ? "∞" : Integer.toString(Math.max(0, limit - used)); }
    private ItemStack missing(String id) { ItemStack item=new ItemStack(Material.BARRIER); ItemMeta meta=item.getItemMeta(); meta.setDisplayName("§c未知物品: " + id); item.setItemMeta(meta); return item; }

    private List<?> sorted(ShopHolder holder) {
        if (holder.isCatalog()) {
            List<ProductConfig> list = new ArrayList<>(holder.getShop().getProducts());
            Comparator<ProductConfig> comparator;
            switch (holder.getSort()) {
                case CHANCE: comparator=Comparator.comparingDouble(ProductConfig::getChance); break;
                case MONEY: comparator=Comparator.comparing(p->p.getMoney().getMin()); break;
                case AMOUNT: comparator=Comparator.comparing(p->p.getAmount().getMin()); break;
                default: comparator=Comparator.comparingInt(ProductConfig::getIndex);
            }
            comparator=comparator.thenComparingInt(ProductConfig::getIndex); if(holder.isDescending())comparator=comparator.reversed(); list.sort(comparator); return list;
        }
        List<Offer> list=new ArrayList<>(holder.getSnapshot().getOffers()); Comparator<Offer> comparator;
        switch(holder.getSort()){
            case CHANCE: comparator=Comparator.comparingDouble(Offer::getChance);break;
            case MONEY: comparator=Comparator.comparing(Offer::getTotalMoney);break;
            case AMOUNT: comparator=Comparator.comparingInt(Offer::getAmount);break;
            default: comparator=Comparator.comparingInt(Offer::getIndex);
        }
        comparator=comparator.thenComparingInt(Offer::getIndex);if(holder.isDescending())comparator=comparator.reversed();list.sort(comparator);return list;
    }

    private int pageCount(ShopHolder holder) { int size=holder.isCatalog()?holder.getShop().getProducts().size():holder.getSnapshot().getOffers().size();return Math.max(1,(size+holder.getShop().getProductSlotsPerPage()-1)/holder.getShop().getProductSlotsPerPage()); }
    private Map<String,String> vars(Player player, ShopHolder holder,int pages){Map<String,String> vars=new HashMap<>();vars.put("page",Integer.toString(holder.getPage()+1));vars.put("page_all",Integer.toString(pages));vars.put("sort",messages.text(player, "shop.sort." + holder.getSort().getKey(), ""));vars.put("order",messages.text(player, holder.isDescending()?"shop.order.descending":"shop.order.ascending", ""));return vars;}

    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player) || event.getRawSlot() < 0 || event.getRawSlot() >= event.getInventory().getSize()) return;
        Player player=(Player)event.getWhoClicked(); ShopHolder holder=(ShopHolder)event.getInventory().getHolder();
        if (!holder.isCatalog() && !service.isCurrent(holder.getShop(), holder.getSnapshot())) {
            refreshHolder(player, holder);
            return;
        }
        int row=event.getRawSlot()/9,col=event.getRawSlot()%9; IconConfig icon=holder.getShop().getIcons().get(holder.getShop().getLayout().get(row).charAt(col)); if(icon==null)return;
        if(icon.getType()==IconType.PREVIOUS_PAGE){int pages=pageCount(holder);holder.setPage((holder.getPage()-1+pages)%pages);Bukkit.getScheduler().runTask(plugin,()->reopen(player,holder));return;}
        if(icon.getType()==IconType.NEXT_PAGE){int pages=pageCount(holder);holder.setPage((holder.getPage()+1)%pages);Bukkit.getScheduler().runTask(plugin,()->reopen(player,holder));return;}
        if(icon.getType()==IconType.SORT){if(event.getClick()==ClickType.RIGHT)holder.toggleDescending();else holder.nextSort();render(player,holder);return;}
        if(icon.getType()!=IconType.SHOPS||holder.isCatalog()||holder.isTrading())return;
        int productOrder=0;for(int r=0;r<row;r++)for(int c=0;c<9;c++){IconConfig i=holder.getShop().getIcons().get(holder.getShop().getLayout().get(r).charAt(c));if(i!=null&&i.getType()==IconType.SHOPS)productOrder++;}for(int c=0;c<col;c++){IconConfig i=holder.getShop().getIcons().get(holder.getShop().getLayout().get(row).charAt(c));if(i!=null&&i.getType()==IconType.SHOPS)productOrder++;}
        List<?> entries=sorted(holder);int selected=holder.getPage()*holder.getShop().getProductSlotsPerPage()+productOrder;if(selected>=entries.size())return;Offer offer=(Offer)entries.get(selected);
        long now=System.currentTimeMillis(),last=clickTimes.getOrDefault(player.getUniqueId(),0L),cooldown=plugin.getConfig().getLong("shop.click_cooldown",200L);if(now-last<cooldown){messages.send(player,"shop.cooldown",Collections.emptyMap());return;}clickTimes.put(player.getUniqueId(),now);
        if(holder.getShop().getType()==ShopType.SELL&&!service.hasMoney(player,offer.getTotalMoney())){messages.send(player,"shop.no-money",tradeVars(holder,offer));SoundUtil.play(player,holder.getShop().getFailSound());return;}
        if(holder.getShop().getType()==ShopType.BUY&&!service.hasItem(player,offer.getProvider(),offer.getItemId(),offer.getAmount())){messages.send(player,"shop.no-item",tradeVars(holder,offer));SoundUtil.play(player,holder.getShop().getFailSound());return;}
        holder.setTrading(true);service.trade(player,holder.getShop(),holder.getSnapshot(),offer,(result,overflow)->{holder.setTrading(false);UsageResult.Status status=result.getStatus();String path;if(status==UsageResult.Status.SUCCESS)path=holder.getShop().getType()==ShopType.SELL?"shop.buy-success":"shop.sell-success";else if(status==UsageResult.Status.PERSONAL_LIMIT)path="shop.personal-limit";else if(status==UsageResult.Status.SERVER_LIMIT)path="shop.server-limit";else if(status==UsageResult.Status.STALE)path="shop.stale";else path="shop.transaction-failed";messages.send(player,path,tradeVars(holder,offer));SoundUtil.play(player,status==UsageResult.Status.SUCCESS?holder.getShop().getSuccessSound():holder.getShop().getFailSound());if(overflow)messages.send(player,"shop.overflow",tradeVars(holder,offer));if(status==UsageResult.Status.STALE)refreshHolder(player,holder);else if(status==UsageResult.Status.SUCCESS||status==UsageResult.Status.PERSONAL_LIMIT||status==UsageResult.Status.SERVER_LIMIT)syncOpenViews(player,holder,offer,result);});
    }

    private void refreshOpenServerShops() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof ShopHolder)) continue;
            ShopHolder holder = (ShopHolder) player.getOpenInventory().getTopInventory().getHolder();
            if (holder.isCatalog() || holder.isSnapshotRefreshing()) continue;
            if (!service.isCurrent(holder.getShop(), holder.getSnapshot())) { refreshHolder(player, holder); continue; }
            if (holder.getShop().getScene() != ShopScene.SERVER || holder.isUsageRefreshing()) continue;
            holder.setUsageRefreshing(true);
            service.usageBatch(holder.getSnapshot(), player).whenComplete((usage, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                holder.setUsageRefreshing(false);
                if (error == null && player.getOpenInventory().getTopInventory().getHolder() == holder) {
                    if (holder.replaceUsage(usage)) refreshVisibleOffers(player, holder);
                }
            }));
        }
    }

    private void syncOpenViews(Player buyer, ShopHolder source, Offer offer, UsageResult result) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!(viewer.getOpenInventory().getTopInventory().getHolder() instanceof ShopHolder)) continue;
            ShopHolder holder = (ShopHolder) viewer.getOpenInventory().getTopInventory().getHolder();
            if (holder.isCatalog() || !holder.getShop().getId().equals(source.getShop().getId()) || !holder.getSnapshot().getCycleId().equals(source.getSnapshot().getCycleId())) continue;
            int personal = viewer.getUniqueId().equals(buyer.getUniqueId()) ? result.getPersonalUsed() : holder.getUsage(offer.getIndex())[0];
            holder.putUsage(offer.getIndex(), personal, result.getServerUsed());
            refreshOffer(viewer, holder, offer.getIndex());
        }
    }

    private void refreshOffer(Player player, ShopHolder holder, int offerIndex) {
        List<?> entries = sorted(holder);
        int entryPosition = -1;
        for (int i = 0; i < entries.size(); i++) if (((Offer) entries.get(i)).getIndex() == offerIndex) { entryPosition = i; break; }
        int perPage = holder.getShop().getProductSlotsPerPage();
        int pageOffset = entryPosition - holder.getPage() * perPage;
        if (entryPosition < 0 || pageOffset < 0 || pageOffset >= perPage) return;
        int productSlot = 0;
        List<String> layout = holder.getShop().getLayout();
        for (int row = 0; row < layout.size(); row++) for (int col = 0; col < 9; col++) {
            IconConfig icon = holder.getShop().getIcons().get(layout.get(row).charAt(col));
            if (icon != null && icon.getType() == IconType.SHOPS && productSlot++ == pageOffset) {
                Offer offer = (Offer) entries.get(entryPosition);
                holder.getInventory().setItem(row * 9 + col, offerItem(player, holder, offer, holder.getUsage(offerIndex)));
                return;
            }
        }
    }

    private void refreshVisibleOffers(Player player, ShopHolder holder) {
        List<?> entries = sorted(holder);
        int entryOffset = holder.getPage() * holder.getShop().getProductSlotsPerPage();
        int productSlot = 0;
        List<String> layout = holder.getShop().getLayout();
        for (int row = 0; row < layout.size(); row++) for (int col = 0; col < 9; col++) {
            IconConfig icon = holder.getShop().getIcons().get(layout.get(row).charAt(col));
            if (icon == null || icon.getType() != IconType.SHOPS) continue;
            int index = entryOffset + productSlot++;
            if (index < entries.size()) {
                Offer offer = (Offer) entries.get(index);
                holder.getInventory().setItem(row * 9 + col, offerItem(player, holder, offer, holder.getUsage(offer.getIndex())));
            }
        }
    }

    private Map<String,String> tradeVars(ShopHolder holder,Offer offer){Map<String,String> vars=new HashMap<>();vars.put("shop",holder.getShop().getId());vars.put("item",offer.getItemId());vars.put("item_id",offer.getItemId());vars.put("amount",Integer.toString(offer.getAmount()));vars.put("money",offer.getMoney().toPlainString());vars.put("totalMoney",offer.getTotalMoney().toPlainString());return vars;}
    @EventHandler public void drag(InventoryDragEvent event){if(event.getInventory().getHolder() instanceof ShopHolder)event.setCancelled(true);}
}
