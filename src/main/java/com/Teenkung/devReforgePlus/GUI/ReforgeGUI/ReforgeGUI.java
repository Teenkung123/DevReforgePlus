package com.Teenkung.devReforgePlus.GUI.ReforgeGUI;

import com.Teenkung.devReforgePlus.Config.ConfigLoader;
import com.Teenkung.devReforgePlus.Config.ModifierConfig.ModifierData;
import com.Teenkung.devReforgePlus.Config.ModifierConfig.ReforgeStatData;
import com.Teenkung.devReforgePlus.Config.TypeConfig.TypeData;
import com.Teenkung.devReforgePlus.DevReforgePlus;
import com.Teenkung.devReforgePlus.Utils.GUIBuilder;
import com.Teenkung.devReforgePlus.Utils.ItemBuilder;
import com.Teenkung.devReforgePlus.Utils.MessageUtils;
import com.Teenkung.devReforgePlus.Utils.TrackerPayload;
import com.Teenkung.devReforgePlus.Utils.TrackerUtils;
import lombok.Getter;
import lombok.Setter;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.TextDecoration.State;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ReforgeGUI extends GUIBuilder {

    @Getter @Setter private int inputSlot;
    @Getter @Setter private int reforgeButtonSlot;
    @Getter private Map<String, ItemBuilder> reforgeButtonItems = new HashMap<>();

    @Getter @Setter private int modifierListSlot;
    @Getter private Map<String, ItemBuilder> modifierListItems = new HashMap<>();
    private int modifierListPage = 0;
    private int modifierListTotalPages = 1;
    private String lastModifierListTypeId = null;

    @Getter @Setter private int modifierInfoSlot;
    @Getter private Map<String, ItemBuilder> modifierInfoItems = new HashMap<>();

    @Getter private Inventory inventory;
    /** The player who currently has this GUI open. */
    private Player player;

    /** Tracks when each player last pressed the reforge button (epoch ms). */
    private final Map<UUID, Long> cooldownMap = new HashMap<>();


    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public ReforgeGUI() {
        super();
        DevReforgePlus.getInstance().getLogger().info("Loading Reforge GUI...");
        Bukkit.getPluginManager().registerEvents(new ReforgeGUIListener(), DevReforgePlus.getInstance());
    }

    public void loadReforgeMenu() {
        ConfigurationSection config = DevReforgePlus.getInstance().getConfig()
                .getConfigurationSection("GUI.Reforge");
        if (config == null) {
            DevReforgePlus.getInstance().getLogger().severe("Reforge GUI config is missing.");
            return;
        }
        this.loadFrom(config);

        this.inputSlot        = getOptions().getInt("InputSlot", 22);
        this.reforgeButtonSlot = getOptions().getInt("ReforgeButton.Slot", 31);
        this.modifierListSlot = getOptions().getInt("ModifierList.Slot", 20);
        this.modifierInfoSlot = getOptions().getInt("ModifierInfo.Slot", 24);

        loadItemStates("ReforgeButton", reforgeButtonItems);
        loadItemStates("ModifierList",  modifierListItems);
        loadItemStates("ModifierInfo",  modifierInfoItems);
    }

    private void loadItemStates(String optionPath, Map<String, ItemBuilder> targetMap) {
        ConfigurationSection section = getOptions().getConfigurationSection(optionPath);
        if (section == null) return;
        for (String key : section.getKeys(false)) {
        if (key.equals("Slot") || key.equals("ListLore") || key.equals("MaxEntries")
                || key.equals("EntriesPerPage") || key.equals("MoreMessage")) continue;
            ConfigurationSection stateSection = section.getConfigurationSection(key);
            if (stateSection != null) targetMap.put(key, new ItemBuilder(stateSection));
        }
    }

    public void open(Player player) {
        this.player = player;
        this.inventory = this.build();
        ReforgeGUIManager.addGUI(this);

        clearInputSlot();
        updateDisplay();

        player.openInventory(this.inventory);
    }

    private void clearInputSlot() {
        this.inventory.setItem(inputSlot, null);
    }

    // -------------------------------------------------------------------------
    // Display updates
    // -------------------------------------------------------------------------

    public void updateDisplay() {
        updateReforgeButton();
        updateModifierList();
        updateModifierInfo();
    }

    /** Advances the modifier list to the next page, wrapping back to 0 after the last page. */
    public void nextModifierListPage() {
        modifierListPage = (modifierListPage + 1) % modifierListTotalPages;
        updateModifierList();
    }

    private void updateReforgeButton() {
        if (getInputItem() == null) {
            ItemBuilder b = reforgeButtonItems.get("ItemEmpty");
            if (b != null) this.inventory.setItem(reforgeButtonSlot, b.build());
            return;
        }
        if (!isItemValid()) {
            ItemBuilder b = reforgeButtonItems.get("ItemInvalid");
            if (b != null) this.inventory.setItem(reforgeButtonSlot, b.build());
            return;
        }
        // Item is valid — compute next reforge cost
        int currentAttempt = getCurrentAttempt();
        ConfigLoader cfg = DevReforgePlus.getInstance().getConfigLoader();
        double nextCost = cfg.getCostAtAttempt(currentAttempt + 1);
        String costStr = cfg.isRoundingEnabled()
                ? String.valueOf((long) nextCost)
                : String.format("%.2f", nextCost);
        Map<String, String> placeholders = Map.of("cost", costStr);

        Economy econ = DevReforgePlus.getEcon();
        if (econ != null && player != null && !econ.has(player, nextCost)) {
            ItemBuilder b = reforgeButtonItems.get("ItemNotEnoughMoney");
            if (b != null) this.inventory.setItem(reforgeButtonSlot, b.build(placeholders));
            return;
        }
        ItemBuilder b = reforgeButtonItems.get("ItemValid");
        if (b != null) this.inventory.setItem(reforgeButtonSlot, b.build(placeholders));
    }

    private void updateModifierList() {
        String state = resolveState();
        // The modifier list shows the same possible modifiers regardless of whether
        // the item has been reforged before — remap that state to ItemValid here.
        if (state.equals("ItemNotReforgeBefore")) state = "ItemValid";

        if (!state.equals("ItemValid")) {
            ItemBuilder item = modifierListItems.get(state);
            if (item != null) this.inventory.setItem(modifierListSlot, item.build());
            return;
        }

        ItemBuilder templateBuilder = modifierListItems.get("ItemValid");
        if (templateBuilder == null) return;

        ConfigurationSection optSection = getOptions().getConfigurationSection("ModifierList.ItemValid");
        String listLoreTemplate = optSection != null
                ? optSection.getString("ListLore", "&8- &f<modifier_display> &7(<weight> | <percent>%)")
                : "&8- &f<modifier_display> &7(<weight> | <percent>%)";
        int entriesPerPage = optSection != null
                ? optSection.getInt("EntriesPerPage", optSection.getInt("MaxEntries", 10))
                : 10;
        String moreMessage = optSection != null
                ? optSection.getString("MoreMessage", "&8  And <count> more...")
                : "&8  And <count> more...";

        String typeId = Type.get(getInputItem()).getId();
        TypeData typeData = DevReforgePlus.getInstance().getConfigLoader().getTypeConfig().getTypeData(typeId);

        // Reset page when the item type changes
        if (!typeId.equals(lastModifierListTypeId)) {
            modifierListPage = 0;
            lastModifierListTypeId = typeId;
        }

        long totalEntries = typeData.reforgeWeights().entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() > 0).count();
        modifierListTotalPages = (int) Math.max(1, Math.ceil((double) totalEntries / entriesPerPage));
        modifierListPage = Math.max(0, Math.min(modifierListPage, modifierListTotalPages - 1));

        List<Component> entryComponents = buildModifierEntryComponents(typeData, listLoreTemplate, entriesPerPage, modifierListPage, moreMessage);
        List<Component> finalLore = expandEntriesPlaceholder(templateBuilder.getLore(), entryComponents);

        ItemBuilder built = new ItemBuilder()
                .setMaterial(templateBuilder.getMaterial())
                .setAmount(1)
                .setDisplayName(templateBuilder.getDisplayName())
                .setLore(finalLore)
                .setCustomModelData(templateBuilder.getCustomModelData());
        this.inventory.setItem(modifierListSlot, built.build());
    }

    /**
     * Builds one page of modifier entry components for the modifier list lore.
     * Each entry is formatted with the provided template and the "And X more..." line
     * is appended when there are entries on subsequent pages.
     */
    private List<Component> buildModifierEntryComponents(TypeData typeData, String template,
                                                          int entriesPerPage, int page, String moreMessage) {
        double totalWeight = typeData.getTotalWeight();

        List<Map.Entry<String, Double>> sortedEntries = typeData.reforgeWeights().entrySet()
                .stream()
                .filter(e -> e.getValue() != null && e.getValue() > 0)
                .sorted((a, b) -> Double.compare(
                        typeData.getDisplayWeight(a.getKey()),
                        typeData.getDisplayWeight(b.getKey())))
                .toList();

        int totalEntries = sortedEntries.size();
        int startIdx = page * entriesPerPage;
        int endIdx = Math.min(startIdx + entriesPerPage, totalEntries);
        int remaining = totalEntries - endIdx;

        List<Component> out = new ArrayList<>();
        for (int i = startIdx; i < endIdx; i++) {
            Map.Entry<String, Double> entry = sortedEntries.get(i);
            double weight = entry.getValue();
            ModifierData modData = DevReforgePlus.getInstance().getConfigLoader()
                    .getModifierConfig().getModifier(entry.getKey());
            String display = modData != null ? modData.display() : entry.getKey();
            double displayWeight = typeData.getDisplayWeight(entry.getKey());
            double percent = totalWeight > 0 ? (weight / totalWeight) * 100.0 : 0;

            String line = template
                    .replace("<modifier_display>", display)
                    .replace("<modifier_id>", entry.getKey())
                    .replace("<weight>", displayWeight % 1 == 0
                            ? String.valueOf((long) displayWeight)
                            : String.valueOf(displayWeight))
                    .replace("<percent>", String.format("%.1f", percent));
            out.add(MessageUtils.parse(line).decorationIfAbsent(TextDecoration.ITALIC, State.FALSE));
        }

        if (remaining > 0) {
            String moreLine = moreMessage.replace("<count>", String.valueOf(remaining));
            out.add(MessageUtils.parse(moreLine).decorationIfAbsent(TextDecoration.ITALIC, State.FALSE));
        }

        return out;
    }

    /** Replaces any lore line containing {@code <entries>} with the provided entry components. */
    private List<Component> expandEntriesPlaceholder(List<Component> baseLore, List<Component> entries) {
        List<Component> result = new ArrayList<>();
        for (Component c : baseLore) {
            if (PlainTextComponentSerializer.plainText().serialize(c).contains("<entries>")) {
                result.addAll(entries);
            } else {
                result.add(c);
            }
        }
        return result;
    }
    private void updateModifierInfo() {
        String state;
        if (getInputItem() == null) {
            state = "ItemEmpty";
        } else if (!isItemValid()) {
            state = "ItemInvalid";
        } else if (!hasReforgeBefore()) {
            state = "ItemNotReforgeBefore";
        } else {
            // hasReforgeBefore() guarantees the tracker NBT is present and non-empty here
            LiveMMOItem item = new LiveMMOItem(getInputItem());
            String rawTracker = item.getNBT().getString("MMOITEMS_REFORGE_TRACKER");
            TrackerPayload decoded = TrackerUtils.decode(rawTracker);
            ModifierData modData = DevReforgePlus.getInstance().getConfigLoader()
                    .getModifierConfig().getModifier(decoded.modifier());

            Map<String, String> scalars = new HashMap<>();
            scalars.put("modifier_id",     decoded.modifier());
            scalars.put("modifier_display", modData != null ? modData.display() : decoded.modifier());
            scalars.put("attempts",         String.valueOf(decoded.attempt()));

            Map<String, List<String>> multiLine = new HashMap<>();
            multiLine.put("stats", buildStatDisplayLines(modData));

            ItemBuilder builder = modifierInfoItems.get("ItemValid");
            if (builder != null) this.inventory.setItem(modifierInfoSlot, builder.build(scalars, multiLine));
            return;
        }

        ItemBuilder builder = modifierInfoItems.get(state);
        if (builder != null) this.inventory.setItem(modifierInfoSlot, builder.build());
    }

    /**
     * Builds the stat display lines for a modifier using the same format as item lore
     * ({@code ReforgeDisplay.LoreLine} + {@code ReforgeDisplay.Stats.Format}).
     */
    private List<String> buildStatDisplayLines(ModifierData modData) {
        if (modData == null) return List.of();
        ConfigLoader cfg = DevReforgePlus.getInstance().getConfigLoader();
        List<String> lines = new ArrayList<>();
        lines.add(cfg.getDisplayLoreLine().replace("<reforge_display>", modData.display()));
        String statFmt = cfg.getDisplayStatFormat();
        for (Map.Entry<String, ReforgeStatData> e : modData.stats().entrySet()) {
            String localised = cfg.getStatLangConfig().getFormattedStat(e.getKey(), e.getValue());
            lines.add(statFmt.replace("<stat_format>", localised));
        }
        return lines;
    }
 

    // -------------------------------------------------------------------------
    // Reforge action
    // -------------------------------------------------------------------------

    /**
     * Attempts to reforge the item currently in the input slot of the player's reforge GUI.
     * If successful, applies a new modifier to the item and updates its state accordingly.
     *
     * The method enforces a cooldown to prevent rapid consecutive reforging and validates
     * the input item before proceeding. If no valid item or modifier is found, the reforge action
     * is canceled.
     *
     * @param player The player performing the reforge action.
     * @return {@code true} if the item was successfully reforged; {@code false} otherwise.
     */
    public boolean reforgeItem(Player player) {
        // Cooldown guard
        long delayMs = (long) (DevReforgePlus.getInstance().getConfig()
                .getDouble("ReforgeDelay", 0.1) * 1000);
        long now = System.currentTimeMillis();
        Long lastUse = cooldownMap.get(player.getUniqueId());
        if (lastUse != null && now - lastUse < delayMs) return false;
        cooldownMap.put(player.getUniqueId(), now);

        if (!isItemValid()) return false;
        ItemStack inputItem = getInputItem();
        if (inputItem == null) return false;

        String typeId = Type.get(inputItem).getId();
        TypeData typeData = DevReforgePlus.getInstance().getConfigLoader().getTypeConfig().getTypeData(typeId);
        if (typeData == null) return false;

        // Economy cost check
        ConfigLoader cfg = DevReforgePlus.getInstance().getConfigLoader();
        Economy econ = DevReforgePlus.getEcon();
        int currentAttempt = getCurrentAttempt();
        double cost = cfg.getCostAtAttempt(currentAttempt + 1);
        if (econ != null && !econ.has(player, cost)) return false;

        ModifierData nextModifier = ReforgeItemEngine.pickModifier(typeId, typeData);
        if (nextModifier == null) return false;

        // Deduct cost
        if (econ != null) econ.withdrawPlayer(player, cost);

        // Snapshot original PDC so external plugins (e.g. EcoEnchants) survive the rebuild
        ItemMeta originalMeta = inputItem.getItemMeta();

        LiveMMOItem liveItem = new LiveMMOItem(inputItem);
        ModifierData oldModifier = ReforgeItemEngine.readOldModifier(liveItem);

        if (oldModifier != null) ReforgeItemEngine.clearModifierEffects(liveItem, oldModifier, typeId);
        List<String> appliedStats = ReforgeItemEngine.applyModifierEffects(liveItem, nextModifier, typeId);
        ReforgeItemEngine.updateTrackerModifier(liveItem, nextModifier.id(), appliedStats);

        ItemStack result = liveItem.newBuilder().build();

        // Restore external data that the MMOItems rebuild discards
        ReforgeItemEngine.restoreUntracked(originalMeta, result);

        this.inventory.setItem(inputSlot, result);
        updateDisplay();
        return true;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    public ItemStack getInputItem() {
        return this.inventory.getItem(inputSlot);
    }

    private boolean isItemValid() {
        ItemStack item = getInputItem();
        if (item == null) return false;
        if (Type.get(item) == null) return false;
        return DevReforgePlus.getInstance().getConfigLoader()
                .getTypeConfig().hasTypeData(Type.get(item).getId());
    }

    private boolean hasReforgeBefore() {
        LiveMMOItem item = new LiveMMOItem(getInputItem());
        String rawTracker = item.getNBT().getString("MMOITEMS_REFORGE_TRACKER");
        return rawTracker != null && !rawTracker.isEmpty();
    }

    /** Returns the current reforge attempt count from the item's tracker (0 if never reforged). */
    private int getCurrentAttempt() {
        ItemStack item = getInputItem();
        if (item == null) return 0;
        try {
            LiveMMOItem live = new LiveMMOItem(item);
            String raw = live.getNBT().getString("MMOITEMS_REFORGE_TRACKER");
            if (raw == null || raw.isEmpty()) return 0;
            return TrackerUtils.decode(raw).attempt();
        } catch (Exception ignored) {
            return 0;
        }
    }

    /** Resolves the common ItemEmpty / ItemInvalid / ItemNotReforgeBefore / ItemValid state string. */
    private String resolveState() {
        if (getInputItem() == null) return "ItemEmpty";
        if (!isItemValid())         return "ItemInvalid";
        if (!hasReforgeBefore())    return "ItemNotReforgeBefore";
        return "ItemValid";
    }
}
