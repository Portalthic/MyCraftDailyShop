package com.mycraftdailyshop.command;

import com.mycraftdailyshop.gui.ShopGui;
import com.mycraftdailyshop.model.ProductConfig;
import com.mycraftdailyshop.model.ShopConfig;
import com.mycraftdailyshop.model.ShopScene;
import com.mycraftdailyshop.service.*;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class DailyShopCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;private final ShopRegistry registry;private final ShopService service;private final ShopGui gui;private final MessageService messages;private final MaintenanceScheduler scheduler;
    public DailyShopCommand(JavaPlugin plugin,ShopRegistry registry,ShopService service,ShopGui gui,MessageService messages,MaintenanceScheduler scheduler){this.plugin=plugin;this.registry=registry;this.service=service;this.gui=gui;this.messages=messages;this.scheduler=scheduler;}

    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(args.length==0){help(sender);return true;}String root=args[0].toLowerCase();
        if(!permission(sender,"mycraftdailyshop.command."+root))return true;
        try{
            switch(root){
                case"open":return open(sender,args,false);
                case"show":return open(sender,args,true);
                case"quota":return quota(sender,args);
                case"refresh":return refresh(sender,args);
                case"reload":return reload(sender,args);
                case"validate":return validate(sender,args);
                default:messages.send(sender,"command.invalid-usage",null);return true;
            }
        }catch(Exception ex){Map<String,String>vars=map("reason",ex.getMessage());messages.send(sender,"command.failed",vars);return true;}
    }

    private boolean open(CommandSender sender,String[] args,boolean catalog){if(args.length!=3){messages.send(sender,"command.invalid-usage",null);return true;}Player player=Bukkit.getPlayerExact(args[1]);if(player==null){messages.send(sender,"command.player-not-found",map("player",args[1]));return true;}ShopConfig shop=registry.get(args[2]);if(shop==null){messages.send(sender,"command.shop-not-found",map("shop",args[2]));return true;}if(catalog)gui.show(player,shop);else gui.open(player,shop);return true;}

    private boolean quota(CommandSender sender,String[] args){
        if(args.length<4||!args[1].equalsIgnoreCase("reset")){messages.send(sender,"command.invalid-usage",null);return true;}
        if(args[2].equalsIgnoreCase("server")&&args.length==4){String shop=args[3];if(!validShopOrAll(sender,shop))return true;complete(sender,service.resetServer(shop));return true;}
        if(args[2].equalsIgnoreCase("player")&&args.length==5){String player=args[3],shop=args[4];if(!validShopOrAll(sender,shop))return true;if(player.equals("*")){complete(sender,service.resetPlayer("*",shop));return true;}service.findPlayerUuid(player).whenComplete((uuid,error)->{if(error!=null)failed(sender,error);else if(uuid==null)sync(()->messages.send(sender,"command.player-not-found",map("player",player)));else complete(sender,service.resetPlayer(uuid,shop));});return true;}
        messages.send(sender,"command.invalid-usage",null);return true;
    }

    private boolean refresh(CommandSender sender,String[] args){
        if(args.length!=2&&args.length!=4){messages.send(sender,"command.invalid-usage",null);return true;}String shopId=args[1];if(!validShopOrAll(sender,shopId))return true;
        String playerArg=null;if(args.length==4){if(!args[2].equalsIgnoreCase("--player")){messages.send(sender,"command.invalid-usage",null);return true;}playerArg=args[3];if(shopId.equals("*")){messages.send(sender,"command.failed",map("reason","使用 --player 时必须指定一个个人商店"));return true;}ShopConfig shop=registry.get(shopId);if(shop.getScene()!=ShopScene.PERSONAL){messages.send(sender,"command.failed",map("reason","全服商店不能指定玩家"));return true;}}
        if(playerArg!=null){String target=playerArg;service.findPlayerUuid(target).whenComplete((uuid,error)->{if(error!=null)failed(sender,error);else if(uuid==null)sync(()->messages.send(sender,"command.player-not-found",map("player",target)));else complete(sender,service.invalidate(shopId,uuid));});return true;}
        CompletableFuture<Void> future;
        if(shopId.equals("*"))future=service.invalidate("*",null);else future=service.invalidate(shopId,registry.get(shopId).getScene()==ShopScene.SERVER?"SERVER":null);
        future.whenComplete((unused,error)->sync(()->{if(error!=null)messages.send(sender,"command.failed",map("reason",rootMessage(error)));else{messages.send(sender,"command.success",null);if(shopId.equals("*"))for(ShopConfig shop:registry.all())scheduler.announce(shop);else scheduler.announce(registry.get(shopId));}}));return true;
    }

    private boolean reload(CommandSender sender,String[]args){if(args.length!=1){messages.send(sender,"command.invalid-usage",null);return true;}plugin.reloadConfig();messages.load();int count=registry.load();scheduler.reloadCycles();messages.send(sender,"command.reload-success",map("shops",Integer.toString(count)));return true;}
    private boolean validate(CommandSender sender,String[]args){if(args.length!=1){messages.send(sender,"command.invalid-usage",null);return true;}int errors=0;for(ShopConfig shop:registry.all())for(ProductConfig product:shop.getProducts())if(!service.providerExists(product)){sender.sendMessage("§c[MyCraftDailyShop] "+shop.getId()+" 的商品 "+product.getIndex()+" 不存在: "+product.getProvider()+":"+product.getItemId());errors++;}if(errors==0)messages.send(sender,"command.validate-success",map("shops",Integer.toString(registry.all().size())));else messages.send(sender,"command.failed",map("reason","发现 "+errors+" 个无效商品"));return true;}
    private boolean validShopOrAll(CommandSender sender,String value){if(value.equals("*")||registry.get(value)!=null)return true;messages.send(sender,"command.shop-not-found",map("shop",value));return false;}
    private boolean permission(CommandSender sender,String node){if(sender.hasPermission(node))return true;messages.send(sender,"command.no-permission",null);return false;}
    private void help(CommandSender sender){for(String line:messages.rawList("command.help"))sender.sendMessage(messages.format(sender instanceof Player?(Player)sender:null,line,null));}
    private void complete(CommandSender sender,CompletableFuture<Void> future){future.whenComplete((unused,error)->sync(()->{if(error==null)messages.send(sender,"command.success",null);else messages.send(sender,"command.failed",map("reason",rootMessage(error)));}));}
    private void failed(CommandSender sender,Throwable error){sync(()->messages.send(sender,"command.failed",map("reason",rootMessage(error))));}
    private void sync(Runnable runnable){Bukkit.getScheduler().runTask(plugin,runnable);}
    private String rootMessage(Throwable error){Throwable root=error;while(root.getCause()!=null)root=root.getCause();return root.getMessage()==null?root.getClass().getSimpleName():root.getMessage();}
    private Map<String,String> map(String...values){Map<String,String>map=new HashMap<>();for(int i=0;i+1<values.length;i+=2)map.put(values[i],values[i+1]);return map;}

    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[]args){
        List<String> values=new ArrayList<>();if(args.length==1)values.addAll(Arrays.asList("open","show","quota","refresh","reload","validate"));
        else if(args.length==2&&(args[0].equalsIgnoreCase("open")||args[0].equalsIgnoreCase("show")))for(Player player:Bukkit.getOnlinePlayers())values.add(player.getName());
        else if(args.length==3&&(args[0].equalsIgnoreCase("open")||args[0].equalsIgnoreCase("show")))values.addAll(registry.ids());
        else if(args.length==2&&args[0].equalsIgnoreCase("quota"))values.add("reset");
        else if(args.length==3&&args[0].equalsIgnoreCase("quota"))values.addAll(Arrays.asList("player","server"));
        else if(args.length==4&&args[0].equalsIgnoreCase("quota")&&args[2].equalsIgnoreCase("player")){values.add("*");for(Player player:Bukkit.getOnlinePlayers())values.add(player.getName());}
        else if((args.length==4&&args[0].equalsIgnoreCase("quota")&&args[2].equalsIgnoreCase("server"))||(args.length==5&&args[0].equalsIgnoreCase("quota")&&args[2].equalsIgnoreCase("player"))){values.add("*");values.addAll(registry.ids());}
        else if(args.length==2&&args[0].equalsIgnoreCase("refresh")){values.add("*");values.addAll(registry.ids());}
        else if(args.length==3&&args[0].equalsIgnoreCase("refresh"))values.add("--player");
        else if(args.length==4&&args[0].equalsIgnoreCase("refresh"))for(Player player:Bukkit.getOnlinePlayers())values.add(player.getName());
        String prefix=args[args.length-1].toLowerCase();values.removeIf(value->!value.toLowerCase().startsWith(prefix));return values;
    }
}
