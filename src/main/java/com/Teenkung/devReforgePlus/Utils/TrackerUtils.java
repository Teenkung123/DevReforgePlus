package com.Teenkung.devReforgePlus.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

public class TrackerUtils {

    public static String encode(String modifier, Integer attempts, List<String> appliedStats) {
        String statsStr = appliedStats == null || appliedStats.isEmpty() ? "" : String.join(",", appliedStats);
        String payload = modifier + ":" + attempts + ":" + statsStr;
        byte[] encoded = Base64.getEncoder().encode(payload.getBytes());
        return new String(encoded);
    }

    public static TrackerPayload decode(String encoded) {
        byte[] decoded = Base64.getDecoder().decode(encoded);
        String payload = new String(decoded);
        String[] parts = payload.split(":", 3);

        // Support old format (backward compatibility)
        if (parts.length == 2) {
            return new TrackerPayload(parts[0], Integer.parseInt(parts[1]), new ArrayList<>());
        }

        // New format with stat tracking
        List<String> appliedStats = new ArrayList<>();
        if (parts.length >= 3 && !parts[2].isEmpty()) {
            appliedStats = new ArrayList<>(Arrays.asList(parts[2].split(",")));
        }

        return new TrackerPayload(parts[0], Integer.parseInt(parts[1]), appliedStats);
    }

}
