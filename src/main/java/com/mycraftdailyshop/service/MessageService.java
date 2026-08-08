package com.mycraftdailyshop.service;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
        migrateLegacyCommandPrefix();
        messages = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "message.yml"));
        InputStream resource = plugin.getResource("message.yml");
        if (resource != null) try (InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            messages.setDefaults(YamlConfiguration.loadConfiguration(reader));
        } catch (java.io.IOException ignored) { }
    }

    /** Keeps existing server message files in sync with the renamed short command. */
    private void migrateLegacyCommandPrefix() {
        File file = new File(plugin.getDataFolder(), "message.yml");
        if (!file.isFile()) return;
        try {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            if (content.contains("/mds")) {
                Files.write(file.toPath(), content.replace("/mds", "/mcds").getBytes(StandardCharsets.UTF_8));
                plugin.getLogger().info("已将 message.yml 中的旧命令 /mds 更新为 /mcds。");
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("无法更新 message.yml 中的旧命令提示: " + ex.getMessage());
        }
    }

    public void send(CommandSender sender, String path, Map<String, String> variables) {
        String value = messages.getString(path);
        if (value != null && !value.isEmpty()) sender.sendMessage(format(sender instanceof Player ? (Player) sender : null, value, variables));
    }

    public List<String> list(Player player, String path, Map<String, String> variables) {
        return list(player, path, null, variables);
    }

    public List<String> list(Player player, String path, String fallbackPath, Map<String, String> variables) {
        List<String> raw = messages.getStringList(path);
        if (raw.isEmpty() && fallbackPath != null) {
            raw = new ArrayList<>(messages.getStringList(fallbackPath));
            raw.removeIf(line -> line.contains("[serverLimit]") || line.contains("[allNumber]"));
        }
        List<String> result = new ArrayList<>();
        for (String line : raw) result.add(format(player, line, variables));
        return result;
    }

    public String text(Player player, String path, String fallback) {
        String value = messages.getString(path);
        return format(player, value == null || value.isEmpty() ? fallback : value, Collections.emptyMap());
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
