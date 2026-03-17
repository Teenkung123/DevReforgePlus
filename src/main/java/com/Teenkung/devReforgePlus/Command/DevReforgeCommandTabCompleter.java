package com.Teenkung.devReforgePlus.Command;

import com.Teenkung.devReforgePlus.DevReforgePlus;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DevReforgeCommandTabCompleter implements TabCompleter {

    private final DevReforgePlus plugin;

    public DevReforgeCommandTabCompleter(DevReforgePlus plugin) {
        this.plugin = plugin;
    }

    private static final List<String> ADMIN_SUBS = List.of("reload", "remove", "reset", "reforge");

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!sender.hasPermission("devreforge.admin")) return List.of();

        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            String typed = args[0].toLowerCase();
            for (String sub : ADMIN_SUBS) {
                if (sub.startsWith(typed)) out.add(sub);
            }
            return out;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("reforge")) {
            String typed = args[1].toLowerCase();
            List<String> out = new ArrayList<>();
            plugin.getConfigLoader().getModifierConfig().getModifiers().keySet().stream()
                    .filter(id -> id.toLowerCase().startsWith(typed))
                    .forEach(out::add);
            return out;
        }

        return List.of();
    }
}
