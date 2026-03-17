package com.Teenkung.devReforgePlus.Command;

import com.Teenkung.devReforgePlus.Config.ModifierConfig.ModifierData;
import com.Teenkung.devReforgePlus.DevReforgePlus;
import com.Teenkung.devReforgePlus.GUI.ReforgeGUI.ReforgeGUIManager;
import com.Teenkung.devReforgePlus.GUI.ReforgeGUI.ReforgeItemEngine;
import com.Teenkung.devReforgePlus.Utils.MessageUtils;
import com.Teenkung.devReforgePlus.Utils.StatResolver;
import com.Teenkung.devReforgePlus.Utils.TrackerPayload;
import com.Teenkung.devReforgePlus.Utils.TrackerUtils;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.stat.data.StringData;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public class DevReforgeCommandExecutor implements CommandExecutor {

    private final DevReforgePlus plugin;

    public DevReforgeCommandExecutor(DevReforgePlus plugin) {
        this.plugin = plugin;
        PluginCommand command = plugin.getCommand("devreforge");
        if (command == null) {
            plugin.getLogger().severe("Failed to register command!, This is not your fault, please report this to the plugin author.");
            return;
        }
        command.setExecutor(this);
        command.setTabCompleter(new DevReforgeCommandTabCompleter(plugin));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {

        if (args.length == 0) {
            if (sender instanceof Player player) {
                if (player.hasPermission("devreforge.use")) {
                    plugin.getReforgeGUI().open(player);
                } else {
                    player.sendMessage(lang("OpenGUINoPermission", "&cYou don't have permission to use this command."));
                }
            } else {
                sender.sendMessage(lang("OpenGUIPlayersOnly", "&cOnly players can open the reforge GUI."));
            }
            return true;
        }

        String sub = args[0].toLowerCase();

        // ── reload ────────────────────────────────────────────────────────────
        if (sub.equals("reload")) {
            if (!sender.hasPermission("devreforge.admin")) { sender.sendMessage(noPerms()); return true; }
            plugin.getConfigLoader().loadConfig();
            plugin.getReforgeGUI().loadReforgeMenu();
            StatResolver.clearCache();
            ReforgeGUIManager.closeAll();
            sender.sendMessage(lang("ReloadSuccess", "&a[DevReforgePlus] Configurations reloaded successfully."));
            return true;
        }

        // Admin sub-commands below require a player (target), who must be holding an MMOItem
        if (!(sender instanceof Player player)) {
            sender.sendMessage(lang("PlayersOnly", "&cThis command can only be run by a player."));
            return true;
        }
        if (!player.hasPermission("devreforge.admin")) { player.sendMessage(noPerms()); return true; }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            player.sendMessage(lang("HoldItem", "&cYou must be holding an item."));
            return true;
        }

        // ── remove ────────────────────────────────────────────────────────────
        if (sub.equals("remove")) {
            LiveMMOItem liveItem = new LiveMMOItem(held);
            String rawTracker = liveItem.getNBT().getString("MMOITEMS_REFORGE_TRACKER");
            if (rawTracker == null || rawTracker.isEmpty()) {
                player.sendMessage(lang("NoReforgeModifier", "&cThis item has no reforge modifier."));
                return true;
            }

            ModifierData old = ReforgeItemEngine.readOldModifier(liveItem);
            if (old != null) {
                ReforgeItemEngine.clearModifierEffects(liveItem, old, Objects.requireNonNull(net.Indyuce.mmoitems.api.Type.get(held)).getId());
            } else {
                ReforgeItemEngine.clearNamePrefix(liveItem);
            }

            liveItem.removeData(DevReforgePlus.getInstance().getTrackerStat());
            player.getInventory().setItemInMainHand(liveItem.newBuilder().build());
            player.sendMessage(lang("RemoveSuccess", "&aReforge modifier removed from the item."));
            return true;
        }

        // ── reset ─────────────────────────────────────────────────────────────
        if (sub.equals("reset")) {
            LiveMMOItem liveItem = new LiveMMOItem(held);
            String rawTracker = liveItem.getNBT().getString("MMOITEMS_REFORGE_TRACKER");
            if (rawTracker == null || rawTracker.isEmpty()) {
                player.sendMessage(lang("NoReforgeModifier", "&cThis item has no reforge modifier."));
                return true;
            }
            try {
                TrackerPayload payload = TrackerUtils.decode(rawTracker);
                liveItem.setData(
                        DevReforgePlus.getInstance().getTrackerStat(),
                        new StringData(TrackerUtils.encode(payload.modifier(), 0, payload.appliedStats()))
                );
                player.getInventory().setItemInMainHand(liveItem.newBuilder().build());
                player.sendMessage(lang("ResetSuccess", "&aReforge attempt count reset to 0."));
            } catch (Exception e) {
                player.sendMessage(lang("ResetFailed", "&cFailed to reset attempt count: &f<error>",
                        "<error>", e.getMessage()));
            }
            return true;
        }

        // ── reforge <reforge_id> ──────────────────────────────────────────────
        if (sub.equals("reforge")) {
            if (args.length < 2) {
                player.sendMessage(lang("ReforgeUsage", "&cUsage: /devreforge reforge <reforge_id>"));
                return true;
            }
            String modifierId = args[1];
            ModifierData targetModifier = plugin.getConfigLoader().getModifierConfig().getModifier(modifierId);
            if (targetModifier == null) {
                player.sendMessage(lang("UnknownModifier", "&cUnknown modifier: &f<modifier_id>",
                        "<modifier_id>", modifierId));
                return true;
            }

            net.Indyuce.mmoitems.api.Type mmoType = net.Indyuce.mmoitems.api.Type.get(held);
            if (mmoType == null) {
                player.sendMessage(lang("NotMMOItem", "&cThis item is not an MMOItem."));
                return true;
            }
            String typeId = mmoType.getId();

            // Snapshot original PDC so external plugins (e.g. EcoEnchants) survive the rebuild
            org.bukkit.inventory.meta.ItemMeta originalMeta = held.getItemMeta();

            LiveMMOItem liveItem = new LiveMMOItem(held);
            ModifierData old = ReforgeItemEngine.readOldModifier(liveItem);
            if (old != null) ReforgeItemEngine.clearModifierEffects(liveItem, old, typeId);

            List<String> appliedStats = ReforgeItemEngine.applyModifierEffects(liveItem, targetModifier, typeId);
            ReforgeItemEngine.updateTrackerModifier(liveItem, targetModifier.id(), appliedStats);

            org.bukkit.inventory.ItemStack result = liveItem.newBuilder().build();

            // Restore external PDC data (EcoEnchants, etc.) that the MMOItems rebuild discards
            if (result != null && originalMeta != null) {
                org.bukkit.inventory.meta.ItemMeta resultMeta = result.getItemMeta();
                if (resultMeta != null) {
                    originalMeta.getPersistentDataContainer()
                            .copyTo(resultMeta.getPersistentDataContainer(), false);
                    result.setItemMeta(resultMeta);
                }
            }

            player.getInventory().setItemInMainHand(result);
            player.sendMessage(lang("ReforgeSuccess", "&aApplied reforge modifier &f<modifier_id> &ato the item.",
                    "<modifier_id>", modifierId));
            return true;
        }

        player.sendMessage(lang("UnknownSubCommand", "&cUnknown sub-command. Use: &freload&c, &fremove&c, &freset&c, &freforge <id>"));
        return true;
    }

    /** Reads a message from config Messages section and parses it as MiniMessage. */
    private Component lang(String key, String defaultValue, String... replacements) {
        return MessageUtils.mm(plugin.getConfigLoader().getMessage(key, defaultValue, replacements));
    }

    private Component noPerms() {
        return lang("NoPermission", "&cYou don't have permission to do this.");
    }
}
