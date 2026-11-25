package com.sdlinkrolegate.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Utility helpers for formatting mod messages and translating Minecraft color codes.
 */
public final class TextUtil {

    private TextUtil() {
    }

    /**
     * Replaces supported placeholders and converts '&' formatting codes to '§', returning a text component.
     */
    public static MutableComponent format(String template, String discordInviteLink) {
        String safeTemplate = template == null ? "" : template;
        String safeInvite = discordInviteLink == null ? "" : discordInviteLink;
        String resolved = safeTemplate.replace("{invite}", safeInvite);
        return Component.literal(translateAlternateColorCodes('&', resolved));
    }

    /**
     * Translates alternate color/formatting codes (e.g. '&c', '&l', '&r') to Minecraft section symbols ('§').
     */
    public static String translateAlternateColorCodes(char altChar, String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == altChar && isFormattingChar(chars[i + 1])) {
                chars[i] = '\u00A7';
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }

    private static boolean isFormattingChar(char c) {
        char lower = Character.toLowerCase(c);
        return (lower >= '0' && lower <= '9')
                || (lower >= 'a' && lower <= 'f')
                || (lower >= 'k' && lower <= 'o')
                || lower == 'r';
    }
}
