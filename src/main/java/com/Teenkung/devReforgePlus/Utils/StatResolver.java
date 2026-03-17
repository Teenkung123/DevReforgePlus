package com.Teenkung.devReforgePlus.Utils;

import net.Indyuce.mmoitems.stat.type.ItemStat;
import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves MMOItems stat ID strings to their {@link ItemStat} instances.
 *
 * <p>Uses the MMOItems {@code StatManager} API at runtime via a single
 * Bukkit plugin instance lookup. We can't reference {@code MMOItems.plugin}
 * directly in the API-only compile scope, so we grab the plugin instance
 * through Bukkit and reflectively call {@code getStats().get(id)} once per
 * unknown stat ID — results are cached for O(1) subsequent lookups.</p>
 */
public class StatResolver {

    private static final Map<String, ItemStat<?, ?>> CACHE = new HashMap<>();

    private StatResolver() {}

    /**
     * Returns the {@link ItemStat} for the given stat ID, or {@code null}
     * if no such stat is registered with MMOItems.
     */
    public static ItemStat<?, ?> resolve(String statId) {
        if (statId == null) return null;
        String key = statId.toUpperCase();
        return CACHE.computeIfAbsent(key, StatResolver::lookupViaBukkit);
    }

    /** Clears the cache — call on plugin reload so stale entries are evicted. */
    public static void clearCache() {
        CACHE.clear();
    }

    /**
     * Fetches the stat using the MMOItems plugin instance obtained through
     * Bukkit's plugin manager. Avoids any compile-time dependency on the
     * non-API {@code MMOItems} class, making this safe with the API-only jar.
     */
    private static ItemStat<?, ?> lookupViaBukkit(String statId) {
        try {
            Object mmoItemsPlugin = Bukkit.getPluginManager().getPlugin("MMOItems");
            if (mmoItemsPlugin == null) return null;

            // StatManager statManager = mmoItemsPlugin.getStats();
            Method getStats = mmoItemsPlugin.getClass().getMethod("getStats");
            Object statManager = getStats.invoke(mmoItemsPlugin);

            // ItemStat<?, ?> stat = statManager.get(statId);
            Method get = statManager.getClass().getMethod("get", String.class);
            Object result = get.invoke(statManager, statId);

            return (result instanceof ItemStat<?, ?>) ? (ItemStat<?, ?>) result : null;
        } catch (Exception e) {
            return null;
        }
    }
}
