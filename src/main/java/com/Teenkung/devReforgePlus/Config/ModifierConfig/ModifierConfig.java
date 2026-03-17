package com.Teenkung.devReforgePlus.Config.ModifierConfig;

import com.Teenkung.devReforgePlus.Config.TypeConfig.TypeConfig;
import com.Teenkung.devReforgePlus.DevReforgePlus;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The {@code ModifierConfig} class is responsible for managing and loading modifier configurations
 * for the {@code DevReforgePlus} plugin. Modifier configurations define various customizable
 * attributes such as IDs, display names, and the stat bonuses each modifier provides.
 *
 * <p>Each modifier YAML defines <em>what</em> stats it changes and by how much ({@link ReforgeStatData}).
 * The <em>selection weight</em> of a modifier for a given item type is intentionally <strong>not</strong>
 * stored here — it belongs in the type configuration files loaded by
 * {@link TypeConfig}.</p>
 *
 * <p>Configurations are loaded from YAML files (with {@code .yml} or {@code .yaml} extensions) located
 * within the {@code modifiers/} sub-directory of the plugin's data folder. The directory and an
 * example file are created automatically if they do not yet exist.</p>
 **/
public class ModifierConfig {

    private final DevReforgePlus plugin;
    private final Map<String, ModifierData> modifiers = new HashMap<>();

    public ModifierConfig(DevReforgePlus plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * Loads (or reloads) all modifier definitions from the {@code modifiers/} directory.
     * Creates the directory and an example file when first run.
     **/
    public void loadConfig() {
        modifiers.clear();
        File directory = new File(plugin.getDataFolder(), "modifiers");
        if (!directory.exists()) {
            //noinspection ResultOfMethodCallIgnored
            directory.mkdir();
            plugin.saveResource("modifiers/example_reforge.yml", false);
        }
        plugin.getLogger().info("Loading modifiers from " + directory.getAbsolutePath());
        loadDirectory(directory);
        plugin.getLogger().info("Loaded " + modifiers.size() + " modifiers");
    }

    /**
     * Recursively processes all {@code .yml}/{@code .yaml} files inside {@code file}.
     *
     * @param file the directory (or file) to scan
     **/
    private void loadDirectory(File file) {
        File[] children = file.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (f.isDirectory()) {
                loadDirectory(f);
                continue;
            }
            if (!(f.getName().endsWith(".yml") || f.getName().endsWith(".yaml"))) continue;
            loadFile(f);
        }
    }

    /**
     * Parses a single modifier YAML file.
     *
     * <p>Expected structure:</p>
     * <pre>
     * id: example_reforge
     * displayName: "Example"   # optional, defaults to id
     * stats:
     *   ATTACK_DAMAGE:
     *     Type: FLAT            # FLAT or SCALE
     *     Value: 1.5
     * </pre>
     *
     * @param file the YAML file to load
     **/
    private void loadFile(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        String id = config.getString("id");
        if (id == null) {
            plugin.getLogger().warning("Modifier file " + file.getName() + " is missing an 'id' field. Skipping.");
            return;
        }

        String display = config.getString("displayName", id);
        Map<String, ReforgeStatData> stats = loadStatMap(config, file.getName());
        if (stats == null) {
            plugin.getLogger().warning("Modifier file " + file.getName() + " is missing a 'stats' section. Skipping.");
            return;
        }

        modifiers.put(id, new ModifierData(id, display, stats));
    }

    /**
     * Reads the {@code stats} section of a modifier config and converts each entry into a
     * {@link ReforgeStatData} holding the {@link ReforgeStatType} and the numeric value.
     *
     * <p>Example YAML entry:</p>
     * <pre>
     * stats:
     *   ATTACK_DAMAGE:
     *     Type: FLAT
     *     Value: 1.5
     * </pre>
     *
     * @param config   the loaded YAML configuration
     * @param fileName the file name used only for warning messages
     * @return a map of stat-key → {@link ReforgeStatData}, or {@code null} if the section is absent
     **/
    private Map<String, ReforgeStatData> loadStatMap(YamlConfiguration config, String fileName) {
        ConfigurationSection section = config.getConfigurationSection("stats");
        if (section == null) return null;

        Map<String, ReforgeStatData> map = new HashMap<>();
        for (String statKey : section.getKeys(false)) {
            ConfigurationSection statSection = section.getConfigurationSection(statKey);
            if (statSection == null) {
                plugin.getLogger().warning("Modifier file " + fileName + ": stat '" + statKey + "' is not a section. Skipping stat.");
                continue;
            }

            String rawType = statSection.getString("Type");
            if (rawType == null) {
                plugin.getLogger().warning("Modifier file " + fileName + ": stat '" + statKey + "' is missing 'Type'. Skipping stat.");
                continue;
            }

            ReforgeStatType type;
            try {
                type = ReforgeStatType.valueOf(rawType.toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Modifier file " + fileName + ": stat '" + statKey + "' has unknown Type '" + rawType + "'. Skipping stat.");
                continue;
            }

            double value = statSection.getDouble("Value");
            Double fallback = statSection.contains("Fallback") ? statSection.getDouble("Fallback") : null;
            map.put(statKey, new ReforgeStatData(type, value, fallback));
        }
        return map;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link ModifierData} for the given modifier ID, or {@code null} if not found.
     *
     * @param id the modifier ID
     * @return the corresponding data, or {@code null}
     **/
    public ModifierData getModifier(String id) {
        return modifiers.get(id);
    }

    /**
     * Returns an unmodifiable view of all loaded modifiers, keyed by their ID.
     *
     * @return all loaded modifiers
     **/
    public Map<String, ModifierData> getModifiers() {
        return Collections.unmodifiableMap(modifiers);
    }
}

