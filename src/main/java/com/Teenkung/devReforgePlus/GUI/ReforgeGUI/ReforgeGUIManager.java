package com.Teenkung.devReforgePlus.GUI.ReforgeGUI;

import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

public class ReforgeGUIManager {

    private static final List<ReforgeGUI> inventory = new ArrayList<>();

    protected static void addGUI(ReforgeGUI gui) {
        inventory.add(gui);
    }

    protected static void removeGUI(ReforgeGUI gui) {
        inventory.remove(gui);
    }

    protected static void removeGUI(Inventory inv) {
        inventory.removeIf(gui -> gui.getInventory().equals(inv));
    }

    public static boolean isReforgeGUI(Inventory inv) {
        for (ReforgeGUI gui : inventory) {
            if (gui.getInventory().equals(inv)) {
                return true;
            }
        }
        return false;
    }

    public static ReforgeGUI getReforgeGUI(Inventory inv) {
        for (ReforgeGUI gui : inventory) {
            if (gui.getInventory().equals(inv)) {
                return gui;
            }
        }
        return null;
    }

    public static void closeAll() {
        // Copy the list to prevent ConcurrentModificationException when InventoryCloseEvent triggers removeGUI()
        for (ReforgeGUI gui : new ArrayList<>(inventory)) {
            new ArrayList<>(gui.getInventory().getViewers()).forEach(HumanEntity::closeInventory);
        }
    }

}
