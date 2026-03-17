package com.Teenkung.devReforgePlus.Utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.TextDecoration.State;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fluent item-building utility.
 *
 * <p>Raw display-name and lore strings are kept alongside their parsed
 * {@link Component} counterparts so that {@link #build(Map)} can substitute
 * placeholders at the <em>string level</em> before parsing — guaranteeing that
 * tokens like {@code <modifier_display>} are always replaced, regardless of how
 * Adventure internally splits text nodes.</p>
 */
public class ItemBuilder {

    private Material material;
    private int amount = 1;
    private Component displayName;
    private List<Component> lore = new ArrayList<>();
    private Integer customModelData;
    private ConfigurationSection config;

    // Raw strings from config — used by build(Map) for placeholder substitution
    private String rawDisplayName;
    private List<String> rawLore = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public ItemBuilder() {}

    public ItemBuilder(ItemStack itemStack) {
        this.material = itemStack.getType();
        this.amount = itemStack.getAmount();
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) return;
        this.displayName = meta.displayName();
        this.lore = meta.lore() != null ? meta.lore() : new ArrayList<>();
        this.customModelData = readCustomModelData(meta);
    }

    public ItemBuilder(ConfigurationSection section) {
        this.config = section;
        this.material = parseMaterial(getString(section, "Material", "material"));
        this.amount = Math.max(1, section.getInt(section.contains("Amount") ? "Amount" : "amount", 1));
        this.rawDisplayName = getString(section, "DisplayName", "display name", "display-name", "displayName");
        this.displayName = parseComponent(rawDisplayName);
        this.rawLore = getStringList(section, "Lore", "lore");
        this.lore = rawLore.stream().map(ItemBuilder::parseComponent).collect(java.util.stream.Collectors.toList());
        this.customModelData = getInteger(section, "ModelData", "custom model data", "custom-model-data", "customModelData");
    }

    // -------------------------------------------------------------------------
    // Fluent setters
    // -------------------------------------------------------------------------

    public ItemBuilder setMaterial(Material m)          { this.material = m; return this; }
    public ItemBuilder setAmount(int a)                 { this.amount = a; return this; }
    public ItemBuilder setDisplayName(Component n)      { this.displayName = n; return this; }
    public ItemBuilder setLore(List<Component> l)       { this.lore = l; return this; }
    public ItemBuilder setCustomModelData(Integer cmd)  { this.customModelData = cmd; return this; }
    public ItemBuilder setConfig(ConfigurationSection c){ this.config = c; return this; }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public Material getMaterial()         { return material; }
    public int getAmount()                { return amount; }
    public Component getDisplayName()     { return displayName; }
    public List<Component> getLore()      { return lore; }
    public Integer getCustomModelData()   { return customModelData; }
    public ConfigurationSection getConfig(){ return config; }

    // -------------------------------------------------------------------------
    // Build methods
    // -------------------------------------------------------------------------

    public ItemStack build() {
        ItemStack itemStack = new ItemStack(material == null ? Material.AIR : material, Math.max(1, amount));
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.displayName(displayName.decorationIfAbsent(TextDecoration.ITALIC, State.FALSE));
            List<Component> newLore = new ArrayList<>();
            for (Component component : lore) {
                component.decorationIfAbsent(TextDecoration.ITALIC, State.FALSE);
                newLore.add(component);
            }
            meta.lore(lore == null || lore.isEmpty() ? null : newLore);
            applyCustomModelData(meta, customModelData);
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    public ItemStack build(int amount) {
        ItemStack itemStack = build();
        itemStack.setAmount(amount);
        return itemStack;
    }

    /**
     * Builds the item with string-level placeholder substitution.
     * Substitutes tokens of the form {@code <key>} in the raw config strings
     * <em>before</em> parsing with MiniMessage, so formatting tags embedded in
     * placeholder values (e.g. {@code <dark_green>Sturdy}) are rendered correctly.
     */
    public ItemStack build(Map<String, String> placeholders) {
        return build(placeholders, Map.of());
    }

    /**
     * Like {@link #build(Map)} but additionally supports <em>multi-line</em> expansion.
     * Any lore line whose entire text (after scalar substitution) equals {@code <key>}
     * for a key in {@code multiLine} is replaced by the corresponding list of lines.
     * Useful for placeholders like {@code <stats>} that expand to N lines.
     */
    @SuppressWarnings("D")
    public ItemStack build(Map<String, String> scalars, Map<String, List<String>> multiLine) {
        ItemStack itemStack = new ItemStack(material == null ? Material.AIR : material, Math.max(1, amount));
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            String nameStr = rawDisplayName != null ? rawDisplayName : "";
            for (Map.Entry<String, String> e : scalars.entrySet()) {
                nameStr = nameStr.replace("<" + e.getKey() + ">", e.getValue());
            }
            meta.displayName(parseComponent(nameStr).decorationIfAbsent(TextDecoration.ITALIC, State.FALSE));

            List<Component> resolvedLore = new ArrayList<>();
            for (String loreLine : rawLore) {
                // Apply scalar substitutions
                String resolved = loreLine;
                for (Map.Entry<String, String> e : scalars.entrySet()) {
                    resolved = resolved.replace("<" + e.getKey() + ">", e.getValue());
                }
                // Check for multi-line expansion (exact match after stripping whitespace)
                boolean expanded = false;
                for (Map.Entry<String, List<String>> e : multiLine.entrySet()) {
                    if (resolved.strip().equals("<" + e.getKey() + ">")) {
                        for (String expandedLine : e.getValue()) {
                            resolvedLore.add(parseComponent(expandedLine));
                        }
                        expanded = true;
                        break;
                    }
                }
                if (!expanded) {
                    resolvedLore.add(parseComponent(resolved));
                }
            } // ADDED MISSING BRACKET HERE
            meta.displayName(displayName.decorationIfAbsent(TextDecoration.ITALIC, State.FALSE));
            
            List<Component> finalResolvedLore = new ArrayList<>();
            for (Component component : resolvedLore) {
                finalResolvedLore.add(component.decorationIfAbsent(TextDecoration.ITALIC, State.FALSE));
            }
            meta.lore(finalResolvedLore.isEmpty() ? null : finalResolvedLore);
            
            applyCustomModelData(meta, customModelData);
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    // -------------------------------------------------------------------------
    // Parse helpers
    // -------------------------------------------------------------------------

    private static Component parseComponent(String value) {
        return value == null ? Component.empty() : MessageUtils.parse(value);
    }

    private static Material parseMaterial(String value) {
        if (value == null || value.isBlank()) return Material.AIR;
        Material m = Material.matchMaterial(value.trim());
        return m == null ? Material.AIR : m;
    }

    @SuppressWarnings("UnstableApiUsage")
    private static Integer readCustomModelData(ItemMeta meta) {
        CustomModelDataComponent comp = meta.getCustomModelDataComponent();
        if (comp.getFloats().isEmpty()) return null;
        return Math.round(comp.getFloats().getFirst());
    }

    @SuppressWarnings("UnstableApiUsage")
    private static void applyCustomModelData(ItemMeta meta, Integer customModelData) {
        CustomModelDataComponent comp = meta.getCustomModelDataComponent();
        comp.setFloats(customModelData == null ? List.of() : List.of(customModelData.floatValue()));
        meta.setCustomModelDataComponent(comp);
    }

    private static String getString(ConfigurationSection section, String... paths) {
        for (String path : paths) {
            if (section.contains(path)) return section.getString(path);
        }
        return null;
    }

    private static List<String> getStringList(ConfigurationSection section, String... paths) {
        for (String path : paths) {
            if (section.contains(path)) return section.getStringList(path);
        }
        return List.of();
    }

    private static Integer getInteger(ConfigurationSection section, String... paths) {
        for (String path : paths) {
            if (section.contains(path)) return section.getInt(path);
        }
        return null;
    }
}
