package com.mycraftdailyshop;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResourceConfigTest {
    @Test void bundledYamlFilesAreReadable() {
        Map<String, Object> config = load("config.yml");
        Map<String, Object> messages = load("message.yml");
        Map<String, Object> shop = load("shop/default.yml");
        assertEquals(200, section(config, "shop").get("click_cooldown"));
        assertFalse(((List<?>) section(messages, "command").get("help")).isEmpty());
        assertTrue(shop.get("default_sellshop") instanceof Map);
    }

    private Map<String, Object> load(String path) {
        InputStream input = getClass().getClassLoader().getResourceAsStream(path);
        assertNotNull(input, path);
        return new Yaml().load(new InputStreamReader(input, StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> section(Map<String, Object> root, String key) { return (Map<String, Object>) root.get(key); }
}
