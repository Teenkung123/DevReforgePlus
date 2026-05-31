package com.Teenkung.devReforgePlus.Config.CatalystConfig;

import java.util.List;

/**
 * A single action performed by a catalyst during a reforge.
 *
 * @param type        The action type.
 * @param amount      Amount to reduce by (used by {@link CatalystActionType#REDUCE_COUNT}).
 * @param groups      Group names to restrict modifier selection to (used by {@link CatalystActionType#GUARANTEED_GROUP}).
 * @param modifierIds Modifier IDs to restrict selection to (used by {@link CatalystActionType#GUARANTEED_MODIFIER}).
 */
public record CatalystAction(
        CatalystActionType type,
        int amount,
        List<String> groups,
        List<String> modifierIds
) {
}
