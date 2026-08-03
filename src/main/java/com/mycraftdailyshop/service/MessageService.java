package com.mycraftdailyshop.service;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;

public final class MessageService {
    private final JavaPlugin plugin;
    private YamlConfiguration messages;
    private Method papiMethod;

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
        Plugin papi = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI");
        if (papi != null) try {
            Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            papiMethod = api.getMethod("setPlaceholders", Player.class, String.class);
        } catch (ReflectiveOperationException ignored) { }
    }

    public void load() {
        messages = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "message.yml"));
    }

    public void send(CommandSender sender, String path, Map<String, String> variables) {
        String value = messages.getString(path);
        if (value != null && !value.isEmpty()) sender.sendMessage(format(sender instanceof Player ? (Player) sender : null, value, variables));
    }

    public List<String> list(Player player, String path, Map<String, String> variables) {
        List<String> result = new ArrayList<>();
        for (String line : messages.getStringList(path)) result.add(format(player, line, variables));
        return result;
    }

    public String format(Player player, String input, Map<String, String> variables) {
        if (input == null) return null;
        Map<String, String> all = new HashMap<>();
        all.put("prefix", messages.getString("prefix", ""));
        if (variables != null) all.putAll(variables);
        String result = input;
        for (Map.Entry<String, String> entry : all.entrySet()) result = result.replace("[" + entry.getKey() + "]", String.valueOf(entry.getValue()));
        if (player != null && papiMethod != null) try { result = (String) papiMethod.invoke(null, player, result); }
        catch (ReflectiveOperationException ignored) { }
        return ChatColor.translateAlternateColorCodes('&', result);
    }

    public List<String> rawList(String path) { return messages.getStringList(path); }
}
