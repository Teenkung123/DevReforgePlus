package com.Teenkung.devReforgePlus.Stats;

import com.Teenkung.devReforgePlus.Config.ConfigLoader;
import com.Teenkung.devReforgePlus.Config.ModifierConfig.ModifierData;
import com.Teenkung.devReforgePlus.Config.ModifierConfig.ReforgeStatData;
import com.Teenkung.devReforgePlus.DevReforgePlus;
import com.Teenkung.devReforgePlus.Utils.MessageUtils;
import com.Teenkung.devReforgePlus.Utils.TrackerPayload;
import com.Teenkung.devReforgePlus.Utils.TrackerUtils;
import net.Indyuce.mmoitems.api.item.build.ItemStackBuilder;
import net.Indyuce.mmoitems.stat.data.StringData;
import net.Indyuce.mmoitems.stat.type.StringStat;
import org.apache.commons.lang3.Validate;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class TrackerStat extends StringStat {

    public TrackerStat() {
        super("REFORGE_TRACKER", Material.BARRIER, "Reforge Tracker", new String[]{"DO NOT TOUCH."}, new String[0]);
    }

    @Override
    public StringData whenInitialized(Object object) {
        //noinspection DataFlowIssue
        Validate.isTrue(false, "This stat is not editable.");
        return null;
    }

    @Override
    public void whenApplied(@NotNull ItemStackBuilder item, @NotNull StringData data) {
        item.getLore().insert("reforge", buildLore(data.getString()));
        item.addItemTag(getAppliedNBT(data));
    }

    @NotNull
    @Override
    public String getNBTPath() {
        return "MMOITEMS_REFORGE_TRACKER";
    }

    /**
     * Builds a list of lore strings based on the provided reforge ID.
     *
     * @param reforgeId the identifier of the reforge, used to generate the lore.
     * @return a list of strings representing the lore, including the reforge ID.
     */
    public List<String> buildLore(String reforgeId) {
        TrackerPayload payload = TrackerUtils.decode(reforgeId);
        ConfigLoader configLoader = DevReforgePlus.getInstance().getConfigLoader();
        
        ModifierData modData = configLoader.getModifierConfig().getModifier(payload.modifier());
        if (modData == null) {
            String msg = configLoader.getMessage("UnknownReforge", "&cUnknown Reforge: &f<reforge_id>",
                    "<reforge_id>", payload.modifier());
            return new ArrayList<>(List.of(MessageUtils.toLegacy(msg)));
        }

        List<String> finalLore = new ArrayList<>();

        // Add Header
        String headerRaw = configLoader.getDisplayLoreLine().replace("<reforge_display>", modData.display());
        finalLore.add(MessageUtils.toLegacy(headerRaw));

        // Add Stats
        String statFormatRaw = configLoader.getDisplayStatFormat();
        for (Map.Entry<String, ReforgeStatData> entry : modData.stats().entrySet()) {
            String statId = entry.getKey();
            ReforgeStatData statData = entry.getValue();

            String localizedStatStr = configLoader.getStatLangConfig().getFormattedStat(statId, statData);
            String fullStatLine = statFormatRaw.replace("<stat_format>", localizedStatStr);
            finalLore.add(MessageUtils.toLegacy(fullStatLine));
        }

        return finalLore;
    }
}
