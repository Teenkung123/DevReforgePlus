package com.Teenkung.devReforgePlus.Config.CatalystConfig;

/**
 * The type of action a catalyst performs during a reforge.
 */
public enum CatalystActionType {
    /** Resets the item's reforge attempt count to 0, so next reforge costs Economy.Base. */
    RESET_COUNT,
    /** Reduces the item's reforge attempt count by a configured amount (minimum 0). */
    REDUCE_COUNT,
    /** Clears all reforge modifier effects and the tracker from the item (no new modifier applied). */
    REMOVE_MODIFIER,
    /** Guarantees the new modifier belongs to one of the specified groups. */
    GUARANTEED_GROUP,
    /** Guarantees the new modifier is one of the specified modifier IDs (intersected with the type's pool). */
    GUARANTEED_MODIFIER
}
