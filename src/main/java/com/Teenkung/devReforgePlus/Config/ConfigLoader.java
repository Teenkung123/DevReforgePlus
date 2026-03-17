package com.Teenkung.devReforgePlus.Config;

import com.Teenkung.devReforgePlus.Config.ModifierConfig.ModifierConfig;
import com.Teenkung.devReforgePlus.Config.ModifierConfig.StatLangConfig;
import com.Teenkung.devReforgePlus.Config.TypeConfig.TypeConfig;
import com.Teenkung.devReforgePlus.DevReforgePlus;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Central entry point for loading all plugin configuration files.
 *
 * <p>Instantiate this class once during plugin startup (inside {@code onEnable}) and
 * retrieve the individual config objects via their getters. Call {@link #loadConfig()}
 * again at any time to reload everything from disk (e.g. on {@code /drp reload}).</p>
 */
public class ConfigLoader {

    private final DevReforgePlus plugin;

    @Getter
    private ModifierConfig modifierConfig;
    @Getter
    private TypeConfig typeConfig;
    @Getter
    private StatLangConfig statLangConfig;
    @Getter
    private FileConfiguration config;

    @Getter
    public double basePrice;
    @Getter
    public double priceScaling;
    @Getter
    public double maxPrice;
    @Getter
    public boolean roundingEnabled;

    public ConfigLoader(DevReforgePlus plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * Loads (or reloads) all configuration sub-systems in the correct dependency order:
     * <ol>
     *   <li>{@link ModifierConfig} — defines what each reforge modifier does.</li>
     *   <li>{@link TypeConfig} — defines which modifiers are available per item type and their weights.</li>
     * </ol>
     */
    public void loadConfig() {
        plugin.getLogger().info("Loading configuration...");
        plugin.reloadConfig();
        plugin.saveDefaultConfig();
        config = plugin.getConfig();
        statLangConfig = new StatLangConfig(plugin);
        modifierConfig = new ModifierConfig(plugin);
        typeConfig = new TypeConfig(plugin);
        basePrice      = config.getDouble("Economy.Base", 10000);
        priceScaling   = config.getDouble("Economy.Multiplier", 1.25);
        maxPrice       = config.getDouble("Economy.Max", 1000000);
        roundingEnabled = config.getBoolean("Economy.Rounding", true);
    }

    public double getCostAtAttempt(int attempt) {
        if (roundingEnabled) {
            return Math.round(Math.min(basePrice * Math.pow(priceScaling, attempt - 1), maxPrice));
        } else {
            return Math.min(basePrice * Math.pow(priceScaling, attempt - 1), maxPrice);
        }
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Reads a localised message from {@code Messages.<key>}, applying
     * the provided placeholder pairs (alternating key / value strings).
     *
     * @param key          dot-path under {@code Messages}
     * @param defaultValue fallback if the key is absent
     * @param replacements alternating {"<placeholder>", "value", ...}
     */
    public String getMessage(String key, String defaultValue, String... replacements) {
        String raw = config.getString("Messages." + key, defaultValue);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            raw = raw.replace(replacements[i], replacements[i + 1]);
        }
        return raw;
    }

    public String getDisplayLoreLine() {
        return config.getString("ReforgeDisplay.LoreLine", "<white>ヸ โบนัสการตีบวก: <white><reforge_display>");
    }

    public String getDisplayStatFormat() {
        return config.getString("ReforgeDisplay.Stats.Format", "<white>    <stat_format>");
    }

}


