package com.Teenkung.devReforgePlus.Utils;

import com.Teenkung.devReforgePlus.DevReforgePlus;
import lombok.Getter;
import lombok.Setter;
import com.Teenkung.devReforgePlus.Utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GUIBuilder {

    @Setter
    @Getter
    private String title;
    @Setter
    @Getter
    private List<String> layout;
    @Setter
    @Getter
    private Map<Character, ItemBuilder> characterMap;
    @Setter
    @Getter
    private ConfigurationSection options;

    public GUIBuilder() {
    }

    public void loadFrom(ConfigurationSection config) {
        this.title = config.getString("Title");
        if (this.title == null) {
            DevReforgePlus.getInstance().getLogger().severe("Title is missing in GUI config.");
        }
        this.layout = config.getStringList("Layout");
        if (this.layout.isEmpty()) {
            DevReforgePlus.getInstance().getLogger().severe("Layout is missing in GUI config.");
        }
        this.characterMap = createCharacterMap(config);
        if (this.characterMap.isEmpty()) {
            DevReforgePlus.getInstance().getLogger().warning("Items are missing in GUI config.");
        }
        this.options = config.getConfigurationSection("Options");
    }

    public void setCharacter(char c, ItemBuilder itemBuilder) {
        characterMap.put(c, itemBuilder);
    }

    public void removeCharacter(char c) {
        characterMap.remove(c);
    }

    private Map<Character, ItemBuilder> createCharacterMap(ConfigurationSection config) {
        Map<Character, ItemBuilder> map = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("Items");
        if (section == null) return map;
        for (String key : section.getKeys(false)) {
            map.put(key.charAt(0), new ItemBuilder(section.getConfigurationSection(key)));
        }
        return map;
    }

    public Inventory build() {
        Inventory inventory = Bukkit.createInventory(null, layout.size()*9, MessageUtils.mm(title));

        int i = 0;
        for (String line : layout) {
            for (int j = 0; j < 9; j++) {
                if (j >= line.length()) continue;
                char c = line.charAt(j);
                if (characterMap.containsKey(c)) {
                    inventory.setItem(j+(i*9), characterMap.get(c).build());
                }
            }
            i++;
        }

        return inventory;
    }

}
