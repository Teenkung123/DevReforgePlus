package com.Teenkung.devReforgePlus.Utils;

import java.util.List;

public record TrackerPayload(String modifier, Integer attempt, List<String> appliedStats) {

}
