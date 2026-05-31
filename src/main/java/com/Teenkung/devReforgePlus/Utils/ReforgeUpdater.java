package com.Teenkung.devReforgePlus.Utils;

import com.Teenkung.devReforgePlus.Config.ModifierConfig.ModifierData;
import com.Teenkung.devReforgePlus.Config.ModifierConfig.ReforgeStatData;
import com.Teenkung.devReforgePlus.DevReforgePlus;
import com.Teenkung.devReforgePlus.GUI.ReforgeGUI.ReforgeItemEngine;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.stat.data.DoubleData;
import net.Indyuce.mmoitems.stat.data.StringData;
import net.Indyuce.mmoitems.stat.type.DoubleStat;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReforgeUpdater {

    /**
     * Checks whether the given item has a reforge modifier whose applied stats
     * no longer match the current config, and updates it in-place if so.
     *
     * <p>Preserves: reforge attempt count, PDC data (EcoEnchants etc.), and
     * any enchantments not tracked by MMOItems.</p>
     *
     * <p>Must be called on the main thread.</p>
     *
     * @return {@code true} if the item was updated, {@code false} otherwise
     */
    public static boolean updateItemIfNeeded(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (Type.get(item) == null) return false;

        try {
            LiveMMOItem liveItem = new LiveMMOItem(item);
            String rawTracker = liveItem.getNBT().getString("MMOITEMS_REFORGE_TRACKER");
            if (rawTracker == null || rawTracker.isEmpty()) return false;

            TrackerPayload tracker = TrackerUtils.decode(rawTracker);
            ModifierData currentConfig = DevReforgePlus.getInstance()
                    .getConfigLoader().getModifierConfig().getModifier(tracker.modifier());

            if (currentConfig == null) {
                DevReforgePlus.getInstance().getLogger().warning(
                        "[AutoUpdater] Item has modifier '" + tracker.modifier()
                                + "' which no longer exists in config. Skipping.");
                return false;
            }

            if (!needsUpdate(tracker, currentConfig)) return false;

            return performUpdate(liveItem, tracker, currentConfig, item);

        } catch (Exception e) {
            DevReforgePlus.getInstance().getLogger().warning(
                    "[AutoUpdater] Failed to update item: " + e.getMessage());
            return false;
        }
    }

    /**
     * Scans and updates all reforged items across a player's full inventory
     * (storage, armor, off-hand).
     *
     * <p>Must be called on the main thread.</p>
     *
     * @return number of items that were updated
     */
    public static int updatePlayerInventory(Player player) {
        int count = 0;
        PlayerInventory inv = player.getInventory();

        // Storage slots (0 – 35)
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null) continue;
            if (updateItemIfNeeded(item)) {
                inv.setItem(slot, item);
                count++;
            }
        }

        // Armor slots
        ItemStack[] armor = inv.getArmorContents();
        boolean armorChanged = false;
        for (ItemStack piece : armor) {
            if (piece == null) continue;
            if (updateItemIfNeeded(piece)) {
                armorChanged = true;
                count++;
            }
        }
        if (armorChanged) inv.setArmorContents(armor);

        // Off-hand
        ItemStack offhand = inv.getItemInOffHand();
        if (!offhand.getType().isAir() && updateItemIfNeeded(offhand)) {
            inv.setItemInOffHand(offhand);
            count++;
        }

        return count;
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    /**
     * Computes a short hex hash of the modifier's stat configuration — covering
     * stat IDs, application types, values, and fallbacks.  Sorted by stat ID so
     * HashMap ordering never affects the result.
     *
     * <p>This is stored in the tracker on every reforge/update so that even a
     * pure value change (e.g. +5 → +8) is detected on the next check.</p>
     */
    public static String computeConfigHash(ModifierData modifier) {
        StringBuilder sb = new StringBuilder();
        modifier.stats().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    ReforgeStatData sd = e.getValue();
                    sb.append(e.getKey()).append(':')
                      .append(sd.type().name()).append(':')
                      .append(sd.value());
                    if (sd.fallback() != null) sb.append(':').append(sd.fallback());
                    sb.append('|');
                });
        return Integer.toHexString(sb.toString().hashCode());
    }

    /**
     * Returns {@code true} if the item needs updating.
     *
     * <p>Two-stage check:
     * <ol>
     *   <li>Stat-key set comparison — catches added/removed stats.</li>
     *   <li>Config hash comparison — catches value/type/fallback changes.
     *       Skipped for old items that have no stored hash (graceful migration:
     *       those items will start storing a hash on their next reforge/update).</li>
     * </ol>
     */
    private static boolean needsUpdate(TrackerPayload tracker, ModifierData currentConfig) {
        // Stage 1: stat keys added or removed
        Set<String> trackedStats = new HashSet<>(tracker.appliedStats());
        Set<String> configStats  = currentConfig.stats().keySet();
        if (!trackedStats.equals(configStats)) return true;

        // Stage 2: any value / type / fallback changed (only if hash is present)
        String storedHash = tracker.configHash();
        if (storedHash != null) {
            return !storedHash.equals(computeConfigHash(currentConfig));
        }

        // No hash on this item (pre-hash format) — stat keys match, assume up to date.
        // The hash will be written when the item is next reforged or updated.
        return false;
    }

    /**
     * Clears old stats, applies the new config, rebuilds, and restores all
     * untracked data (PDC, external enchantments) onto {@code originalItem}.
     */
    private static boolean performUpdate(LiveMMOItem liveItem, TrackerPayload oldTracker,
                                         ModifierData newConfig, ItemStack originalItem) {
        // Snapshot original meta before we touch anything — needed to restore PDC/enchants
        ItemMeta originalMeta = originalItem.getItemMeta();

        String typeId = liveItem.getType().getId();

        // Remove old stats using the TRACKED list (not current config) so we correctly
        // undo whatever was actually applied, even if the config has since changed.
        clearTrackedStats(liveItem, oldTracker, typeId);

        // Apply new effects from current config
        List<String> newAppliedStats = ReforgeItemEngine.applyModifierEffects(liveItem, newConfig, typeId);

        // Write tracker back — preserve the original attempt count, do NOT increment it
        liveItem.setData(
                DevReforgePlus.getInstance().getTrackerStat(),
                new StringData(TrackerUtils.encode(newConfig.id(), oldTracker.attempt(),
                        newAppliedStats, computeConfigHash(newConfig)))
        );

        // Rebuild the MMOItem
        ItemStack updated = liveItem.newBuilder().build();
        if (updated == null) return false;

        // Restore PDC data and untracked enchantments that the MMOItems rebuild discards
        ReforgeItemEngine.restoreUntracked(originalMeta, updated);

        // Apply updated meta back onto the original ItemStack in-place so callers
        // that hold a reference to the same object see the change
        originalItem.setItemMeta(updated.getItemMeta());
        return true;
    }

    /**
     * Removes the modifier bonus for every stat that was recorded in the old
     * tracker payload, then performs safe cleanup (removes the stat entirely if
     * it is now 0.0 and has no other contributing sources).
     */
    private static void clearTrackedStats(LiveMMOItem liveItem, TrackerPayload tracker, String typeId) {
        for (String statId : tracker.appliedStats()) {
            var stat = StatResolver.resolve(statId);
            if (!(stat instanceof DoubleStat doubleStat)) continue;

            var history = liveItem.computeStatHistory(doubleStat);
            history.removeModifierBonus(DevReforgePlus.REFORGE_UUID);

            var recalculated = history.recalculate(liveItem.getUpgradeLevel());
            liveItem.setData(doubleStat, recalculated);

            if (recalculated instanceof DoubleData dd && dd.getValue() == 0.0) {
                boolean hasOtherSources = !history.getAllModifiers().isEmpty()
                        || !history.getAllGemstones().isEmpty()
                        || !history.getExternalData().isEmpty();
                if (!hasOtherSources) liveItem.removeData(doubleStat);
            }
        }
        ReforgeItemEngine.clearNamePrefix(liveItem);
    }
}
