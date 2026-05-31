package com.Teenkung.devReforgePlus.Utils;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @param configHash Hex hash of the modifier's stat config at the time of last reforge/update.
 *                   {@code null} on items reforged before hash tracking was added —
 *                   treated as "unknown, skip value-change detection".
 */
public record TrackerPayload(String modifier, Integer attempt, List<String> appliedStats,
                             @Nullable String configHash) {
}
