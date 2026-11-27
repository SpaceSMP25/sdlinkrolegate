package com.sdlinkrolegate.discord;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SDLinkRoleCheckerTest {

    @Test
    void parsesSingleMentionMultipleAndBlankRoleConfigs() {
        assertEquals(List.of("123456789012345678"), SDLinkRoleChecker.parseRoleTokens(" 123456789012345678 "));
        assertEquals(List.of("123456789012345678"), SDLinkRoleChecker.parseRoleTokens("<@&123456789012345678>"));
        assertEquals(List.of("123456789012345678"), SDLinkRoleChecker.parseRoleTokens("\"<@&123456789012345678>\""));
        assertEquals(List.of("111", "Whitelisted", "222"), SDLinkRoleChecker.parseRoleTokens("111, Whitelisted, <@&222>"));

        assertFalse(SDLinkRoleChecker.hasConfiguredRole(null));
        assertFalse(SDLinkRoleChecker.hasConfiguredRole("   "));
        assertFalse(SDLinkRoleChecker.hasConfiguredRole(" , <@&> , "));
        assertTrue(SDLinkRoleChecker.hasConfiguredRole("Whitelisted"));
    }

    @Test
    void matchesRoleByIdOrCaseInsensitiveName() {
        List<String> byId = SDLinkRoleChecker.parseRoleTokens("<@&999888777>");
        assertTrue(SDLinkRoleChecker.matchesRole("999888777", "Whitelisted", byId));
        assertFalse(SDLinkRoleChecker.matchesRole("111222333", "Member", byId));

        List<String> byName = SDLinkRoleChecker.parseRoleTokens("Whitelisted, VIP");
        assertTrue(SDLinkRoleChecker.matchesRole("444555666", "whitelisted", byName));
        assertTrue(SDLinkRoleChecker.matchesRole("777888999", "VIP", byName));
        assertFalse(SDLinkRoleChecker.matchesRole("111222333", "Guest", byName));
    }
}
