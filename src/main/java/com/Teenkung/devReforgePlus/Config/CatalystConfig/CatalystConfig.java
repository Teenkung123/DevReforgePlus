package com.Teenkung.devReforgePlus.Config.CatalystConfig;

import com.Teenkung.devReforgePlus.DevReforgePlus;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads catalyst definitions from YAML files in the {@code catalysts/} directory.
 *
 * <p>Each YAML file defines one catalyst item, its MMOItems detection criteria
 * (type + ID), and the list of actions it applies during a reforge.</p>
 *
 * <p>Expected YAML structure:</p>
 * <pre>
 * id: reset_crystal
 * displayName: "&lt;aqua&gt;Reset Crystal"
 * Detection:
 *   MMOItemType: CONSUMABLE
 *   MMOItemId: reset_crystal
 * Actions:
 *   - Type: RESET_COUNT
 *   - Type: REDUCE_COUNT
 *     Amount: 3
 *   - Type: GUARANTEED_GROUP
 *     Groups:
 *       - legendary
 *   - Type: GUARANTEED_MODIFIER
 *     Modifiers:
 *       - savage
 * </pre>
 */
public class CatalystConfig {

    private final DevReforgePlus plugin;
    /** Catalysts keyed by their ID. */
    private final Map<String, CatalystData> catalysts = new HashMap<>();
    /** Cache: "TYPE:ID" → CatalystData for fast item lookup. */
    private final Map<String, CatalystData> byTypeAndId = new HashMap<>();

    public CatalystConfig(DevReforgePlus plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        catalysts.clear();
        byTypeAndId.clear();

        File directory = new File(plugin.getDataFolder(), "catalysts");
        if (!directory.exists()) {
            //noinspection ResultOfMethodCallIgnored
            directory.mkdir();
            plugin.saveResource("catalysts/example_catalyst.yml", false);
        }

        plugin.getLogger().info("Loading catalysts from " + directory.getAbsolutePath());
        loadDirectory(directory);
        plugin.getLogger().info("Loaded " + catalysts.size() + " catalyst(s)");
    }

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

    private void loadFile(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        String id = config.getString("id");
        if (id == null) {
            plugin.getLogger().warning("Catalyst file " + file.getName() + " is missing 'id'. Skipping.");
            return;
        }

        String displayName = config.getString("displayName", id);

        ConfigurationSection detection = config.getConfigurationSection("Detection");
        if (detection == null) {
            plugin.getLogger().warning("Catalyst file " + file.getName() + " is missing 'Detection' section. Skipping.");
            return;
        }

        String mmoItemType = detection.getString("MMOItemType");
        String mmoItemId = detection.getString("MMOItemId");
        if (mmoItemType == null || mmoItemId == null) {
            plugin.getLogger().warning("Catalyst file " + file.getName() + " is missing MMOItemType or MMOItemId. Skipping.");
            return;
        }

        List<CatalystAction> actions = loadActions(config.getList("Actions"), file.getName());

        CatalystData data = new CatalystData(id, displayName, mmoItemType.toUpperCase(), mmoItemId.toLowerCase(), actions);
        catalysts.put(id, data);
        byTypeAndId.put(mmoItemType.toUpperCase() + ":" + mmoItemId.toLowerCase(), data);
    }

    private List<CatalystAction> loadActions(@Nullable java.util.List<?> rawList, String fileName) {
        List<CatalystAction> result = new ArrayList<>();
        if (rawList == null) return result;

        for (Object raw : rawList) {
            if (!(raw instanceof Map<?, ?> map)) {
                plugin.getLogger().warning("Catalyst file " + fileName + ": action entry is not a map. Skipping.");
                continue;
            }

            String rawType = String.valueOf(map.get("Type"));
            CatalystActionType actionType;
            try {
                actionType = CatalystActionType.valueOf(rawType.toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Catalyst file " + fileName + ": unknown action type '" + rawType + "'. Skipping action.");
                continue;
            }

            int amount = 0;
            if (map.containsKey("Amount")) {
                try {
                    amount = Integer.parseInt(String.valueOf(map.get("Amount")));
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Catalyst file " + fileName + ": invalid Amount for action " + rawType + ". Using 0.");
                }
            }

            List<String> groups = new ArrayList<>();
            if (map.containsKey("Groups") && map.get("Groups") instanceof List<?> groupList) {
                for (Object g : groupList) groups.add(String.valueOf(g).toLowerCase());
            }

            List<String> modifierIds = new ArrayList<>();
            if (map.containsKey("Modifiers") && map.get("Modifiers") instanceof List<?> modList) {
                for (Object m : modList) modifierIds.add(String.valueOf(m));
            }

            result.add(new CatalystAction(actionType, amount, groups, modifierIds));
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Lookups
    // -------------------------------------------------------------------------

    /**
     * Tries to find a matching {@link CatalystData} for the given item by reading its
     * MMOItems type and ID from the item's NBT tags.
     *
     * @return matching {@link CatalystData}, or {@code null} if the item is not a catalyst
     */
    public @Nullable CatalystData getByItem(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        try {
            // Use Type.get to determine MMOItems type
            net.Indyuce.mmoitems.api.Type type = Type.get(item);
            if (type == null) return null;
            String typeId = type.getId().toUpperCase();

            // Read item ID from NBT
            LiveMMOItem live = new LiveMMOItem(item);
            String itemId = live.getNBT().getString("MMOITEMS_ITEM_ID");
            if (itemId == null || itemId.isEmpty()) return null;

            return byTypeAndId.get(typeId + ":" + itemId.toLowerCase());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns the {@link CatalystData} for the given ID, or {@code null} if not loaded.
     */
    public @Nullable CatalystData getById(String id) {
        return catalysts.get(id);
    }

    /**
     * Returns an unmodifiable view of all loaded catalysts, keyed by their ID.
     */
    public Map<String, CatalystData> getCatalysts() {
        return Collections.unmodifiableMap(catalysts);
    }
}
