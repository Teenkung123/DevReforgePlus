package com.Teenkung.devReforgePlus.GUI.ReforgeGUI;

import com.Teenkung.devReforgePlus.Config.CatalystConfig.CatalystAction;
import com.Teenkung.devReforgePlus.Config.CatalystConfig.CatalystActionType;
import com.Teenkung.devReforgePlus.Config.CatalystConfig.CatalystData;
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
import org.jetbrains.annotations.Nullable;
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

    /** Slot where the player physically places their catalyst item (stored as a real item, never overwritten by GUI code). */
    @Getter @Setter private int catalystInputSlot;
    /** Display-only slot that shows current catalyst status (like modifierInfoSlot). Never stores real items. */
    @Getter @Setter private int catalystInfoSlot;
    @Getter private Map<String, ItemBuilder> catalystInfoItems = new HashMap<>();

    @Getter private Inventory inventory;
    /** The player who currently has this GUI open. */
    private Player player;

    /** Tracks when each player last pressed the reforge button (epoch ms). Shared across all sessions. */
    private static final Map<UUID, Long> cooldownMap = new HashMap<>();


    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public ReforgeGUI() {
        super();
        DevReforgePlus.getInstance().getLogger().info("Loading Reforge GUI...");
    }

    /** Creates a per-session copy from the template, sharing config references. */
    ReforgeGUI(ReforgeGUI template) {
        super();
        this.setOptions(template.getOptions());
        this.inputSlot = template.inputSlot;
        this.reforgeButtonSlot = template.reforgeButtonSlot;
        this.modifierListSlot = template.modifierListSlot;
        this.modifierInfoSlot = template.modifierInfoSlot;
        this.catalystInputSlot = template.catalystInputSlot;
        this.catalystInfoSlot = template.catalystInfoSlot;
        this.reforgeButtonItems = template.reforgeButtonItems;
        this.modifierListItems = template.modifierListItems;
        this.modifierInfoItems = template.modifierInfoItems;
        this.catalystInfoItems = template.catalystInfoItems;
    }

    public void loadReforgeMenu() {
        ConfigurationSection config = DevReforgePlus.getInstance().getConfig()
                .getConfigurationSection("GUI.Reforge");
        if (config == null) {
            DevReforgePlus.getInstance().getLogger().severe("Reforge GUI config is missing.");
            return;
        }
        this.loadFrom(config);

        this.inputSlot         = getOptions().getInt("InputSlot", 22);
        this.reforgeButtonSlot = getOptions().getInt("ReforgeButton.Slot", 31);
        this.modifierListSlot  = getOptions().getInt("ModifierList.Slot", 20);
        this.modifierInfoSlot  = getOptions().getInt("ModifierInfo.Slot", 24);
        this.catalystInputSlot = getOptions().getInt("CatalystInputSlot", 26);
        this.catalystInfoSlot  = getOptions().getInt("CatalystInfo.Slot", 25);

        loadItemStates("ReforgeButton", reforgeButtonItems);
        loadItemStates("ModifierList",  modifierListItems);
        loadItemStates("ModifierInfo",  modifierInfoItems);
        loadItemStates("CatalystInfo",  catalystInfoItems);
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
        ReforgeGUI session = new ReforgeGUI(this);
        session.player = player;
        session.inventory = this.build();

        ReforgeGUIManager.addGUI(session.inventory, session);

        session.clearInputSlot();
        session.clearCatalystInputSlot();
        session.updateDisplay();

        player.openInventory(session.inventory);
    }

    private void clearInputSlot() {
        this.inventory.setItem(inputSlot, null);
    }

    private void clearCatalystInputSlot() {
        this.inventory.setItem(catalystInputSlot, null);
    }

    // -------------------------------------------------------------------------
    // Display updates
    // -------------------------------------------------------------------------

    public void updateDisplay() {
        updateReforgeButton();
        updateModifierList();
        updateModifierInfo();
        updateCatalystSlot();
    }

    /** Advances the modifier list to the next page, wrapping back to 0 after the last page. */
    public void nextModifierListPage() {
        modifierListPage = (modifierListPage + 1) % modifierListTotalPages;
        updateModifierList();
    }

    /**
     * Updates the catalyst INFO slot (display only). Never touches the catalyst INPUT slot.
     * Reads the item from catalystInputSlot, writes a display item to catalystInfoSlot.
     */
    private void updateCatalystSlot() {
        ItemStack catalystItem = getCatalystItem();
        if (catalystItem == null || catalystItem.getType().isAir()) {
            ItemBuilder b = catalystInfoItems.get("ItemEmpty");
            if (b != null) this.inventory.setItem(catalystInfoSlot, b.build());
            return;
        }
        CatalystData catalyst = getCurrentCatalyst();
        if (catalyst == null) {
            ItemBuilder b = catalystInfoItems.get("ItemInvalid");
            if (b != null) this.inventory.setItem(catalystInfoSlot, b.build());
            return;
        }
        ItemBuilder b = catalystInfoItems.get("ItemValid");
        if (b == null) return;
        Map<String, String> scalars = Map.of("catalyst_display", catalyst.displayName());
        Map<String, List<String>> multi = Map.of("actions", buildCatalystActionLines(catalyst));
        this.inventory.setItem(catalystInfoSlot, b.build(scalars, multi));
    }

    /** Returns the actual item placed in the catalyst INPUT slot, or null if empty. */
    public @Nullable ItemStack getCatalystItem() {
        return this.inventory.getItem(catalystInputSlot);
    }

    public @Nullable CatalystData getCurrentCatalyst() {
        return DevReforgePlus.getInstance().getConfigLoader()
                .getCatalystConfig().getByItem(getCatalystItem());
    }

    /** Builds human-readable lore lines describing what this catalyst does. */
    private List<String> buildCatalystActionLines(CatalystData catalyst) {
        List<String> lines = new ArrayList<>();
        for (CatalystAction action : catalyst.actions()) {
            switch (action.type()) {
                case RESET_COUNT -> lines.add("&7- Reset reforge count to 0");
                case REDUCE_COUNT -> lines.add("&7- Reduce reforge count by &f" + action.amount());
                case REMOVE_MODIFIER -> lines.add("&7- Remove all reforge modifiers");
                case GUARANTEED_GROUP -> lines.add("&7- Guarantee modifier from group(s): &f" + String.join(", ", action.groups()));
                case GUARANTEED_MODIFIER -> lines.add("&7- Guarantee modifier: &f" + String.join(", ", action.modifierIds()));
            }
        }
        return lines;
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
        // Item is valid — compute next reforge cost (base price if catalyst is active)
        int currentAttempt = getCurrentAttempt();
        ConfigLoader cfg = DevReforgePlus.getInstance().getConfigLoader();
        double nextCost = (getCurrentCatalyst() != null)
                ? cfg.getBasePrice()
                : cfg.getCostAtAttempt(currentAttempt + 1);
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
     * Attempts to reforge the item in the input slot, applying any active catalyst.
     *
     * <p>Catalyst behaviour:</p>
     * <ul>
     *   <li>Cost is always {@code Economy.Base} when a catalyst is present.</li>
     *   <li>{@code REMOVE_MODIFIER}: clears the modifier and tracker, no new modifier applied.</li>
     *   <li>{@code RESET_COUNT} / {@code REDUCE_COUNT}: adjusts the attempt counter before storing.</li>
     *   <li>{@code GUARANTEED_GROUP} / {@code GUARANTEED_MODIFIER}: filters the modifier pool.</li>
     * </ul>
     *
     * @param player The player performing the reforge action.
     * @return {@code true} if the reforge (or modifier removal) succeeded; {@code false} otherwise.
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

        // Resolve active catalyst (may be null)
        CatalystData catalyst = getCurrentCatalyst();

        ConfigLoader cfg = DevReforgePlus.getInstance().getConfigLoader();
        Economy econ = DevReforgePlus.getEcon();
        int currentAttempt = getCurrentAttempt();

        // Cost: always base price when using a catalyst, otherwise attempt-scaled
        double cost = (catalyst != null)
                ? cfg.getBasePrice()
                : cfg.getCostAtAttempt(currentAttempt + 1);
        if (econ != null && !econ.has(player, cost)) return false;

        // Snapshot original PDC before any rebuild
        ItemMeta originalMeta = inputItem.getItemMeta();
        LiveMMOItem liveItem = new LiveMMOItem(inputItem);
        ModifierData oldModifier = ReforgeItemEngine.readOldModifier(liveItem);

        // Read item ID for logging
        String itemId = liveItem.getNBT().getString("MMOITEMS_ITEM_ID");
        if (itemId == null) itemId = "unknown";

        // ── REMOVE_MODIFIER catalyst: clear modifier, no new one applied ──────
        if (catalyst != null && catalyst.hasAction(CatalystActionType.REMOVE_MODIFIER)) {
            if (econ != null) econ.withdrawPlayer(player, cost);
            if (oldModifier != null) ReforgeItemEngine.clearModifierEffects(liveItem, oldModifier, typeId);
            else ReforgeItemEngine.clearNamePrefix(liveItem);
            ReforgeItemEngine.clearTrackerFromItem(liveItem);
            ItemStack result = liveItem.newBuilder().build();
            ReforgeItemEngine.restoreUntracked(originalMeta, result);
            this.inventory.setItem(inputSlot, result);
            consumeCatalyst();
            DevReforgePlus.getInstance().getReforgeLogger().logReforge(
                    player.getUniqueId(), player.getName(), typeId, itemId,
                    oldModifier != null ? oldModifier.id() : null,
                    null, currentAttempt, 0, cost,
                    catalyst.id());
            updateDisplay();
            return true;
        }

        // ── Normal reforge (with optional catalyst constraints) ───────────────
        ModifierData nextModifier = ReforgeItemEngine.pickModifier(typeId, typeData, catalyst);
        if (nextModifier == null) return false;

        // Deduct cost
        if (econ != null) econ.withdrawPlayer(player, cost);

        if (oldModifier != null) ReforgeItemEngine.clearModifierEffects(liveItem, oldModifier, typeId);
        List<String> appliedStats = ReforgeItemEngine.applyModifierEffects(liveItem, nextModifier, typeId);

        // Calculate target attempt after catalyst count modifications
        int targetAttempt = currentAttempt + 1;
        if (catalyst != null) {
            if (catalyst.hasAction(CatalystActionType.RESET_COUNT)) {
                targetAttempt = 1;
            } else if (catalyst.hasAction(CatalystActionType.REDUCE_COUNT)) {
                CatalystAction reduceAction = catalyst.getAction(CatalystActionType.REDUCE_COUNT);
                int amount = reduceAction != null ? reduceAction.amount() : 0;
                targetAttempt = Math.max(0, currentAttempt - amount) + 1;
            }
        }

        ReforgeItemEngine.updateTrackerModifier(liveItem, nextModifier.id(), appliedStats, targetAttempt);
        ItemStack result = liveItem.newBuilder().build();
        ReforgeItemEngine.restoreUntracked(originalMeta, result);
        this.inventory.setItem(inputSlot, result);

        if (catalyst != null) consumeCatalyst();

        DevReforgePlus.getInstance().getReforgeLogger().logReforge(
                player.getUniqueId(), player.getName(), typeId, itemId,
                oldModifier != null ? oldModifier.id() : null,
                nextModifier.id(), currentAttempt, targetAttempt, cost,
                catalyst != null ? catalyst.id() : null);

        updateDisplay();
        return true;
    }

    /**
     * Consumes exactly one catalyst item from the catalyst INPUT slot.
     * Clears the slot if this was the last item in the stack.
     */
    private void consumeCatalyst() {
        ItemStack catalystItem = getCatalystItem();
        if (catalystItem == null || catalystItem.getType().isAir()) return;
        if (catalystItem.getAmount() <= 1) {
            this.inventory.setItem(catalystInputSlot, null);
        } else {
            ItemStack remaining = catalystItem.clone();
            remaining.setAmount(remaining.getAmount() - 1);
            this.inventory.setItem(catalystInputSlot, remaining);
        }
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
