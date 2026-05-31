package com.Teenkung.devReforgePlus.Utils;

import com.Teenkung.devReforgePlus.DevReforgePlus;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Logs reforge attempts to a daily CSV file in {@code <plugin-data>/logs/reforge-YYYY-MM-DD.csv}.
 *
 * <p>Writes are performed asynchronously to avoid blocking the main thread.
 * Logging can be disabled via {@code Logging.Enabled: false} in {@code config.yml}.</p>
 *
 * <p>CSV columns:</p>
 * <pre>timestamp,player_uuid,player_name,item_type,item_id,previous_modifier,new_modifier,attempt_before,attempt_after,cost_paid,catalyst_id</pre>
 */
public class ReforgeLogger {

    private static final String CSV_HEADER =
            "timestamp,player_uuid,player_name,item_type,item_id,previous_modifier,new_modifier,attempt_before,attempt_after,cost_paid,catalyst_id";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final DevReforgePlus plugin;
    private final File logsDir;

    public ReforgeLogger(DevReforgePlus plugin) {
        this.plugin = plugin;
        this.logsDir = new File(plugin.getDataFolder(), "logs");
        if (!logsDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            logsDir.mkdirs();
        }
    }

    /**
     * Logs a single reforge event asynchronously.
     * No-op if {@code Logging.Enabled} is {@code false} in config.
     *
     * @param playerUuid      UUID of the player
     * @param playerName      Name of the player
     * @param itemType        MMOItems type ID of the reforged item
     * @param itemId          MMOItems item ID of the reforged item
     * @param previousModifier Modifier that was on the item before (null if none)
     * @param newModifier      Modifier applied after the reforge (null if REMOVE_MODIFIER catalyst)
     * @param attemptBefore   Reforge count before this reforge
     * @param attemptAfter    Reforge count stored in tracker after this reforge
     * @param costPaid        Economy cost deducted from the player
     * @param catalystId      ID of the catalyst used (null if none)
     */
    public void logReforge(
            UUID playerUuid,
            String playerName,
            String itemType,
            String itemId,
            @Nullable String previousModifier,
            @Nullable String newModifier,
            int attemptBefore,
            int attemptAfter,
            double costPaid,
            @Nullable String catalystId
    ) {
        if (!plugin.getConfig().getBoolean("Logging.Enabled", true)) return;

        String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
        String row = String.join(",",
                escapeCsv(timestamp),
                escapeCsv(playerUuid.toString()),
                escapeCsv(playerName),
                escapeCsv(itemType),
                escapeCsv(itemId),
                escapeCsv(previousModifier != null ? previousModifier : "none"),
                escapeCsv(newModifier != null ? newModifier : "none"),
                String.valueOf(attemptBefore),
                String.valueOf(attemptAfter),
                String.valueOf(costPaid),
                escapeCsv(catalystId != null ? catalystId : "none")
        );

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> writeRow(row));
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private void writeRow(String row) {
        File file = getLogFile(LocalDate.now());
        boolean isNewFile = !file.exists();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
            if (isNewFile) {
                writer.write(CSV_HEADER);
                writer.newLine();
            }
            writer.write(row);
            writer.newLine();
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write to reforge log: " + e.getMessage());
        }
    }

    private File getLogFile(LocalDate date) {
        return new File(logsDir, "reforge-" + date.format(DATE_FMT) + ".csv");
    }

    /** Wraps a value in double-quotes and escapes any existing double-quotes inside it. */
    private static String escapeCsv(String value) {
        if (value == null) return "none";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
