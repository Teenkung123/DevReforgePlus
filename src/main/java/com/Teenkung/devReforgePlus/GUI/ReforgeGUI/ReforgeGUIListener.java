package com.Teenkung.devReforgePlus.GUI.ReforgeGUI;

import com.Teenkung.devReforgePlus.DevReforgePlus;
import com.Teenkung.devReforgePlus.Utils.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

public class ReforgeGUIListener implements Listener {

    @SuppressWarnings("DataFlowIssue")
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (ReforgeGUIManager.isReforgeGUI(event.getInventory())) {
            ReforgeGUI gui = ReforgeGUIManager.getReforgeGUI(event.getInventory());
            if (gui.getInputItem() != null) {
                if (event.getPlayer().getInventory().firstEmpty() != -1) event.getPlayer().getInventory().addItem(gui.getInputItem());
                else event.getPlayer().getWorld().dropItemNaturally(event.getPlayer().getLocation(), gui.getInputItem());
            }
            ReforgeGUIManager.removeGUI(event.getInventory());
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @EventHandler
    public void onInsertItem(InventoryClickEvent e) {
        if (ReforgeGUIManager.isReforgeGUI(e.getView().getTopInventory())) {
            ReforgeGUI gui = ReforgeGUIManager.getReforgeGUI(e.getView().getTopInventory());
            if (e.getClickedInventory() == e.getView().getTopInventory() && e.getSlot() != gui.getInputSlot()) e.setCancelled(true);
            Bukkit.getScheduler().runTaskLater(DevReforgePlus.getInstance(), () -> gui.updateDisplay(), 1L);
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (ReforgeGUIManager.isReforgeGUI(event.getClickedInventory())) {
            ReforgeGUI gui = ReforgeGUIManager.getReforgeGUI(event.getClickedInventory());
            if (event.getSlot() == gui.getReforgeButtonSlot()) handleReforgeButton(event, gui);
            if (event.getSlot() == gui.getModifierListSlot()) {
                event.setCancelled(true);
                gui.nextModifierListPage();
            }

        }
    }

    private void handleReforgeButton(InventoryClickEvent event, ReforgeGUI gui) {
        event.setCancelled(true);
        if (gui.reforgeItem((Player) event.getWhoClicked())) SoundUtils.playSound("ReforgeComplete", (Player) event.getWhoClicked());
        else SoundUtils.playSound("ReforgeFailed", (Player) event.getWhoClicked());
    }
}
