package com.sdlinkrolegate.util;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextUtilTest {

    @Test
    void replacesInvitePlaceholderAndTranslatesColorCodes() {
        Component result = TextUtil.format(
                "&cJoin &lDiscord&r at {invite}!",
                "https://discord.gg/example"
        );
        assertEquals(
                "\u00A7cJoin \u00A7lDiscord\u00A7r at https://discord.gg/example!",
                result.getString()
        );
    }

    @Test
    void leavesNonColorAmpersandsUntouched() {
        String translated = TextUtil.translateAlternateColorCodes('&', "Rules & Info &aOK &Z");
        assertEquals("Rules & Info \u00A7aOK &Z", translated);
    }
}
