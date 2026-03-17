package com.Teenkung.devReforgePlus.Config.ModifierConfig;

import com.Teenkung.devReforgePlus.DevReforgePlus;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class StatLangConfig {

    private final DevReforgePlus plugin;
    private final File configFile;
    private FileConfiguration config;

    // Outer map: Stat ID (or "DEFAULT")
    // Inner map: "FLAT", "FLAT_NEGATIVE", "SCALE", "SCALE_NEGATIVE" -> format string
    private final Map<String, Map<String, String>> statFormats = new HashMap<>();

    public StatLangConfig(DevReforgePlus plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "lang/stats.yml");
        loadConfig();
    }

    public void loadConfig() {
        statFormats.clear();

        // Save default if it doesn't exist
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            plugin.saveResource("lang/stats.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        // Merge defaults if config doesn't have complete entries
        InputStream defConfigStream = plugin.getResource("lang/stats.yml");
        if (defConfigStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream));
            config.setDefaults(defConfig);
        }

        ConfigurationSection statsSection = config.getConfigurationSection("stats");
        if (statsSection == null) {
            plugin.getLogger().warning("No 'stats' section found in lang/stats.yml!");
            return;
        }

        for (String statId : statsSection.getKeys(false)) {
            ConfigurationSection typeSection = statsSection.getConfigurationSection(statId);
            if (typeSection == null) continue;

            Map<String, String> formatMap = new HashMap<>();
            for (String formatType : typeSection.getKeys(false)) {
                formatMap.put(formatType.toUpperCase(), typeSection.getString(formatType));
            }
            statFormats.put(statId.toUpperCase(), formatMap);
        }
    }

    /**
     * Retrieves the MiniMessage formatted string for a specific stat.
     * Replaces %value%, %abs_value%, %percent%, and %stat% placeholders.
     */
    public String getFormattedStat(String statId, ReforgeStatData data) {
        double val = data.value();
        // Adjust value for SCALE type: 1.1 -> +0.1 (+10%), 0.9 -> -0.1 (-10%)
        if (data.type() == ReforgeStatType.SCALE) {
            val = val - 1.0;
        }

        String formatType = determineFormatType(data.type(), val);

        // Try precise stat ID first, fallback to DEFAULT
        Map<String, String> formats = statFormats.getOrDefault(statId.toUpperCase(), statFormats.get("DEFAULT"));
        if (formats == null) {
            return "<gray>Missing stat format: " + statId;
        }

        String rawFormat = formats.get(formatType);
        if (rawFormat == null) {
            // Fallback to DEFAULT's format if the specific stat is missing this polarity
            Map<String, String> defFormats = statFormats.get("DEFAULT");
            if (defFormats != null) {
                rawFormat = defFormats.get(formatType);
            }
            if (rawFormat == null) {
                return "<gray>Missing format " + formatType + " for " + statId;
            }
        }

        String absValueStr = formatDouble(Math.abs(val));
        String valueStr = formatDouble(val);
        String percentStr = formatDouble(Math.abs(val) * 100);

        return rawFormat
                .replace("%value%", valueStr)
                .replace("%abs_value%", absValueStr)
                .replace("%percent%", percentStr)
                .replace("%stat%", statId);
    }

    private String determineFormatType(ReforgeStatType type, double value) {
        if (type == ReforgeStatType.FLAT) {
            return value < 0 ? "FLAT_NEGATIVE" : "FLAT";
        } else {
            return value < 0 ? "SCALE_NEGATIVE" : "SCALE";
        }
    }

    private String formatDouble(double value) {
        if (value % 1.0 == 0) {
            return String.valueOf((long) value);
        } else {
            return String.format("%.2f", value);
        }
    }
}
