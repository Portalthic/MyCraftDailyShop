package com.mycraftdailyshop.service;

import com.mycraftdailyshop.model.*;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.logging.Level;

public final class ShopRegistry {
    private final JavaPlugin plugin;
    private final Map<String, ShopConfig> shops = new LinkedHashMap<>();
    private RefreshCalculator refreshCalculator;

    public ShopRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public int load() {
        shops.clear();
        refreshCalculator = new RefreshCalculator(ZoneId.of(plugin.getConfig().getString("timezone", "Asia/Shanghai")));
        File directory = new File(plugin.getDataFolder(), "shop");
        if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("无法创建 shop 目录");
        File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null) return 0;
        Arrays.sort(files, Comparator.comparing(File::getName));
        Map<String, File> sources = new HashMap<>();
        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (String id : yaml.getKeys(false)) {
                if (!yaml.isConfigurationSection(id)) continue;
                if (sources.containsKey(id)) {
                    plugin.getLogger().severe("重复的商店 ID '" + id + "': " + sources.get(id).getName() + " 与 " + file.getName());
                    shops.remove(id);
                    continue;
                }
                sources.put(id, file);
                try {
                    ConfigurationSection root = yaml.getConfigurationSection(id);
                    if (!root.getBoolean("enabled", true)) continue;
                    shops.put(id, parse(id, root));
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.SEVERE, "无法加载商店 '" + id + "'（" + file.getName() + "）: " + ex.getMessage());
                }
            }
        }
        return shops.size();
    }

    private ShopConfig parse(String id, ConfigurationSection root) {
        ShopType type = ShopType.parse(required(root, "type"));
        ShopScene scene = ShopScene.parse(required(root, "scene"));
        String refreshType = required(root, "refresh.type");
        refreshCalculator.validate(refreshType);
        String refreshValue = required(root, "refresh.time");
        boolean timely = refreshType.trim().equalsIgnoreCase("timely");
        long refreshIntervalMillis = timely ? refreshCalculator.parseTimelyInterval(refreshValue) : 0L;
        LocalTime refreshTime = timely ? LocalTime.MIDNIGHT : LocalTime.parse(refreshValue);
        List<String> layout = new ArrayList<>(root.getStringList("layout"));
        if (layout.isEmpty() || layout.size() > 6) throw new IllegalArgumentException("layout 必须包含 1 到 6 行");
        for (int i = 0; i < layout.size(); i++) {
            String row = layout.get(i);
            if (row.length() > 9) throw new IllegalArgumentException("layout 第 " + (i + 1) + " 行超过 9 格");
            layout.set(i, String.format("%-9s", row));
        }
        Map<Character, IconConfig> icons = new HashMap<>();
        ConfigurationSection iconRoot = root.getConfigurationSection("icons");
        if (iconRoot == null) throw new IllegalArgumentException("缺少 icons 节点");
        for (String key : iconRoot.getKeys(false)) {
            if (key.length() != 1) throw new IllegalArgumentException("图标键必须是单个字符: " + key);
            ConfigurationSection section = iconRoot.getConfigurationSection(key);
            IconType iconType = IconType.parse(required(section, "type"));
            if (iconType != IconType.SHOPS && section.getConfigurationSection("display") == null) {
                throw new IllegalArgumentException("图标 " + key + " 缺少 display 节点");
            }
            if (iconType != IconType.SHOPS) {
                ConfigurationSection display = section.getConfigurationSection("display");
                String material = display.getString("material", "STONE");
                if (Material.matchMaterial(material.toUpperCase()) == null) throw new IllegalArgumentException("图标 " + key + " 使用了无效材质: " + material);
                int amount = display.getInt("amount", 1);
                if (amount < 1 || amount > 64) throw new IllegalArgumentException("图标 " + key + " 的 amount 必须在 1 到 64 之间");
            }
            icons.put(key.charAt(0), new IconConfig(iconType, section.getConfigurationSection("display")));
        }
        for (String row : layout) for (int i = 0; i < row.length(); i++) {
            char symbol = row.charAt(i);
            if (symbol != ' ' && !icons.containsKey(symbol)) throw new IllegalArgumentException("layout 使用了未定义的图标字符: " + symbol);
        }
        List<ProductConfig> products = new ArrayList<>();
        ConfigurationSection productRoot = root.getConfigurationSection("shops");
        if (productRoot != null) for (String key : productRoot.getKeys(false)) {
            int index;
            try { index = Integer.parseInt(key); } catch (NumberFormatException ex) { throw new IllegalArgumentException("商品索引必须为整数: " + key); }
            ConfigurationSection section = productRoot.getConfigurationSection(key);
            ValueSpec money = ValueSpec.parse(required(section, "money"));
            ValueSpec amount = ValueSpec.parse(section.getString("amount", "1"));
            ValueSpec personalLimit = ValueSpec.parse(section.getString("limit_personal", "-1"));
            ValueSpec serverLimit = ValueSpec.parse(section.getString("limit_server", "-1"));
            if (amount.getMin().compareTo(java.math.BigDecimal.ONE) < 0) throw new IllegalArgumentException("商品 " + index + " 的 amount 最小值必须至少为 1");
            validateLimit(personalLimit, "商品 " + index + " 的 limit_personal");
            validateLimit(serverLimit, "商品 " + index + " 的 limit_server");
            products.add(new ProductConfig(index, required(section, "id"), money, amount, personalLimit, serverLimit, section.getDouble("chance", 1D), parseEnchantments(section, index)));
        }
        products.sort(Comparator.comparingInt(ProductConfig::getIndex));
        ShopConfig shop = new ShopConfig(id, type, scene, refreshType, refreshTime, refreshIntervalMillis,
                root.getString("message.restock"), root.getString("message.noshops"), root.getString("sound.open_shop"),
                root.getString("sound.success"), root.getString("sound.fail"), root.getString("title", id), layout, icons, products);
        validateSound(shop.getOpenSound(), "sound.open_shop");
        validateSound(shop.getSuccessSound(), "sound.success");
        validateSound(shop.getFailSound(), "sound.fail");
        if (shop.getProductSlotsPerPage() < 1) throw new IllegalArgumentException("layout 中没有 shops 类型的商品槽");
        return shop;
    }

    private List<EnchantmentGroupConfig> parseEnchantments(ConfigurationSection product, int index) {
        List<EnchantmentGroupConfig> groups = new ArrayList<>();
        for (Object raw : product.getList("enchantments", Collections.emptyList())) {
            if (!(raw instanceof Map)) throw new IllegalArgumentException("商品 " + index + " 的 enchantments 元素必须是对象");
            Map<?, ?> group = (Map<?, ?>) raw;
            double chance = number(group.get("chance"), 1D, "商品 " + index + " 的附魔 chance");
            if (!finite(chance) || chance < 0 || chance > 1) throw new IllegalArgumentException("商品 " + index + " 的附魔 chance 必须在 0 到 1 之间");
            Object choicesValue = group.get("enchant");
            if (!(choicesValue instanceof List) || ((List<?>) choicesValue).isEmpty()) throw new IllegalArgumentException("商品 " + index + " 的附魔组不能为空");
            List<EnchantmentChoiceConfig> choices = new ArrayList<>();
            for (Object rawChoice : (List<?>) choicesValue) {
                if (!(rawChoice instanceof Map)) throw new IllegalArgumentException("商品 " + index + " 的附魔选择格式错误");
                Map<?, ?> choice = (Map<?, ?>) rawChoice;
                String id = String.valueOf(choice.get("id")).toUpperCase(Locale.ROOT);
                if (org.bukkit.enchantments.Enchantment.getByName(id) == null) throw new IllegalArgumentException("商品 " + index + " 使用了无效附魔: " + id);
                double weight = number(choice.get("weight"), 1D, "商品 " + index + " 的附魔权重");
                if (!finite(weight) || weight <= 0) throw new IllegalArgumentException("商品 " + index + " 的附魔权重必须大于 0");
                List<EnchantmentLevelConfig> levels = new ArrayList<>(); Object levelValue = choice.get("level_list");
                if (levelValue instanceof List) for (Object rawLevel : (List<?>) levelValue) {
                    if (!(rawLevel instanceof Map)) throw new IllegalArgumentException("商品 " + index + " 的附魔等级格式错误");
                    Map<?, ?> level = (Map<?, ?>) rawLevel;
                    int value = integer(level.get("level"), 1, "商品 " + index + " 的附魔等级");
                    double levelWeight = number(level.get("weight"), 1D, "商品 " + index + " 的附魔等级权重");
                    if (value < 1 || !finite(levelWeight) || levelWeight <= 0) throw new IllegalArgumentException("商品 " + index + " 的附魔等级或权重无效");
                    Object premium = level.get("premium");
                    ValueSpec premiumSpec = ValueSpec.parse(premium == null ? "0" : String.valueOf(premium));
                    if (premiumSpec.getMin().signum() < 0) throw new IllegalArgumentException("商品 " + index + " 的附魔溢价不能为负数");
                    levels.add(new EnchantmentLevelConfig(value, levelWeight, premiumSpec));
                }
                if (levels.isEmpty()) levels.add(new EnchantmentLevelConfig(1, 1D, ValueSpec.parse("0")));
                Set<Integer> levelIds = new HashSet<>(); for (EnchantmentLevelConfig level : levels) if (!levelIds.add(level.getLevel())) throw new IllegalArgumentException("商品 " + index + " 的附魔 " + id + " 存在重复等级: " + level.getLevel());
                choices.add(new EnchantmentChoiceConfig(id, weight, levels));
            }
            Set<String> ids = new HashSet<>(); for (EnchantmentChoiceConfig choice : choices) if (!ids.add(choice.getId())) throw new IllegalArgumentException("商品 " + index + " 的同一附魔组存在重复附魔: " + choice.getId());
            groups.add(new EnchantmentGroupConfig(chance, choices));
        }
        Set<String> ids = new HashSet<>(); for (EnchantmentGroupConfig group : groups) for (EnchantmentChoiceConfig choice : group.getChoices()) if (!ids.add(choice.getId())) throw new IllegalArgumentException("商品 " + index + " 的附魔在多个附魔组中重复: " + choice.getId());
        return groups;
    }

    private double number(Object value, double fallback, String path) { try { return value == null ? fallback : Double.parseDouble(String.valueOf(value)); } catch (NumberFormatException ex) { throw new IllegalArgumentException(path + " 必须是数字"); } }
    private int integer(Object value, int fallback, String path) { try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException ex) { throw new IllegalArgumentException(path + " 必须是整数"); } }
    private boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }

    private String required(ConfigurationSection section, String path) {
        String value = section == null ? null : section.getString(path);
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("缺少配置项 " + path);
        return value;
    }

    private void validateLimit(ValueSpec spec, String path) {
        boolean unlimited = spec.getMin().compareTo(java.math.BigDecimal.valueOf(-1)) == 0 && spec.getMax().compareTo(java.math.BigDecimal.valueOf(-1)) == 0;
        if (!unlimited && spec.getMin().signum() < 0) throw new IllegalArgumentException(path + " 只能为 -1 或非负数");
    }

    private void validateSound(String value, String path) {
        if (value == null || value.trim().isEmpty() || value.equalsIgnoreCase("null")) return;
        try { Sound.valueOf(value.trim().replace('.', '_').toUpperCase()); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException(path + " 使用了无效的 1.12.2 音效: " + value); }
    }

    public ShopConfig get(String id) { return shops.get(id); }
    public Collection<ShopConfig> all() { return Collections.unmodifiableCollection(shops.values()); }
    public Set<String> ids() { return Collections.unmodifiableSet(shops.keySet()); }
    public RefreshCalculator getRefreshCalculator() { return refreshCalculator; }
}
