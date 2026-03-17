package com.Teenkung.devReforgePlus.Config.TypeConfig;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable data holder for a single item-type configuration.
 *
 * <p>Each type config file defines:</p>
 * <ul>
 *   <li>Which MMOItems {@code type} strings this configuration applies to (e.g. {@code SWORD}).</li>
 *   <li>A weighted table of modifier IDs that can be rolled for items of those types.</li>
 *   <li>An optional display-weight table used only for GUI sorting and the {@code <weight>}
 *       placeholder. Falls back to the real weight when absent.</li>
 * </ul>
 *
 * <p>The weight values in {@code reforgeWeights} are relative — a modifier with weight {@code 20}
 * is twice as likely to be selected as one with weight {@code 10}.</p>
 *
 * @param mmoItemsTypes  The list of MMOItems type strings this config covers (e.g. {@code ["SWORD"]}).
 * @param reforgeWeights A map of modifier ID → selection weight. Higher weight = higher chance.
 * @param displayWeights An optional map of modifier ID → display-only weight used for GUI
 *                       sorting and the {@code <weight>} placeholder. Entries absent from this
 *                       map fall back to their value in {@code reforgeWeights}.
 **/
public record TypeData(
        List<String> mmoItemsTypes,
        Map<String, Double> reforgeWeights,
        Map<String, Double> displayWeights
) {

    /**
     * Returns an unmodifiable copy of the MMOItems type list.
     * **/
    @Override
    public List<String> mmoItemsTypes() {
        return Collections.unmodifiableList(mmoItemsTypes);
    }

    /**
     * Returns an unmodifiable copy of the reforge weight map.
     * **/
    @Override
    public Map<String, Double> reforgeWeights() {
        return Collections.unmodifiableMap(reforgeWeights);
    }

    /**
     * Returns an unmodifiable copy of the display weight map.
     * Entries here override the real weight for GUI display/sorting only.
     **/
    @Override
    public Map<String, Double> displayWeights() {
        return Collections.unmodifiableMap(displayWeights);
    }

    /**
     * Returns the display weight for a modifier.
     * Falls back to the actual selection weight when no display weight is configured.
     *
     * @param modifierId the modifier ID to look up
     * @return display weight if configured, otherwise the actual selection weight
     */
    public double getDisplayWeight(String modifierId) {
        Double dw = displayWeights.get(modifierId);
        if (dw != null) return dw;
        Double w = reforgeWeights.get(modifierId);
        return w != null ? w : 0.0;
    }

    /**
     * Calculates the total weight of all reforges in this type.
     *
     * @return the sum of all individual weights
     */
    public double getTotalWeight() {
        return reforgeWeights.values().stream().mapToDouble(Double::doubleValue).sum();
    }
}

