package com.Teenkung.devReforgePlus.Config.ModifierConfig;

import org.jetbrains.annotations.Nullable;

/**
 * Holds a single stat entry for a modifier: its application type (FLAT or SCALE), the value,
 * and an optional flat fallback used when the item has no base value for a SCALE stat.
 *
 * @param type     How the value is applied — {@link ReforgeStatType#FLAT} adds a flat amount,
 *                 {@link ReforgeStatType#SCALE} multiplies the base stat by this value.
 * @param value    The numeric amount to apply.
 * @param fallback Optional flat value applied when {@code type} is {@link ReforgeStatType#SCALE}
 *                 and the item's base stat is 0 (absent). {@code null} preserves the previous
 *                 behaviour of skipping the stat and logging a warning.
 */
public record ReforgeStatData(ReforgeStatType type, double value, @Nullable Double fallback) {
}
