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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ShopGui implements Listener {
    private final JavaPlugin plugin;
    private final ShopService service;
    private final MessageService messages;
    private final Map<UUID, Long> clickTimes = new ConcurrentHashMap<>();

    public ShopGui(JavaPlugin plugin, ShopService service, MessageService messages) {
        this.plugin = plugin; this.service = service; this.messages = messages;
    }

    public void open(Player player, ShopConfig shop) {
        messages.send(player, "shop.loading", Collections.emptyMap());
        service.snapshot(player, shop).whenComplete((snapshot, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null) { messages.send(player, "shop.transaction-failed", Collections.emptyMap()); return; }
            if (snapshot.getOffers().isEmpty()) {
                if (shop.getNoShopsMessage() == null || shop.getNoShopsMessage().isEmpty()) messages.send(player, "shop.empty", Collections.emptyMap());
                else player.sendMessage(messages.format(player, shop.getNoShopsMessage(), Collections.emptyMap()));
            }
            openHolder(player, new ShopHolder(shop, snapshot, false));
        }));
    }

    public void show(Player player, ShopConfig shop) { openHolder(player, new ShopHolder(shop, null, true)); }

    private void openHolder(Player player, ShopHolder holder) {
        String title = messages.format(player, holder.getShop().getTitle(), vars(holder, pageCount(holder)));
        if (title.length() > 32) title = title.substring(0, 32);
        Inventory inventory = Bukkit.createInventory(holder, holder.getShop().getSize(), title);
        holder.setInventory(inventory);
        render(player, holder);
        player.openInventory(inventory);
        SoundUtil.play(player, holder.getShop().getOpenSound());
    }

    private void render(Player player, ShopHolder holder) {
        long renderVersion = holder.nextRenderVersion();
        Inventory inventory = holder.getInventory();
        inventory.clear();
        List<?> entries = sorted(holder);
        int perPage = holder.getShop().getProductSlotsPerPage();
        int pages = Math.max(1, (entries.size() + perPage - 1) / perPage);
        if (holder.getPage() >= pages) holder.setPage(0);
        int entryOffset = holder.getPage() * perPage;
        int productSlot = 0;
        Map<String, String> vars = vars(holder, pages);
        List<String> layout = holder.getShop().getLayout();
        for (int row = 0; row < layout.size(); row++) for (int col = 0; col < 9; col++) {
            int slot = row * 9 + col;
            IconConfig icon = holder.getShop().getIcons().get(layout.get(row).charAt(col));
            if (icon == null) continue;
            if (icon.getType() == IconType.SHOPS) {
                int index = entryOffset + productSlot++;
                if (index < entries.size()) {
                    Object entry = entries.get(index);
                    inventory.setItem(slot, holder.isCatalog() ? catalogItem(player, holder.getShop(), (ProductConfig) entry) : offerItem(player, holder, (Offer) entry, null));
                    if (!holder.isCatalog()) updateUsage(player, holder, (Offer) entry, slot, renderVersion);
                }
            } else inventory.setItem(slot, ItemStackFactory.fromConfig(player, icon.getDisplay(), messages, vars));
        }
    }

    private void updateUsage(Player player, ShopHolder holder, Offer offer, int slot, long renderVersion) {
        service.usage(offer, player).whenComplete((usage, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error == null && holder.getRenderVersion() == renderVersion && player.getOpenInventory().getTopInventory().getHolder() == holder) holder.getInventory().setItem(slot, offerItem(player, holder, offer, usage));
        }));
    }

    private void reopen(Player player, ShopHolder holder) {
        String title = messages.format(player, holder.getShop().getTitle(), vars(holder, pageCount(holder)));
        if (title.length() > 32) title = title.substring(0, 32);
        Inventory inventory = Bukkit.createInventory(holder, holder.getShop().getSize(), title);
        holder.setInventory(inventory);
        render(player, holder);
        player.openInventory(inventory);
    }

    private ItemStack catalogItem(Player player, ShopConfig shop, ProductConfig product) {
        ItemStack base = service.createDisplay(player, product.getProvider(), product.getItemId());
        if (base == null) base = missing(product.getProvider() + ":" + product.getItemId());
        Map<String, String> vars = new HashMap<>();
        vars.put("minMoney", product.getMoney().getMin().setScale(2, RoundingMode.HALF_UP).toPlainString());
        vars.put("maxMoney", product.getMoney().getMax().setScale(2, RoundingMode.HALF_UP).toPlainString());
        vars.put("minAmount", product.getAmount().getMin().setScale(0, RoundingMode.HALF_UP).toPlainString());
        vars.put("maxAmount", product.getAmount().getMax().setScale(0, RoundingMode.HALF_UP).toPlainString());
        vars.put("personalLimit", product.getPersonalLimit().describeInteger());
        vars.put("serverLimit", shop.getScene() == ShopScene.SERVER ? product.getServerLimit().describeInteger() : "-1");
        vars.put("chance", String.format(Locale.US, "%.2f", product.getChance() * 100D));
        return ItemStackFactory.appendLore(base, messages.list(player, "lore." + (shop.getType() == ShopType.SELL ? "sell-catalog" : "buy-catalog"), vars));
    }

    private ItemStack offerItem(Player player, ShopHolder holder, Offer offer, int[] usage) {
        ItemStack base = service.createDisplay(player, offer.getProvider(), offer.getItemId());
        if (base == null) base = missing(offer.getProvider() + ":" + offer.getItemId());
        int personalUsed = usage == null ? 0 : usage[0], serverUsed = usage == null ? 0 : usage[1];
        Map<String, String> vars = new HashMap<>();
        vars.put("money", offer.getMoney().toPlainString()); vars.put("totalMoney", offer.getTotalMoney().toPlainString()); vars.put("amount", Integer.toString(offer.getAmount()));
        vars.put("number", remaining(offer.getPersonalLimit(), personalUsed)); vars.put("allNumber", remaining(offer.getServerLimit(), serverUsed));
        return ItemStackFactory.appendLore(base, messages.list(player, "lore." + (holder.getShop().getType() == ShopType.SELL ? "sell-offer" : "buy-offer"), vars));
    }

    private String remaining(int limit, int used) { return limit < 0 ? "不限" : Integer.toString(Math.max(0, limit - used)); }
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
    private Map<String,String> vars(ShopHolder holder,int pages){Map<String,String> vars=new HashMap<>();vars.put("page",Integer.toString(holder.getPage()+1));vars.put("page_all",Integer.toString(pages));vars.put("sort",holder.getSort().getDisplay());vars.put("order",holder.isDescending()?"降序":"升序");return vars;}

    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player) || event.getRawSlot() < 0 || event.getRawSlot() >= event.getInventory().getSize()) return;
        Player player=(Player)event.getWhoClicked(); ShopHolder holder=(ShopHolder)event.getInventory().getHolder();
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
        holder.setTrading(true);service.trade(player,holder.getShop(),holder.getSnapshot(),offer,(status,overflow)->{holder.setTrading(false);String path;if(status==UsageResult.Status.SUCCESS)path=holder.getShop().getType()==ShopType.SELL?"shop.buy-success":"shop.sell-success";else if(status==UsageResult.Status.PERSONAL_LIMIT)path="shop.personal-limit";else if(status==UsageResult.Status.SERVER_LIMIT)path="shop.server-limit";else if(status==UsageResult.Status.STALE)path="shop.stale";else path="shop.transaction-failed";messages.send(player,path,tradeVars(holder,offer));SoundUtil.play(player,status==UsageResult.Status.SUCCESS?holder.getShop().getSuccessSound():holder.getShop().getFailSound());if(overflow)messages.send(player,"shop.overflow",tradeVars(holder,offer));if(player.getOpenInventory().getTopInventory().getHolder()==holder)render(player,holder);});
    }

    private Map<String,String> tradeVars(ShopHolder holder,Offer offer){Map<String,String> vars=new HashMap<>();vars.put("shop",holder.getShop().getId());vars.put("item",offer.getItemId());vars.put("item_id",offer.getItemId());vars.put("amount",Integer.toString(offer.getAmount()));vars.put("money",offer.getMoney().toPlainString());vars.put("totalMoney",offer.getTotalMoney().toPlainString());return vars;}
    @EventHandler public void drag(InventoryDragEvent event){if(event.getInventory().getHolder() instanceof ShopHolder)event.setCancelled(true);}
}
