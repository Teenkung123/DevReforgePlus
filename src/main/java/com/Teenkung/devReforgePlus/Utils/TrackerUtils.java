package com.Teenkung.devReforgePlus.Utils;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

public class TrackerUtils {

    /**
     * Encodes a tracker payload.
     *
     * <p>Wire format (Base64 of): {@code modifier:attempts:stat1,stat2:configHash}
     *
     * @param configHash hex hash of the modifier config at time of reforge; empty string if unknown
     */
    public static String encode(String modifier, int attempts, List<String> appliedStats,
                                @Nullable String configHash) {
        String statsStr = appliedStats == null || appliedStats.isEmpty() ? "" : String.join(",", appliedStats);
        String hashStr  = configHash  == null ? "" : configHash;
        String payload  = modifier + ":" + attempts + ":" + statsStr + ":" + hashStr;
        return Base64.getEncoder().encodeToString(payload.getBytes());
    }

    /**
     * Decodes a tracker payload. Backward-compatible with three older formats:
     * <ul>
     *   <li>{@code modifier:attempts} — very old, no stat list</li>
     *   <li>{@code modifier:attempts:stats} — previous version, no config hash</li>
     *   <li>{@code modifier:attempts:stats:hash} — current format</li>
     * </ul>
     * Items from the first two formats will have {@code configHash = null}, which tells
     * {@link ReforgeUpdater} to skip value-change detection until the item is next touched.
     */
    public static TrackerPayload decode(String encoded) {
        byte[] decoded = Base64.getDecoder().decode(encoded);
        String payload = new String(decoded);
        // Limit to 4 parts: modifier, attempts, stats, hash
        String[] parts = payload.split(":", 4);

        // Very old format: modifier:attempts
        if (parts.length == 2) {
            return new TrackerPayload(parts[0], Integer.parseInt(parts[1]),
                    new ArrayList<>(), null);
        }

        List<String> appliedStats = new ArrayList<>();
        if (!parts[2].isEmpty()) {
            appliedStats = new ArrayList<>(Arrays.asList(parts[2].split(",")));
        }

        // Old format: modifier:attempts:stats (no hash)
        if (parts.length == 3) {
            return new TrackerPayload(parts[0], Integer.parseInt(parts[1]),
                    appliedStats, null);
        }

        // Current format: modifier:attempts:stats:hash
        String configHash = parts[3].isEmpty() ? null : parts[3];
        return new TrackerPayload(parts[0], Integer.parseInt(parts[1]),
                appliedStats, configHash);
    }
}
