package com.Teenkung.devReforgePlus.Config.ModifierConfig;

import com.Teenkung.devReforgePlus.Config.TypeConfig.TypeData;

import java.util.List;
import java.util.Map;

/**
 * Immutable data holder for a single reforge modifier.
 *
 * @param id      The unique identifier of the modifier (matches the YAML {@code id} field).
 * @param display The human-readable display name shown to players.
 * @param stats   A map of MMOItems stat keys to their {@link ReforgeStatData} (type + value).
 *                Weight / selection probability is <em>not</em> stored here — it lives in
 *                {@link TypeData} so that each
 *                item type can assign its own weight to this modifier.
 * @param groups  The tier/group(s) this modifier belongs to (e.g., ["rare", "epic"]). Defaults to ["none"].
 *                A modifier can belong to multiple groups. Used by catalyst {@code GUARANTEED_GROUP}
 *                actions to filter modifier selection. Supports both {@code group: rare} (single)
 *                and {@code groups: [rare, epic]} (list) in YAML.
 */
public record ModifierData(
        String id,
        String display,
        Map<String, ReforgeStatData> stats,
        List<String> groups
) {
    /** Returns {@code true} if this modifier belongs to the given group (case-insensitive). */
    public boolean hasGroup(String group) {
        return groups.stream().anyMatch(g -> g.equalsIgnoreCase(group));
    }
}
