package com.Teenkung.devReforgePlus.Config.ModifierConfig;

import com.Teenkung.devReforgePlus.Config.TypeConfig.TypeData;

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
 */
public record ModifierData(
        String id,
        String display,
        Map<String, ReforgeStatData> stats
) {
}
