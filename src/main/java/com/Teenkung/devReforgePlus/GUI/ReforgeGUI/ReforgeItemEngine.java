package com.Teenkung.devReforgePlus.GUI.ReforgeGUI;

import com.Teenkung.devReforgePlus.Config.CatalystConfig.CatalystAction;
import com.Teenkung.devReforgePlus.Config.CatalystConfig.CatalystActionType;
import com.Teenkung.devReforgePlus.Config.CatalystConfig.CatalystData;
import com.Teenkung.devReforgePlus.Config.ModifierConfig.ModifierData;
import com.Teenkung.devReforgePlus.Config.ModifierConfig.ReforgeStatData;
import com.Teenkung.devReforgePlus.Config.ModifierConfig.ReforgeStatType;
import com.Teenkung.devReforgePlus.Config.TypeConfig.TypeData;
import com.Teenkung.devReforgePlus.DevReforgePlus;
import com.Teenkung.devReforgePlus.Utils.ReforgeUpdater;
import com.Teenkung.devReforgePlus.Utils.StatResolver;
import com.Teenkung.devReforgePlus.Utils.TrackerPayload;
import com.Teenkung.devReforgePlus.Utils.TrackerUtils;
import org.jetbrains.annotations.Nullable;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.stat.data.DoubleData;
import net.Indyuce.mmoitems.stat.data.StringData;
import net.Indyuce.mmoitems.stat.type.DoubleStat;
import net.Indyuce.mmoitems.stat.type.ItemStat;
import net.Indyuce.mmoitems.stat.type.NameData;
import net.Indyuce.mmoitems.stat.type.StatHistory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import static com.Teenkung.devReforgePlus.DevReforgePlus.REFORGE_UUID;

/**
 * <p>Handles modifier selection, stat application / removal, and tracker
 * persistence. All methods are package-private or public; none touch Bukkit
 * inventory or GUI state directly.</p>
 */
public class ReforgeItemEngine {

    // -------------------------------------------------------------------------
    // Modifier selection
    // -------------------------------------------------------------------------

    /**
     * Picks a modifier for the given type using weighted random selection.
     * If a catalyst is provided, its {@code GUARANTEED_GROUP} or {@code GUARANTEED_MODIFIER}
     * actions are applied as filters on the candidate pool before selection.
     * Falls back to the full pool if the filtered set is empty.
     *
     * @param catalyst optional catalyst being used (may be {@code null})
     * @return the selected {@link ModifierData}, or {@code null} if no valid candidates exist
     */
    public static ModifierData pickModifier(String typeId, TypeData typeData, @Nullable CatalystData catalyst) {
        List<Map.Entry<String, Double>> allCandidates = buildCandidates(typeId, typeData);
        if (allCandidates.isEmpty()) return null;

        List<Map.Entry<String, Double>> candidates = applyCatalystFilter(allCandidates, catalyst, typeId);
        // Fall back to full pool if filtering removed everything
        if (candidates.isEmpty()) candidates = allCandidates;

        return pickWeightedRandom(candidates);
    }

    /**
     * Builds all valid (positive-weight, known) modifier candidates for this type.
     */
    private static List<Map.Entry<String, Double>> buildCandidates(String typeId, TypeData typeData) {
        List<Map.Entry<String, Double>> candidates = new ArrayList<>();
        for (Map.Entry<String, Double> entry : typeData.reforgeWeights().entrySet()) {
            String modifierId = entry.getKey();
            double weight = entry.getValue() == null ? 0D : entry.getValue();
            if (weight < 0) {
                DevReforgePlus.getInstance().getLogger().warning(
                        "Skipping modifier with negative weight: type='" + typeId + "', modifier='" + modifierId + "', weight=" + weight);
                continue;
            }
            if (weight == 0) continue;
            ModifierData modifier = DevReforgePlus.getInstance().getConfigLoader()
                    .getModifierConfig().getModifier(modifierId);
            if (modifier == null) {
                DevReforgePlus.getInstance().getLogger().warning(
                        "Unknown modifier in type config: type='" + typeId + "', modifier='" + modifierId + "'. Skipping.");
                continue;
            }
            candidates.add(entry);
        }
        return candidates;
    }

    /**
     * Applies GUARANTEED_GROUP and GUARANTEED_MODIFIER catalyst filters to the candidate list.
     * Returns the original list unchanged if no relevant actions are present.
     */
    private static List<Map.Entry<String, Double>> applyCatalystFilter(
            List<Map.Entry<String, Double>> candidates, @Nullable CatalystData catalyst, String typeId) {
        if (catalyst == null) return candidates;

        List<Map.Entry<String, Double>> filtered = new ArrayList<>(candidates);

        CatalystAction groupAction = catalyst.getAction(CatalystActionType.GUARANTEED_GROUP);
        if (groupAction != null && !groupAction.groups().isEmpty()) {
            List<Map.Entry<String, Double>> groupFiltered = filtered.stream()
                    .filter(e -> {
                        ModifierData mod = DevReforgePlus.getInstance().getConfigLoader()
                                .getModifierConfig().getModifier(e.getKey());
                        return mod != null && groupAction.groups().stream()
                                .anyMatch(mod::hasGroup);
                    })
                    .collect(java.util.stream.Collectors.toList());
            if (!groupFiltered.isEmpty()) {
                filtered = groupFiltered;
            } else {
                DevReforgePlus.getInstance().getLogger().warning(
                        "GUARANTEED_GROUP catalyst found no modifiers in groups " + groupAction.groups()
                        + " for type '" + typeId + "'. Falling back to full pool.");
                return java.util.Collections.emptyList();
            }
        }

        CatalystAction modAction = catalyst.getAction(CatalystActionType.GUARANTEED_MODIFIER);
        if (modAction != null && !modAction.modifierIds().isEmpty()) {
            List<Map.Entry<String, Double>> modFiltered = filtered.stream()
                    .filter(e -> modAction.modifierIds().contains(e.getKey()))
                    .collect(java.util.stream.Collectors.toList());
            if (!modFiltered.isEmpty()) {
                filtered = modFiltered;
            } else {
                DevReforgePlus.getInstance().getLogger().warning(
                        "GUARANTEED_MODIFIER catalyst found no matching modifiers " + modAction.modifierIds()
                        + " in the type '" + typeId + "' pool. Falling back.");
                return java.util.Collections.emptyList();
            }
        }

        return filtered;
    }

    /** Performs weighted random selection from the candidate list. */
    private static @Nullable ModifierData pickWeightedRandom(List<Map.Entry<String, Double>> candidates) {
        double totalWeight = candidates.stream().mapToDouble(Map.Entry::getValue).sum();
        if (totalWeight <= 0) return null;

        double roll = new Random().nextDouble() * totalWeight;
        double cumulative = 0D;
        for (Map.Entry<String, Double> entry : candidates) {
            cumulative += entry.getValue();
            if (roll < cumulative) {
                return DevReforgePlus.getInstance().getConfigLoader()
                        .getModifierConfig().getModifier(entry.getKey());
            }
        }
        // Fallback to last candidate (floating-point edge case)
        return DevReforgePlus.getInstance().getConfigLoader()
                .getModifierConfig().getModifier(candidates.get(candidates.size() - 1).getKey());
    }

    // -------------------------------------------------------------------------
    // Stat application / removal
    // -------------------------------------------------------------------------

    /**
     * Removes all stat bonuses of the given modifier from the live item,
     * and also clears the display name prefix.
     * Uses safe removal: if a stat becomes 0.0 and has no other sources, removes it entirely.
     */
    public static void clearModifierEffects(LiveMMOItem liveItem, ModifierData modifier, String typeId) {
        for (Map.Entry<String, ReforgeStatData> statEntry : modifier.stats().entrySet()) {
            ItemStat<?, ?> stat = StatResolver.resolve(statEntry.getKey());
            if (!(stat instanceof DoubleStat doubleStat)) {
                warnUnknownStat(typeId, modifier.id(), statEntry.getKey());
                continue;
            }

            StatHistory history = liveItem.computeStatHistory(doubleStat);
            history.removeModifierBonus(REFORGE_UUID);

            // Recalculate and check if the stat should be removed
            var recalculated = history.recalculate(liveItem.getUpgradeLevel());
            liveItem.setData(doubleStat, recalculated);

            // Safe removal: only remove stat if it's now 0.0 and has no other sources
            if (recalculated instanceof DoubleData doubleData && doubleData.getValue() == 0.0) {
                // Check if there are any other modifiers, gemstones, or external data
                boolean hasOtherSources = !history.getAllModifiers().isEmpty()
                    || !history.getAllGemstones().isEmpty()
                    || !history.getExternalData().isEmpty();

                if (!hasOtherSources) {
                    liveItem.removeData(doubleStat);
                }
            }
        }
        clearNamePrefix(liveItem);
    }

    /**
     * Applies all stat bonuses of the given modifier to the live item,
     * converting SCALE values to their flat equivalent using the item's base stat.
     * Also injects the modifier's display name as a prefix in the item display name.
     *
     * @return List of stat IDs that were successfully applied
     */
    public static List<String> applyModifierEffects(LiveMMOItem liveItem, ModifierData modifier, String typeId) {
        List<String> appliedStats = new ArrayList<>();

        for (Map.Entry<String, ReforgeStatData> statEntry : modifier.stats().entrySet()) {
            String statId = statEntry.getKey();
            ReforgeStatData statData = statEntry.getValue();

            ItemStat<?, ?> stat = StatResolver.resolve(statId);
            if (!(stat instanceof DoubleStat doubleStat)) {
                warnUnknownStat(typeId, modifier.id(), statId);
                continue;
            }

            // Initialize stat with 0.0 if it doesn't exist on the item
            if (!liveItem.hasData(doubleStat)) {
                liveItem.setData(doubleStat, new DoubleData(0.0));
            }

            StatHistory history = liveItem.computeStatHistory(doubleStat);
            double valueToApply = statData.value();

            if (statData.type() == ReforgeStatType.SCALE) {
                if (!(history.getOriginalData() instanceof DoubleData originalData)) {
                    warnUnknownStat(typeId, modifier.id(), statId);
                    continue;
                }
                double baseValue = originalData.getValue();
                if (baseValue == 0.0) {
                    if (statData.fallback() != null) {
                        // Base is absent/zero — apply the configured flat fallback instead
                        valueToApply = statData.fallback();
                    } else {
                        // No base, no fallback — skip (original behaviour)
                        warnUnknownStat(typeId, modifier.id(), statId);
                        continue;
                    }
                } else {
                    valueToApply = baseValue * (statData.value() - 1);
                }
            }

            history.registerModifierBonus(REFORGE_UUID, new DoubleData(valueToApply));
            liveItem.setData(doubleStat, history.recalculate(liveItem.getUpgradeLevel()));
            appliedStats.add(statId);
        }
        applyNamePrefix(liveItem, modifier.display());
        return appliedStats;
    }

    // -------------------------------------------------------------------------
    // Tracker persistence
    // -------------------------------------------------------------------------

    /**
     * Reads the existing tracker from NBT, increments the attempt counter by 1,
     * and writes the new tracker back. Use this for normal (non-catalyst) reforges.
     *
     * @param appliedStats List of stat IDs that were actually applied to the item
     */
    public static void updateTrackerModifier(LiveMMOItem liveItem, String modifierId, List<String> appliedStats) {
        int attempts = 1;
        try {
            String rawTracker = liveItem.getNBT().getString("MMOITEMS_REFORGE_TRACKER");
            if (rawTracker != null && !rawTracker.isEmpty()) {
                TrackerPayload old = TrackerUtils.decode(rawTracker);
                attempts = old.attempt() + 1;
            }
        } catch (Exception ignored) {
            // decode failure → restart count at 1
        }
        updateTrackerModifier(liveItem, modifierId, appliedStats, attempts);
    }

    /**
     * Writes the tracker with a specific {@code targetAttempt} value.
     * Used by catalyst actions that override the attempt count (RESET_COUNT, REDUCE_COUNT).
     *
     * @param appliedStats  List of stat IDs actually applied to the item
     * @param targetAttempt The exact attempt number to store (1 = first reforge)
     */
    public static void updateTrackerModifier(LiveMMOItem liveItem, String modifierId,
                                             List<String> appliedStats, int targetAttempt) {
        ModifierData modifier = DevReforgePlus.getInstance().getConfigLoader()
                .getModifierConfig().getModifier(modifierId);
        String hash = modifier != null ? ReforgeUpdater.computeConfigHash(modifier) : "";

        liveItem.setData(
                DevReforgePlus.getInstance().getTrackerStat(),
                new StringData(TrackerUtils.encode(modifierId, targetAttempt, appliedStats, hash))
        );
    }

    /**
     * Clears the reforge tracker from the live item (sets it to an empty string),
     * effectively marking the item as never-reforged. Used by the REMOVE_MODIFIER catalyst action.
     */
    public static void clearTrackerFromItem(LiveMMOItem liveItem) {
        liveItem.setData(
                DevReforgePlus.getInstance().getTrackerStat(),
                new StringData("")
        );
    }

    /**
     * Returns the {@link ModifierData} currently recorded on the item, or
     * {@code null} if the item has never been reforged or the payload is corrupt.
     */
    public static ModifierData readOldModifier(LiveMMOItem liveItem) {
        String rawTracker = liveItem.getNBT().getString("MMOITEMS_REFORGE_TRACKER");
        if (rawTracker == null || rawTracker.isEmpty()) return null;

        try {
            TrackerPayload payload = TrackerUtils.decode(rawTracker);
            return DevReforgePlus.getInstance().getConfigLoader()
                    .getModifierConfig().getModifier(payload.modifier());
        } catch (Exception ex) {
            DevReforgePlus.getInstance().getLogger().warning(
                    "Failed to decode tracker payload: " + ex.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Display name prefix helpers
    // -------------------------------------------------------------------------

    /**
     * Applies a MiniMessage-formatted display name prefix to the item.
     * Uses the NAME stat's StatHistory so it integrates with MMOItems' merge pipeline.
     * Safe to call repeatedly — always replaces the previous reforge prefix via REFORGE_UUID.
     */
    public static void applyNamePrefix(LiveMMOItem liveItem, String miniMessagePrefix) {
        StatHistory nameHist = liveItem.computeStatHistory(ItemStats.NAME);
        NameData prefixData = new NameData(null);
        String toApply = DevReforgePlus.getInstance().getConfig().getString("ReforgeDisplay.Prefix", "<reforge>").replace("<reforge>", miniMessagePrefix);
        prefixData.addPrefix(toApply);
        nameHist.removeModifierBonus(REFORGE_UUID);
        nameHist.registerModifierBonus(REFORGE_UUID, prefixData);
        liveItem.setData(ItemStats.NAME, nameHist.recalculate(liveItem.getUpgradeLevel()));
    }

    /**
     * Removes any reforge display name prefix from the item,
     * restoring the original item name.
     */
    public static void clearNamePrefix(LiveMMOItem liveItem) {
        StatHistory nameHist = liveItem.computeStatHistory(ItemStats.NAME);
        nameHist.removeModifierBonus(REFORGE_UUID);
        liveItem.setData(ItemStats.NAME, nameHist.recalculate(liveItem.getUpgradeLevel()));
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static void warnUnknownStat(String typeId, String modifierId, String statId) {
        DevReforgePlus.getInstance().getLogger().warning(
                "Skipping unknown/unsupported reforge stat: type='" + typeId
                        + "', modifier='" + modifierId + "', stat='" + statId + "'.");
    }

    // -------------------------------------------------------------------------
    // Post-rebuild restoration
    // -------------------------------------------------------------------------

    /**
     * Restores data that {@code LiveMMOItem.newBuilder().build()} does not preserve:
     * <ul>
     *   <li>PDC keys added by external plugins (EcoEnchants, etc.)</li>
     *   <li>Enchantments not tracked in {@code MMOITEMS_ENCHANTS} (datapack / 3rd-party
     *       custom enchants)</li>
     * </ul>
     * Only missing enchantments are added — enchantments already present in the rebuilt
     * item are left untouched so MMOItems-managed values are never overridden.
     */
    public static void restoreUntracked(ItemMeta originalMeta, ItemStack result) {
        if (result == null || originalMeta == null) return;
        ItemMeta resultMeta = result.getItemMeta();
        if (resultMeta == null) return;

        originalMeta.getPersistentDataContainer()
                .copyTo(resultMeta.getPersistentDataContainer(), false);

        var rebuiltEnchants = resultMeta.getEnchants();
        for (var entry : originalMeta.getEnchants().entrySet()) {
            if (!rebuiltEnchants.containsKey(entry.getKey())) {
                resultMeta.addEnchant(entry.getKey(), entry.getValue(), true);
            }
        }

        result.setItemMeta(resultMeta);
    }
}
