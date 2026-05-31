package com.Teenkung.devReforgePlus.Command;

import com.Teenkung.devReforgePlus.Config.CatalystConfig.CatalystAction;
import com.Teenkung.devReforgePlus.Config.CatalystConfig.CatalystData;
import com.Teenkung.devReforgePlus.Config.ModifierConfig.ModifierData;
import com.Teenkung.devReforgePlus.DevReforgePlus;
import com.Teenkung.devReforgePlus.GUI.ReforgeGUI.ReforgeGUIManager;
import com.Teenkung.devReforgePlus.GUI.ReforgeGUI.ReforgeItemEngine;
import com.Teenkung.devReforgePlus.Utils.MessageUtils;
import com.Teenkung.devReforgePlus.Utils.ReforgeUpdater;
import com.Teenkung.devReforgePlus.Utils.StatResolver;
import com.Teenkung.devReforgePlus.Utils.TrackerPayload;
import com.Teenkung.devReforgePlus.Utils.TrackerUtils;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import org.bukkit.Bukkit;
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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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

        // ── listgroups [group_name] ────────────────────────────────────────────
        if (sub.equals("listgroups")) {
            if (!sender.hasPermission("devreforge.admin")) { sender.sendMessage(noPerms()); return true; }
            var modCfg = plugin.getConfigLoader().getModifierConfig();
            if (args.length >= 2) {
                // Show all modifiers in the specified group
                String groupName = args[1].toLowerCase();
                List<ModifierData> inGroup = modCfg.getModifiersByGroup(groupName);
                if (inGroup.isEmpty()) {
                    sender.sendMessage(MessageUtils.mm("&eNo modifiers found in group &f" + groupName + "&e."));
                } else {
                    sender.sendMessage(MessageUtils.mm("&aModifiers in group &f" + groupName + " &a(" + inGroup.size() + "):"));
                    for (ModifierData m : inGroup) {
                        String groupsStr = String.join(", ", m.groups());
                        sender.sendMessage(MessageUtils.mm("&8- &f" + m.id() + " &7(" + m.display() + "&7) &8[&7groups: " + groupsStr + "&8]"));
                    }
                }
            } else {
                // Show all groups with modifier counts
                Set<String> groups = modCfg.getAllGroups();
                if (groups.isEmpty()) {
                    sender.sendMessage(MessageUtils.mm("&eNo groups defined. Add a &fgroup: &eor &fgroups: &efield to modifier files."));
                } else {
                    sender.sendMessage(MessageUtils.mm("&aAll modifier groups (" + groups.size() + "):"));
                    groups.stream().sorted().forEach(g -> {
                        int count = modCfg.getModifiersByGroup(g).size();
                        sender.sendMessage(MessageUtils.mm("&8- &f" + g + " &7(" + count + " modifier" + (count == 1 ? "" : "s") + ")"));
                    });
                    sender.sendMessage(MessageUtils.mm("&7Use &f/devreforge listgroups <group> &7to see modifiers in a group."));
                }
            }
            return true;
        }

        // ── listcatalysts [catalyst_id] ───────────────────────────────────────
        if (sub.equals("listcatalysts")) {
            if (!sender.hasPermission("devreforge.admin")) { sender.sendMessage(noPerms()); return true; }
            var catalystCfg = plugin.getConfigLoader().getCatalystConfig();
            Map<String, CatalystData> catalysts = catalystCfg.getCatalysts();
            if (args.length >= 2) {
                // Show details of a specific catalyst
                String catalystId = args[1].toLowerCase();
                CatalystData cat = catalystCfg.getById(catalystId);
                if (cat == null) {
                    sender.sendMessage(MessageUtils.mm("&cUnknown catalyst: &f" + catalystId));
                } else {
                    sender.sendMessage(MessageUtils.mm("&aCatalyst: &f" + cat.id() + " &7(" + cat.displayName() + "&7)"));
                    sender.sendMessage(MessageUtils.mm("&7Detection: MMOItem &f" + cat.mmoItemType() + ":" + cat.mmoItemId()));
                    sender.sendMessage(MessageUtils.mm("&7Actions:"));
                    for (CatalystAction action : cat.actions()) {
                        String detail = switch (action.type()) {
                            case RESET_COUNT -> "Reset reforge count to 0";
                            case REDUCE_COUNT -> "Reduce reforge count by " + action.amount();
                            case REMOVE_MODIFIER -> "Remove all reforge modifiers";
                            case GUARANTEED_GROUP -> "Guarantee group(s): " + String.join(", ", action.groups());
                            case GUARANTEED_MODIFIER -> "Guarantee modifier(s): " + String.join(", ", action.modifierIds());
                        };
                        sender.sendMessage(MessageUtils.mm("&8  - &f" + action.type().name() + " &7- " + detail));
                    }
                }
            } else {
                // List all catalysts
                if (catalysts.isEmpty()) {
                    sender.sendMessage(MessageUtils.mm("&eNo catalysts loaded. Add yml files to the &fcatalysts/ &efolder."));
                } else {
                    sender.sendMessage(MessageUtils.mm("&aLoaded catalysts (" + catalysts.size() + "):"));
                    catalysts.values().stream()
                            .sorted((a, b) -> a.id().compareToIgnoreCase(b.id()))
                            .forEach(cat -> {
                                String actionSummary = cat.actions().stream()
                                        .map(a -> a.type().name())
                                        .collect(Collectors.joining(", "));
                                sender.sendMessage(MessageUtils.mm(
                                        "&8- &f" + cat.id() + " &7(" + cat.displayName() + "&7) &8[&7" + cat.mmoItemType() + ":" + cat.mmoItemId() + "&8] &8→ &7" + actionSummary));
                            });
                    sender.sendMessage(MessageUtils.mm("&7Use &f/devreforge listcatalysts <id> &7for full details."));
                }
            }
            return true;
        }

        // Admin sub-commands below require a player sender
        if (!(sender instanceof Player player)) {
            sender.sendMessage(lang("PlayersOnly", "&cThis command can only be run by a player."));
            return true;
        }
        if (!player.hasPermission("devreforge.admin")) { player.sendMessage(noPerms()); return true; }

        // ── updateall ─────────────────────────────────────────────────────────
        if (sub.equals("updateall")) {
            int totalUpdated = 0;
            int playerCount = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                int updated = ReforgeUpdater.updatePlayerInventory(p);
                if (updated > 0) {
                    totalUpdated += updated;
                    playerCount++;
                }
            }
            sender.sendMessage(lang("UpdateAllSuccess",
                    "&aUpdated &f<count>&a reforged item(s) across &f<players>&a online player(s).",
                    "<count>", String.valueOf(totalUpdated),
                    "<players>", String.valueOf(playerCount)));
            return true;
        }

        // ── updateitem ────────────────────────────────────────────────────────
        if (sub.equals("updateitem")) {
            ItemStack heldItem = player.getInventory().getItemInMainHand();
            if (heldItem.getType().isAir()) {
                player.sendMessage(lang("HoldItem", "&cYou must be holding an item."));
                return true;
            }
            boolean updated = ReforgeUpdater.updateItemIfNeeded(heldItem);
            if (updated) {
                player.getInventory().setItemInMainHand(heldItem);
                player.sendMessage(lang("UpdateItemSuccess", "&aItem updated to match current modifier config."));
            } else {
                player.sendMessage(lang("UpdateItemNoChange", "&eItem is already up to date or has no reforge modifier."));
            }
            return true;
        }

        // Sub-commands below additionally require the player to be holding an MMOItem
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
                        new StringData(TrackerUtils.encode(payload.modifier(), 0, payload.appliedStats(), payload.configHash()))
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

        player.sendMessage(lang("UnknownSubCommand", "&cUnknown sub-command. Use: &freload&c, &fremove&c, &freset&c, &freforge <id>&c, &fupdateall&c, &fupdateitem&c, &flistgroups&c, &flistcatalysts"));
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
