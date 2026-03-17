package com.Teenkung.devReforgePlus.Config.TypeConfig;

import com.Teenkung.devReforgePlus.DevReforgePlus;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

/**
 * The {@code TypeConfig} class is responsible for loading and managing item-type configurations
 * for the {@code DevReforgePlus} plugin.
 *
 * <p>Each YAML file in the {@code types/} directory maps one or more MMOItems type strings
 * (e.g. {@code SWORD}, {@code BOW}) to a weighted table of modifier IDs. When a reforge is
 * triggered on an item, the appropriate {@link TypeData} is looked up by the item's MMOItems
 * type, and a modifier is randomly selected according to the declared weights.</p>
 *
 * <p>Expected YAML structure:</p>
 * <pre>
 * # MMOItems type(s) that this config covers
 * type:
 *   - SWORD
 *
 * # Modifier IDs and their relative selection weights
 * reforges:
 *   example_reforge: 10
 *   powerful_reforge: 5
 * </pre>
 *
 * <p>Configurations are loaded from {@code .yml}/{@code .yaml} files inside the {@code types/}
 * sub-directory of the plugin's data folder. The directory and a default {@code sword.yml} are
 * created automatically on first run.</p>
 */
public class TypeConfig {

    private final DevReforgePlus plugin;

    /**
     * Primary lookup: MMOItems type string (upper-case) → {@link TypeData}.
     * A single {@link TypeData} may be registered under multiple keys if its
     * {@code type} list contains more than one entry.
     */
    private final Map<String, TypeData> typeMap = new HashMap<>();

    /** All loaded {@link TypeData} instances, keyed by the config file's stem name. */
    private final Map<String, TypeData> allTypes = new HashMap<>();

    public TypeConfig(DevReforgePlus plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    // -------------------------------------------------------------------------
    // Loading
    // -------------------------------------------------------------------------

    /**
     * Loads (or reloads) all type definitions from the {@code types/} directory.
     * Creates the directory and a default {@code sword.yml} when first run.
     */
    public void loadConfig() {
        typeMap.clear();
        allTypes.clear();

        File directory = new File(plugin.getDataFolder(), "types");
        if (!directory.exists()) {
            //noinspection ResultOfMethodCallIgnored
            directory.mkdir();
            plugin.saveResource("types/sword.yml", false);
        }

        plugin.getLogger().info("Loading type configs from " + directory.getAbsolutePath());
        loadDirectory(directory);
        plugin.getLogger().info("Loaded " + allTypes.size() + " type config(s) covering " + typeMap.size() + " MMOItems type(s)");
    }

    /**
     * Recursively processes all {@code .yml}/{@code .yaml} files inside {@code dir}.
     *
     * @param dir the directory to scan
     */
    private void loadDirectory(File dir) {
        File[] children = dir.listFiles();
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
     * Parses a single type YAML file and registers the resulting {@link TypeData}.
     *
     * @param file the YAML file to parse
     */
    private void loadFile(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        // --- type list ---
        List<String> rawTypes = config.getStringList("type");
        if (rawTypes.isEmpty()) {
            plugin.getLogger().warning("Type file " + file.getName() + " has no 'type' list. Skipping.");
            return;
        }
        List<String> types = new ArrayList<>();
        for (String t : rawTypes) {
            types.add(t.toUpperCase(Locale.ROOT));
        }

        // --- reforges weight map ---
        ConfigurationSection reforgesSection = config.getConfigurationSection("reforges");
        if (reforgesSection == null) {
            plugin.getLogger().warning("Type file " + file.getName() + " is missing a 'reforges' section. Skipping.");
            return;
        }

        Map<String, Double> weights = new HashMap<>();
        for (String modifierId : reforgesSection.getKeys(false)) {
            double weight = reforgesSection.getDouble(modifierId);
            if (weight <= 0) {
                plugin.getLogger().warning("Type file " + file.getName() + ": modifier '" + modifierId
                        + "' has weight " + weight + " (must be > 0). Skipping entry.");
                continue;
            }
            weights.put(modifierId, weight);
        }

        if (weights.isEmpty()) {
            plugin.getLogger().warning("Type file " + file.getName() + " has no valid reforge entries. Skipping.");
            return;
        }

        // --- optional display_weights map ---
        Map<String, Double> displayWeights = new HashMap<>();
        ConfigurationSection displaySection = config.getConfigurationSection("display_weights");
        if (displaySection != null) {
            for (String modifierId : displaySection.getKeys(false)) {
                double dw = displaySection.getDouble(modifierId);
                if (dw > 0) {
                    displayWeights.put(modifierId, dw);
                } else {
                    plugin.getLogger().warning("Type file " + file.getName() + ": display_weight for '"
                            + modifierId + "' must be > 0. Skipping.");
                }
            }
        }

        // Derive a human-readable key from the filename (without extension)
        String fileKey = file.getName().replaceFirst("\\.[^.]+$", "");
        TypeData data = new TypeData(types, weights, displayWeights);
        allTypes.put(fileKey, data);

        // Register under each declared MMOItems type
        for (String t : types) {
            if (typeMap.containsKey(t)) {
                plugin.getLogger().warning("Type file " + file.getName() + ": MMOItems type '" + t
                        + "' is already registered by another config. Overwriting.");
            }
            typeMap.put(t, data);
        }
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link TypeData} for the given MMOItems type string (case-insensitive),
     * or {@code null} if no config covers that type.
     *
     * @param mmoItemsType the MMOItems type string, e.g. {@code "SWORD"}
     * @return the matching {@link TypeData}, or {@code null}
     */
    public TypeData getTypeData(String mmoItemsType) {
        return typeMap.get(mmoItemsType.toUpperCase(Locale.ROOT));
    }

    /**
     * Returns {@code true} if a type config exists for the given MMOItems type.
     *
     * @param mmoItemsType the MMOItems type string
     * @return {@code true} when a config is registered for this type
     */
    public boolean hasTypeData(String mmoItemsType) {
        return typeMap.containsKey(mmoItemsType.toUpperCase(Locale.ROOT));
    }

    /**
     * Returns an unmodifiable view of the MMOItems-type → {@link TypeData} lookup map.
     *
     * @return all registered type mappings
     */
    public Map<String, TypeData> getTypeMap() {
        return Collections.unmodifiableMap(typeMap);
    }

    /**
     * Returns an unmodifiable view of all loaded {@link TypeData} instances,
     * keyed by their config file stem (e.g. {@code "sword"}).
     *
     * @return all loaded type configs
     */
    public Map<String, TypeData> getAllTypes() {
        return Collections.unmodifiableMap(allTypes);
    }
}

