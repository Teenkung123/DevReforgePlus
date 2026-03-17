package com.Teenkung.devReforgePlus.Listener;

import com.Teenkung.devReforgePlus.DevReforgePlus;
import com.Teenkung.devReforgePlus.GUI.ReforgeGUI.ReforgeItemEngine;
import net.Indyuce.mmoitems.api.event.item.ApplyGemStoneEvent;
import net.Indyuce.mmoitems.api.event.item.UnsocketGemStoneEvent;
import net.Indyuce.mmoitems.api.event.item.UpgradeItemEvent;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.stat.data.StringData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Preserves MMOITEMS_REFORGE_TRACKER and untracked item data (PDC, 3rd-party enchants)
 * across MMOItems item rebuilds triggered by gem application, gem removal, and upgrades.
 *
 * Two strategies are used depending on whether we have access to the pre-build LiveMMOItem
 * or the post-build ItemStack:
 *
 *  • Pre-build (ApplyGemStoneEvent, UnsocketGemStoneEvent, UpgradeItemEvent): the event
 *    gives a live reference to the LiveMMOItem that will be passed to newBuilder().build().
 *    We inject TrackerStat data into its data map so whenApplied() is called during build().
 *
 *  • Post-build untracked data (UnsocketGemStoneEvent only): RandomUnsocket calls
 *    event.setCurrentItem(rebuilt) during the same InventoryClickEvent, so by MONITOR
 *    priority on that click the rebuilt ItemStack is already set. We bridge the two phases
 *    with a per-player pending map: store originalMeta in onGemRemove (HIGHEST), read the
 *    rebuilt item and restore in onClickMonitor (MONITOR).
 */
public class GemstoneCompatListener implements Listener {

    /** Keyed by player UUID; populated in onGemRemove, consumed in onClickMonitor. */
    private final Map<UUID, ItemMeta> pendingRestore = new HashMap<>();

    // -------------------------------------------------------------------------
    // Pre-build: tracker injection
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGemApply(ApplyGemStoneEvent event) {
        if (!(event.getTargetItem() instanceof LiveMMOItem liveItem)) return;
        reapplyData(liveItem);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUpgrade(UpgradeItemEvent event) {
        if (!(event.getTargetItem() instanceof LiveMMOItem liveItem)) return;
        reapplyData(liveItem);
    }

    /**
     * Handles gem removal (UnsocketGemStoneEvent).
     *
     * Does two things before the rebuild:
     *  1. Pre-injects the tracker so it survives newBuilder().build().
     *  2. Snapshots the original ItemMeta for post-build restoration of PDC and
     *     untracked enchantments via onClickMonitor.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGemRemove(UnsocketGemStoneEvent event) {
        if (!(event.getTargetItem() instanceof LiveMMOItem liveItem)) return;
        reapplyData(liveItem);

        ItemMeta originalMeta = liveItem.getNBT().getItem().getItemMeta();
        if (originalMeta == null) return;
        pendingRestore.put(event.getPlayerData().getPlayer().getUniqueId(), originalMeta);
    }

    // -------------------------------------------------------------------------
    // Post-build: untracked data restoration for gem removal
    // -------------------------------------------------------------------------

    /**
     * Fires at MONITOR priority on every InventoryClickEvent.
     * Only acts when onGemRemove stored a pending restore for this player —
     * which happens in the same synchronous event chain, so this fires immediately
     * after RandomUnsocket calls event.setCurrentItem(rebuilt).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClickMonitor(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemMeta originalMeta = pendingRestore.remove(player.getUniqueId());
        if (originalMeta == null) return;
        ReforgeItemEngine.restoreUntracked(originalMeta, event.getCurrentItem());
    }

    // -------------------------------------------------------------------------
    // Shared helper
    // -------------------------------------------------------------------------

    private void reapplyData(LiveMMOItem liveItem) {
        // Read tracker directly from backing NBT (stat is not auto-loaded via MMOItems registry)
        String trackerValue = liveItem.getNBT().getString("MMOITEMS_REFORGE_TRACKER");
        if (trackerValue == null || trackerValue.isEmpty()) return;

        // Pre-inject into data map — build() will call TrackerStat.whenApplied() which
        // writes the NBT key and inserts the reforge lore lines.
        liveItem.setData(
                DevReforgePlus.getInstance().getTrackerStat(),
                new StringData(trackerValue)
        );
    }
}
