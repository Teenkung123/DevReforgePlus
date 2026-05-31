package com.Teenkung.devReforgePlus.GUI.ReforgeGUI;

import com.Teenkung.devReforgePlus.DevReforgePlus;
import com.Teenkung.devReforgePlus.Utils.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class ReforgeGUIListener implements Listener {

    // ── Close: return all player items ──────────────────────────────────────

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!ReforgeGUIManager.isReforgeGUI(event.getInventory())) return;
        ReforgeGUI gui = ReforgeGUIManager.getReforgeGUI(event.getInventory());
        if (gui == null) return;
        ReforgeGUIManager.removeGUI(event.getInventory());

        // Return item from input slot
        ItemStack inputItem = gui.getInputItem();
        if (inputItem != null && !inputItem.getType().isAir()) {
            Map<Integer, ItemStack> leftovers = event.getPlayer().getInventory().addItem(inputItem);
            leftovers.values().forEach(i ->
                    event.getPlayer().getWorld().dropItemNaturally(event.getPlayer().getLocation(), i));
        }

        // Return item from catalyst INPUT slot.
        // catalystInputSlot is a raw item slot (like inputSlot) — no GUI placeholder is ever
        // written there, so a non-null, non-air item is always a real player item.
        ItemStack catalystItem = gui.getCatalystItem();
        if (catalystItem != null && !catalystItem.getType().isAir()) {
            Map<Integer, ItemStack> leftovers = event.getPlayer().getInventory().addItem(catalystItem);
            leftovers.values().forEach(i ->
                    event.getPlayer().getWorld().dropItemNaturally(event.getPlayer().getLocation(), i));
        }
    }

    // ── Drag: always cancel inside the reforge GUI ──────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!ReforgeGUIManager.isReforgeGUI(e.getView().getTopInventory())) return;
        e.setCancelled(true);
    }

    // ── Click: cancel everything, handle interactions manually ──────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryInteract(InventoryClickEvent e) {
        if (!ReforgeGUIManager.isReforgeGUI(e.getView().getTopInventory())) return;
        ReforgeGUI gui = ReforgeGUIManager.getReforgeGUI(e.getView().getTopInventory());
        if (gui == null) return;

        // Cancel ALL events unconditionally — no Bukkit item movement whatsoever
        e.setCancelled(true);

        Inventory clicked = e.getClickedInventory();
        if (clicked == null) return;

        // ── Bottom inventory: route clicked item to the correct slot ────────
        if (clicked.equals(e.getView().getBottomInventory())) {
            ItemStack current = e.getCurrentItem();
            if (current == null || current.getType().isAir()) return;

            // Route to catalyst INPUT slot if:
            //   1. The item is a recognized catalyst
            //   2. The catalyst input slot is currently empty
            // catalystInputSlot is a raw slot — checking for null/air is sufficient.
            ItemStack existingCatalyst = gui.getCatalystItem();
            boolean catalystSlotEmpty = existingCatalyst == null || existingCatalyst.getType().isAir();
            boolean itemIsCatalyst = DevReforgePlus.getInstance().getConfigLoader()
                    .getCatalystConfig().getByItem(current) != null;

            if (catalystSlotEmpty && itemIsCatalyst) {
                e.getView().getTopInventory().setItem(gui.getCatalystInputSlot(), current.clone());
                e.getClickedInventory().setItem(e.getSlot(), null);
                Bukkit.getScheduler().runTaskLater(DevReforgePlus.getInstance(), gui::updateDisplay, 1L);
                return;
            }

            // Otherwise route to item input slot
            ItemStack inputItem = gui.getInputItem();
            if (inputItem != null && !inputItem.getType().isAir()) return;

            e.getView().getTopInventory().setItem(gui.getInputSlot(), current.clone());
            e.getClickedInventory().setItem(e.getSlot(), null);
            Bukkit.getScheduler().runTaskLater(DevReforgePlus.getInstance(), gui::updateDisplay, 1L);
            return;
        }

        // ── Top inventory ───────────────────────────────────────────────────
        if (!clicked.equals(e.getView().getTopInventory())) return;

        int slot = e.getSlot();

        // Click item input slot → return item to player
        if (slot == gui.getInputSlot()) {
            ItemStack inputItem = gui.getInputItem();
            if (inputItem == null || inputItem.getType().isAir()) return;

            Map<Integer, ItemStack> leftovers = e.getWhoClicked().getInventory().addItem(inputItem.clone());
            leftovers.values().forEach(i ->
                    e.getWhoClicked().getWorld().dropItemNaturally(e.getWhoClicked().getLocation(), i));
            e.getView().getTopInventory().setItem(gui.getInputSlot(), null);
            Bukkit.getScheduler().runTaskLater(DevReforgePlus.getInstance(), gui::updateDisplay, 1L);

        // Click catalyst INPUT slot → return catalyst to player
        } else if (slot == gui.getCatalystInputSlot()) {
            ItemStack catalystItem = gui.getCatalystItem();
            if (catalystItem == null || catalystItem.getType().isAir()) return;

            Map<Integer, ItemStack> leftovers = e.getWhoClicked().getInventory().addItem(catalystItem.clone());
            leftovers.values().forEach(i ->
                    e.getWhoClicked().getWorld().dropItemNaturally(e.getWhoClicked().getLocation(), i));
            e.getView().getTopInventory().setItem(gui.getCatalystInputSlot(), null);
            Bukkit.getScheduler().runTaskLater(DevReforgePlus.getInstance(), gui::updateDisplay, 1L);

        } else if (slot == gui.getReforgeButtonSlot()) {
            handleReforgeButton(e, gui);
        } else if (slot == gui.getModifierListSlot()) {
            gui.nextModifierListPage();
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void handleReforgeButton(InventoryClickEvent event, ReforgeGUI gui) {
        if (gui.reforgeItem((Player) event.getWhoClicked())) {
            SoundUtils.playSound("ReforgeComplete", (Player) event.getWhoClicked());
        } else {
            SoundUtils.playSound("ReforgeFailed", (Player) event.getWhoClicked());
        }
    }
}
