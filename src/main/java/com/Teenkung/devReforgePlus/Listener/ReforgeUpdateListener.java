package com.Teenkung.devReforgePlus.Listener;

import com.Teenkung.devReforgePlus.DevReforgePlus;
import com.Teenkung.devReforgePlus.Utils.MessageUtils;
import com.Teenkung.devReforgePlus.Utils.ReforgeUpdater;
import org.bukkit.Bukkit;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

public class ReforgeUpdateListener implements Listener {

    // Delay after join before scanning inventory (ticks). Gives MMOItems time to
    // finish its own item processing after the player fully loads in.
    private static final long JOIN_SCAN_DELAY_TICKS = 60L;

    // -------------------------------------------------------------------------
    // Events
    // -------------------------------------------------------------------------

    /**
     * Scans the player's full inventory on join, delayed by
     * {@value JOIN_SCAN_DELAY_TICKS} ticks so MMOItems finishes loading first.
     * Runs entirely on the main thread — no async usage.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!isEnabled() || !cfg("AutoUpdater.UpdateOnJoin", true)) return;

        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(DevReforgePlus.getInstance(), () -> {
            if (!player.isOnline()) return;
            int updated = ReforgeUpdater.updatePlayerInventory(player);
            if (updated > 0 && cfg("AutoUpdater.NotifyPlayer", true)) {
                String msg = DevReforgePlus.getInstance().getConfigLoader()
                        .getMessage("AutoUpdaterUpdated",
                                "<green><count> reforged item(s) updated to match latest config.",
                                "<count>", String.valueOf(updated));
                player.sendMessage(MessageUtils.mm(msg));
            }
        }, JOIN_SCAN_DELAY_TICKS);
    }

    /**
     * Updates a reforged item before it is added to the player's inventory on
     * pickup, so the player always receives the latest stat values.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityPickup(EntityPickupItemEvent event) {
        if (!isEnabled() || !cfg("AutoUpdater.UpdateOnPickup", true)) return;
        if (!(event.getEntity() instanceof Player)) return;

        Item droppedItem = event.getItem();
        ItemStack item = droppedItem.getItemStack().clone();
        if (ReforgeUpdater.updateItemIfNeeded(item)) {
            droppedItem.setItemStack(item);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean isEnabled() {
        return cfg("AutoUpdater.Enabled", true);
    }

    /** Reads a boolean config value with a fallback default. */
    private boolean cfg(String path, boolean def) {
        return DevReforgePlus.getInstance().getConfig().getBoolean(path, def);
    }
}
