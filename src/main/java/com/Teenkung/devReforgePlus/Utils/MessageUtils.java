package com.Teenkung.devReforgePlus.Utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Central message/color utility for DevReforgePlus.
 *
 * <p>Supports both Legacy (&amp;-codes e.g. {@code &c}, {@code &2}) and
 * MiniMessage tags (e.g. {@code <red>}, {@code <#FF5500>}).
 * Legacy codes are translated first, then the result is parsed by MiniMessage,
 * so mixing both in a single string works correctly.</p>
 *
 * <h3>Usage examples</h3>
 * <pre>
 *   // To a Component (for sendMessage / lore lists):
 *   player.sendMessage(MessageUtils.parse("&aHello <gold>World!"));
 *
 *   // To a legacy §-string (for MMOItems lore lists):
 *   String loreLine = MessageUtils.toLegacy("&c<red>Some stat");
 *
 *   // Strip all formatting to plain text:
 *   String plain = MessageUtils.plain("&aHello World");
 * </pre>
 */
public final class MessageUtils {

    // Shared, thread-safe instances — MiniMessage and LEGACY are stateless
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private MessageUtils() {}

    // -------------------------------------------------------------------------
    // Core parse — supports both &-codes and <MiniMessage> tags
    // -------------------------------------------------------------------------

    /**
     * Parses a string that may contain any mix of {@code &}-codes and MiniMessage
     * tags into an Adventure {@link Component}.
     *
     * <p>Processing order:
     * <ol>
     *   <li>Translate {@code &} color codes to the internal legacy serialization form.</li>
     *   <li>Deserialize the result via {@link MiniMessage}.</li>
     * </ol>
     */
    public static Component parse(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        // Translate & codes to MiniMessage tags first, then let MiniMessage handle <tags>
        return MM.deserialize(translateLegacy(text));
    }

    /**
     * Translates {@code &x} legacy colour/format codes to their MiniMessage
     * {@code <tag>} equivalents so that the result can be passed directly to
     * {@link MiniMessage#deserialize(String)}.
     * <ul>
     *   <li>{@code &&} is preserved as a literal {@code &} character.</li>
     *   <li>Unknown {@code &X} sequences are left unchanged.</li>
     * </ul>
     */
    static String translateLegacy(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length()) {
                String tag = legacyCharToTag(text.charAt(i + 1));
                if (tag != null) {
                    sb.append(tag);
                    i++; // consume the code character
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static String legacyCharToTag(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a' -> "<green>";
            case 'b' -> "<aqua>";
            case 'c' -> "<red>";
            case 'd' -> "<light_purple>";
            case 'e' -> "<yellow>";
            case 'f' -> "<white>";
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset>";
            case '&' -> "&"; // escaped ampersand → literal &
            default  -> null; // unknown code — leave the & in place
        };
    }

    /**
     * Convenience shorthand — identical to {@link #parse(String)} but easier to
     * type at call sites where you want a {@link Component} directly.
     */
    public static Component mm(String text) {
        return parse(text);
    }

    /**
     * Parses a string containing any mix of {@code &}-codes and MiniMessage tags,
     * then serializes the result back to a {@code §}-prefixed legacy string.
     *
     * <p>Intended for APIs that still expect a legacy string (e.g. older MMOItems
     * lore injection paths).</p>
     */
    public static String toLegacy(String text) {
        return LEGACY.serialize(parse(text));
    }

    /**
     * Parses a string and strips all formatting, returning plain text.
     */
    public static String plain(String text) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(parse(text));
    }

    // -------------------------------------------------------------------------
    // Low-level access for callers that need the raw serializers
    // -------------------------------------------------------------------------

    /** Returns the shared {@link MiniMessage} instance. */
    public static MiniMessage miniMessage() {
        return MM;
    }

    /** Returns the shared legacy-ampersand {@link LegacyComponentSerializer}. */
    public static LegacyComponentSerializer legacy() {
        return LEGACY;
    }
}
