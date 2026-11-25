package com.sdlinkrolegate.config;

import net.minecraft.world.BossEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void generatesDefaultsAndMapsSolidBossbarOverlay() throws Exception {
        ModConfig config = new ModConfig(tempDir, LoggerFactory.getLogger("test"));
        config.load();

        Path tomlPath = tempDir.resolve("sdlinkrolegate.toml");
        assertTrue(Files.exists(tomlPath));
        String content = Files.readString(tomlPath);
        assertTrue(content.contains("requiredRoleId"));
        assertTrue(content.contains("[bossbar]"));
        assertTrue(content.contains("[luckperms]"));
        ModConfig.ConfigData data = config.get();
        assertEquals("", data.requiredRoleId());
        assertEquals("To interact with the server, please join our Discord!", data.bossbar().text());
        assertEquals(BossEvent.BossBarColor.RED, data.bossbar().color());
        assertEquals(BossEvent.BossBarOverlay.PROGRESS, data.bossbar().style());
        assertTrue(data.modal().enabled());
        assertTrue(data.luckPerms().enabled());
        assertEquals("unverified", data.luckPerms().unverifiedGroup());
    }

    @Test
    void reloadsCustomValuesAndFallsBackOnInvalidEnums() throws Exception {
        Path tomlPath = tempDir.resolve("sdlinkrolegate.toml");
        Files.writeString(tomlPath, """
                requiredRoleId = "123456789012345678"
                discordInviteLink = "https://discord.gg/test"

                [bossbar]
                enabled = false
                id = "custom:gate"
                text = "Verify at {invite}"
                color = "NOT_A_COLOR"
                style = "NOTCHED_10"

                [modal]
                enabled = false

                [luckperms]
                enabled = true
                unverifiedGroup = "guest"
                """);

        ModConfig config = new ModConfig(tempDir, LoggerFactory.getLogger("test"));
        config.load();

        ModConfig.ConfigData data = config.get();
        assertEquals("123456789012345678", data.requiredRoleId());
        assertEquals("https://discord.gg/test", data.discordInviteLink());
        assertFalse(data.bossbar().enabled());
        assertEquals("custom:gate", data.bossbar().id());
        assertEquals(BossEvent.BossBarColor.RED, data.bossbar().color());
        assertEquals(BossEvent.BossBarOverlay.NOTCHED_10, data.bossbar().style());
        assertFalse(data.modal().enabled());
        assertEquals("guest", data.luckPerms().unverifiedGroup());
        // Missing keys get filled in cleanly on reload.
        assertEquals("&cYou cannot break blocks! Join our Discord to get access.", data.messages().blockBreakDenied());
    }
}
