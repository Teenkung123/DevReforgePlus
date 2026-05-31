package com.Teenkung.devReforgePlus.Config.CatalystConfig;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Immutable data holder for a single catalyst item configuration.
 *
 * @param id           Unique identifier matching the YAML {@code id} field.
 * @param displayName  MiniMessage-formatted display name shown in the catalyst slot.
 * @param mmoItemType  MMOItems item type string (e.g. "CONSUMABLE").
 * @param mmoItemId    MMOItems item ID string (e.g. "reset_crystal").
 * @param actions      Ordered list of actions this catalyst applies during a reforge.
 */
public record CatalystData(
        String id,
        String displayName,
        String mmoItemType,
        String mmoItemId,
        List<CatalystAction> actions
) {

    /**
     * Returns {@code true} if this catalyst contains an action of the given type.
     */
    public boolean hasAction(CatalystActionType type) {
        return actions.stream().anyMatch(a -> a.type() == type);
    }

    /**
     * Returns the first action of the given type, or {@code null} if none exists.
     */
    public @Nullable CatalystAction getAction(CatalystActionType type) {
        Optional<CatalystAction> found = actions.stream().filter(a -> a.type() == type).findFirst();
        return found.orElse(null);
    }
}
