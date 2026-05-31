package com.Teenkung.devReforgePlus.GUI.ReforgeGUI;

import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;

public class ReforgeGUIManager {

    private static final IdentityHashMap<Inventory, ReforgeGUI> guiMap = new IdentityHashMap<>();

    protected static void addGUI(Inventory inv, ReforgeGUI gui) {
        guiMap.put(inv, gui);
    }

    protected static void removeGUI(Inventory inv) {
        guiMap.remove(inv);
    }

    public static boolean isReforgeGUI(Inventory inv) {
        return guiMap.containsKey(inv);
    }

    public static ReforgeGUI getReforgeGUI(Inventory inv) {
        return guiMap.get(inv);
    }

    public static void closeAll() {
        for (Map.Entry<Inventory, ReforgeGUI> entry : new ArrayList<>(guiMap.entrySet())) {
            new ArrayList<>(entry.getKey().getViewers()).forEach(HumanEntity::closeInventory);
        }
    }

}
