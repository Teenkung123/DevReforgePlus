package com.Teenkung.devReforgePlus.Listener;

import com.Teenkung.devReforgePlus.DevReforgePlus;
import net.Indyuce.mmoitems.api.event.item.ApplyGemStoneEvent;
import net.Indyuce.mmoitems.api.event.item.UnsocketGemStoneEvent;
import net.Indyuce.mmoitems.api.event.item.UpgradeItemEvent;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.stat.data.StringData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Preserves MMOITEMS_REFORGE_TRACKER across MMOItems gemstone socket application.
 *
 * MMOItems fires ApplyGemStoneEvent synchronously inside applyOntoItem(), before gem
 * stats are merged and before newBuilder().build() is called. At that point the
 * LiveMMOItem's backing NBT still contains MMOITEMS_REFORGE_TRACKER. We pre-inject
 * the tracker stat into the data map so that TrackerStat.whenApplied() is called
 * during the subsequent build(), preserving both the NBT key and the reforge lore lines.
 */
public class GemstoneCompatListener implements Listener {

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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSkinApply(UnsocketGemStoneEvent event) {
        if (!(event.getTargetItem() instanceof LiveMMOItem liveItem)) return;
        reapplyData(liveItem);
    }

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
