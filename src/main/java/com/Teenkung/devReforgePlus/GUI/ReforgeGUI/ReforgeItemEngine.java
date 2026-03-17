package com.Teenkung.devReforgePlus.GUI.ReforgeGUI;

import com.Teenkung.devReforgePlus.Config.ModifierConfig.ModifierData;
import com.Teenkung.devReforgePlus.Config.ModifierConfig.ReforgeStatData;
import com.Teenkung.devReforgePlus.Config.ModifierConfig.ReforgeStatType;
import com.Teenkung.devReforgePlus.Config.TypeConfig.TypeData;
import com.Teenkung.devReforgePlus.DevReforgePlus;
import com.Teenkung.devReforgePlus.Utils.StatResolver;
import com.Teenkung.devReforgePlus.Utils.TrackerPayload;
import com.Teenkung.devReforgePlus.Utils.TrackerUtils;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.stat.data.DoubleData;
import net.Indyuce.mmoitems.stat.data.StringData;
import net.Indyuce.mmoitems.stat.type.DoubleStat;
import net.Indyuce.mmoitems.stat.type.ItemStat;
import net.Indyuce.mmoitems.stat.type.StatHistory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.Teenkung.devReforgePlus.DevReforgePlus.REFORGE_UUID;

/**
 * Pure reforge logic — no GUI or inventory concerns.
 *
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
     *
     * @return the selected {@link ModifierData}, or {@code null} if no valid
     *         candidates exist
     */
    public static ModifierData pickModifier(String typeId, TypeData typeData) {
        List<Map.Entry<String, Double>> candidates = new ArrayList<>();
        double totalWeight = 0D;

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
            totalWeight += weight;
        }

        if (totalWeight <= 0 || candidates.isEmpty()) return null;

        double roll = new Random().nextDouble() * totalWeight;
        double cumulative = 0D;
        for (Map.Entry<String, Double> entry : candidates) {
            cumulative += entry.getValue();
            if (roll < cumulative) {
                return DevReforgePlus.getInstance().getConfigLoader()
                        .getModifierConfig().getModifier(entry.getKey());
            }
        }

        // Fallback to last candidate (handles floating-point edge cases)
        return DevReforgePlus.getInstance().getConfigLoader()
                .getModifierConfig().getModifier(candidates.get(candidates.size() - 1).getKey());
    }

    // -------------------------------------------------------------------------
    // Stat application / removal
    // -------------------------------------------------------------------------

    /**
     * Removes all stat bonuses of the given modifier from the live item.
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
            liveItem.setData(doubleStat, history.recalculate(liveItem.getUpgradeLevel()));
        }
    }

    /**
     * Applies all stat bonuses of the given modifier to the live item,
     * converting SCALE values to their flat equivalent using the item's base stat.
     */
    public static void applyModifierEffects(LiveMMOItem liveItem, ModifierData modifier, String typeId) {
        for (Map.Entry<String, ReforgeStatData> statEntry : modifier.stats().entrySet()) {
            String statId = statEntry.getKey();
            ReforgeStatData statData = statEntry.getValue();

            ItemStat<?, ?> stat = StatResolver.resolve(statId);
            if (!(stat instanceof DoubleStat doubleStat)) {
                warnUnknownStat(typeId, modifier.id(), statId);
                continue;
            }

            StatHistory history = liveItem.computeStatHistory(doubleStat);
            double valueToApply = statData.value();

            if (statData.type() == ReforgeStatType.SCALE) {
                if (!(history.getOriginalData() instanceof DoubleData originalData)) {
                    warnUnknownStat(typeId, modifier.id(), statId);
                    continue;
                }
                // SCALE: convert percentage of base to a flat bonus
                valueToApply = originalData.getValue() * statData.value();
            }

            history.registerModifierBonus(REFORGE_UUID, new DoubleData(valueToApply));
            liveItem.setData(doubleStat, history.recalculate(liveItem.getUpgradeLevel()));
        }
    }

    // -------------------------------------------------------------------------
    // Tracker persistence
    // -------------------------------------------------------------------------

    /**
     * Reads the existing tracker from NBT, increments the attempt counter,
     * and writes the new tracker back into the live item's data so that
     * {@code whenApplied()} serializes it during {@code build()}.
     */
    public static void updateTrackerModifier(LiveMMOItem liveItem, String modifierId) {
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

        liveItem.setData(
                DevReforgePlus.getInstance().getTrackerStat(),
                new StringData(TrackerUtils.encode(modifierId, attempts))
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
    // Internal helpers
    // -------------------------------------------------------------------------

    private static void warnUnknownStat(String typeId, String modifierId, String statId) {
        DevReforgePlus.getInstance().getLogger().warning(
                "Skipping unknown/unsupported reforge stat: type='" + typeId
                        + "', modifier='" + modifierId + "', stat='" + statId + "'.");
    }
}
