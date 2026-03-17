package com.Teenkung.devReforgePlus.Utils;

import com.Teenkung.devReforgePlus.DevReforgePlus;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class SoundUtils {

    public static void playSound(String configPath, Player player) {
        if (configPath == null || configPath.isEmpty()) return;

        String rawConfig = DevReforgePlus.getInstance().getConfig().getString("Sounds."+configPath);
        if (rawConfig == null || rawConfig.isEmpty()) return;

        String soundName = rawConfig;
        float volume = 1f;
        float pitch = 1f;

        String[] parts = rawConfig.split(":");
        if (parts.length >= 2) {
            try {
                // Try treating the last part as pitch and the second to last as volume
                pitch = Float.parseFloat(parts[parts.length - 1]);
                volume = Float.parseFloat(parts[parts.length - 2]);
                int lastColon = rawConfig.lastIndexOf(':');
                int secondLastColon = rawConfig.lastIndexOf(':', lastColon - 1);
                soundName = rawConfig.substring(0, secondLastColon);
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                // Try treating just the last part as volume
                try {
                    volume = Float.parseFloat(parts[parts.length - 1]);
                    pitch = 1f;
                    soundName = rawConfig.substring(0, rawConfig.lastIndexOf(':'));
                } catch (NumberFormatException ignored) {}
            }
        }

        Sound sound = null;
        try {
            NamespacedKey key = NamespacedKey.fromString(soundName.toLowerCase());
            if (key != null) {
                sound = Registry.SOUNDS.get(key);
            }
        } catch (Exception ignored) {}

        if (sound == null) {
            try {
                NamespacedKey key = NamespacedKey.minecraft(soundName.toLowerCase().replace("_", "."));
                sound = Registry.SOUNDS.get(key);
            } catch (Exception ignored) {}
        }

        if (sound != null) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        } else {
            player.playSound(player.getLocation(), soundName, volume, pitch);
        }
    }

}
