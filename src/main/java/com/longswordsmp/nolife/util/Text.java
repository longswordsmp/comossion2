package com.longswordsmp.nolife.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Turns configuration strings into Adventure {@link Component}s.
 *
 * <p>Strings use the familiar legacy colour codes with {@code &} (e.g.
 * {@code &c}, {@code &l}) plus hex codes in the form {@code &#ff0000}.</p>
 */
public final class Text {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private Text() {
    }

    /** Parse a colour-coded string into a component (never {@code null}). */
    public static Component parse(String input) {
        return input == null ? Component.empty() : LEGACY.deserialize(input);
    }

    /**
     * Parse a string for use as an item name or lore line. Item display text is
     * italic by default in Minecraft, so we explicitly disable that.
     */
    public static Component item(String input) {
        return parse(input).decoration(TextDecoration.ITALIC, false);
    }
}
