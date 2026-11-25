package com.sdlinkrolegate.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.file.FileNotFoundAction;
import com.electronwill.nightconfig.toml.TomlFormat;
import net.minecraft.resources.Identifier;
import net.minecraft.world.BossEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Manages loading, defaulting, and hot-reloading the TOML configuration file.
 */
public final class ModConfig {

    private final Path configPath;
    private final Logger logger;
    private volatile ConfigData data = ConfigData.defaults();

    public ModConfig(Path configDir, Logger logger) {
        this.configPath = configDir.resolve("sdlinkrolegate.toml");
        this.logger = logger;
    }

    /**
     * Returns the current immutable configuration snapshot.
     */
    public ConfigData get() {
        return data;
    }

    /**
     * Loads the TOML config from disk, writing any missing defaults and comments.
     */
    public synchronized void load() {
        try {
            Files.createDirectories(configPath.getParent());
        } catch (IOException e) {
            logger.error("Failed to create config directory for {}", configPath, e);
            return;
        }

        try (CommentedFileConfig fileConfig = CommentedFileConfig.builder(configPath, TomlFormat.instance())
                .sync()
                .preserveInsertionOrder()
                .onFileNotFound(FileNotFoundAction.CREATE_EMPTY)
                .build()) {
            fileConfig.load();

            ConfigData defaults = ConfigData.defaults();

            String requiredRoleId = ensureString(
                    fileConfig,
                    "requiredRoleId",
                    defaults.requiredRoleId(),
                    "Discord Role ID required for players to build, break blocks, and open containers.\n"
                            + "Players must also be linked via Simple Discord Link (/discordverify)."
            );
            String discordInviteLink = ensureString(
                    fileConfig,
                    "discordInviteLink",
                    defaults.discordInviteLink(),
                    "Optional Discord invite link used for the {invite} placeholder and the login modal's Join Discord button."
            );

            boolean bossbarEnabled = ensureBoolean(
                    fileConfig,
                    "bossbar.enabled",
                    defaults.bossbar().enabled(),
                    "Whether to show a persistent bossbar to unverified players."
            );
            String bossbarId = ensureString(
                    fileConfig,
                    "bossbar.id",
                    defaults.bossbar().id(),
                    "Unique identifier for the bossbar (used to derive its deterministic bossbar UUID)."
            );
            String bossbarText = ensureString(
                    fileConfig,
                    "bossbar.text",
                    defaults.bossbar().text(),
                    "Text shown on the bossbar. Supports '&' color codes and {invite}."
            );
            String bossbarColorRaw = ensureString(
                    fileConfig,
                    "bossbar.color",
                    defaults.bossbar().colorName(),
                    "Bossbar color: RED, YELLOW, GREEN, BLUE, PURPLE, PINK, WHITE."
            );
            String bossbarStyleRaw = ensureString(
                    fileConfig,
                    "bossbar.style",
                    defaults.bossbar().styleName(),
                    "Bossbar style: SOLID (or PROGRESS), NOTCHED_6, NOTCHED_10, NOTCHED_12, NOTCHED_20."
            );

            String blockBreakDenied = ensureString(
                    fileConfig,
                    "messages.blockBreakDenied",
                    defaults.messages().blockBreakDenied(),
                    "Chat message sent whenever an unverified player tries to break a block. Supports '&' codes and {invite}."
            );
            String blockPlaceDenied = ensureString(
                    fileConfig,
                    "messages.blockPlaceDenied",
                    defaults.messages().blockPlaceDenied(),
                    "Chat message sent whenever an unverified player tries to place a block. Supports '&' codes and {invite}."
            );
            String interactionDenied = ensureString(
                    fileConfig,
                    "messages.interactionDenied",
                    defaults.messages().interactionDenied(),
                    "Chat message sent whenever an unverified player tries to open a chest or interact with blocks/entities. Supports '&' codes and {invite}."
            );

            boolean modalEnabled = ensureBoolean(
                    fileConfig,
                    "modal.enabled",
                    defaults.modal().enabled(),
                    "Whether to show the Minecraft 26.3 native dialog modal when an unverified player joins (or loses access)."
            );
            String modalTitle = ensureString(
                    fileConfig,
                    "modal.title",
                    defaults.modal().title(),
                    "Title displayed at the top of the login modal. Supports '&' codes and {invite}."
            );
            String modalBody = ensureString(
                    fileConfig,
                    "modal.body",
                    defaults.modal().body(),
                    "Body text inside the login modal. Supports '&' codes and {invite}."
            );
            String inviteButtonText = ensureString(
                    fileConfig,
                    "modal.inviteButtonText",
                    defaults.modal().inviteButtonText(),
                    "Button label for opening the configured discordInviteLink (only shown when discordInviteLink is a valid URL)."
            );
            String closeButtonText = ensureString(
                    fileConfig,
                    "modal.closeButtonText",
                    defaults.modal().closeButtonText(),
                    "Button label for dismissing the login modal."
            );

            boolean blockItemPickup = ensureBoolean(
                    fileConfig,
                    "restrictions.blockItemPickup",
                    defaults.restrictions().blockItemPickup(),
                    "When true (default), unverified players cannot pick up items (or arrows/tridents) from the ground."
            );

            boolean effectsEnabled = ensureBoolean(
                    fileConfig,
                    "effects.enabled",
                    defaults.effects().enabled(),
                    "Whether to grant status effects to players while they are unverified (removed automatically once verified)."
            );
            boolean effectsShowParticles = ensureBoolean(
                    fileConfig,
                    "effects.showParticles",
                    defaults.effects().showParticles(),
                    "Whether the status effects applied to unverified players show particles (default false = no particles)."
            );
            boolean effectsShowIcon = ensureBoolean(
                    fileConfig,
                    "effects.showIcon",
                    defaults.effects().showIcon(),
                    "Whether the status effects applied to unverified players show an icon on the client HUD."
            );
            List<String> rawEffects = ensureStringList(
                    fileConfig,
                    "effects.list",
                    defaults.effects().rawEffects(),
                    "Status effects granted while unverified. Format: \"minecraft:effect_id\" or \"minecraft:effect_id:amplifier\" (0 = level I)."
            );

            boolean lpEnabled = ensureBoolean(
                    fileConfig,
                    "luckperms.enabled",
                    defaults.luckPerms().enabled(),
                    "Whether to assign/remove a LuckPerms group when LuckPerms is installed (ignored if LuckPerms is absent)."
            );
            String lpGroup = ensureString(
                    fileConfig,
                    "luckperms.unverifiedGroup",
                    defaults.luckPerms().unverifiedGroup(),
                    "LuckPerms group assigned to unverified players and removed once they verify with the required role."
            );

            fileConfig.save();

            BossEvent.BossBarColor parsedColor = parseColor(bossbarColorRaw);
            BossEvent.BossBarOverlay parsedStyle = parseStyle(bossbarStyleRaw);
            List<EffectSpec> parsedEffects = parseEffectSpecs(rawEffects);

            this.data = new ConfigData(
                    requiredRoleId.trim(),
                    discordInviteLink.trim(),
                    new BossbarConfig(bossbarEnabled, bossbarId.trim(), bossbarText, bossbarColorRaw, bossbarStyleRaw, parsedColor, parsedStyle),
                    new MessagesConfig(blockBreakDenied, blockPlaceDenied, interactionDenied),
                    new ModalConfig(modalEnabled, modalTitle, modalBody, inviteButtonText, closeButtonText),
                    new RestrictionsConfig(blockItemPickup),
                    new EffectsConfig(effectsEnabled, effectsShowParticles, effectsShowIcon, List.copyOf(rawEffects), parsedEffects),
                    new LuckPermsConfig(lpEnabled, lpGroup.trim())
            );
        } catch (Exception e) {
            logger.error("Failed to load config from {}", configPath, e);
        }
    }

    private static String ensureString(CommentedFileConfig cfg, String path, String defaultValue, String comment) {
        if (!cfg.contains(path)) {
            cfg.set(path, defaultValue);
        }
        cfg.setComment(path, comment);
        Object raw = cfg.get(path);
        return raw != null ? String.valueOf(raw) : defaultValue;
    }

    private static boolean ensureBoolean(CommentedFileConfig cfg, String path, boolean defaultValue, String comment) {
        if (!cfg.contains(path)) {
            cfg.set(path, defaultValue);
        }
        cfg.setComment(path, comment);
        Object raw = cfg.get(path);
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw != null) {
            return Boolean.parseBoolean(String.valueOf(raw));
        }
        return defaultValue;
    }

    private static List<String> ensureStringList(CommentedFileConfig cfg, String path, List<String> defaultValue, String comment) {
        if (!cfg.contains(path)) {
            cfg.set(path, new ArrayList<>(defaultValue));
        }
        cfg.setComment(path, comment);
        Object raw = cfg.get(path);
        if (raw instanceof List<?> list) {
            List<String> result = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        if (raw != null) {
            return List.of(String.valueOf(raw));
        }
        return defaultValue;
    }

    private List<EffectSpec> parseEffectSpecs(List<String> rawEntries) {
        List<EffectSpec> parsed = new ArrayList<>();
        for (String entry : rawEntries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String trimmed = entry.trim().toLowerCase(Locale.ROOT);
            String effectPart = trimmed;
            int amplifier = 0;

            int lastColon = trimmed.lastIndexOf(':');
            if (lastColon > 0) {
                String maybeAmplifier = trimmed.substring(lastColon + 1);
                if (isInteger(maybeAmplifier)) {
                    effectPart = trimmed.substring(0, lastColon);
                    try {
                        amplifier = Math.max(0, Math.min(255, Integer.parseInt(maybeAmplifier)));
                    } catch (NumberFormatException ignored) {
                        logger.warn("Invalid amplifier in effects.list entry '{}' in {}, skipping.", entry, configPath);
                        continue;
                    }
                }
            }

            String namespaced = effectPart.contains(":") ? effectPart : "minecraft:" + effectPart;
            Identifier id = Identifier.tryParse(namespaced);
            if (id == null) {
                logger.warn("Invalid effect identifier '{}' in {} (effects.list), skipping.", entry, configPath);
                continue;
            }
            parsed.add(new EffectSpec(id, amplifier));
        }
        return List.copyOf(parsed);
    }

    private static boolean isInteger(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (i == 0 && (c == '-' || c == '+')) {
                if (s.length() == 1) {
                    return false;
                }
                continue;
            }
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }

    private BossEvent.BossBarColor parseColor(String raw) {
        String normalized = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        for (BossEvent.BossBarColor color : BossEvent.BossBarColor.values()) {
            if (color.name().equals(normalized)) {
                return color;
            }
        }
        logger.warn("Invalid bossbar.color '{}' in {}, defaulting to RED", raw, configPath);
        return BossEvent.BossBarColor.RED;
    }

    private BossEvent.BossBarOverlay parseStyle(String raw) {
        String normalized = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("SOLID") || normalized.equals("PROGRESS")) {
            return BossEvent.BossBarOverlay.PROGRESS;
        }
        for (BossEvent.BossBarOverlay overlay : BossEvent.BossBarOverlay.values()) {
            if (overlay.name().equals(normalized)) {
                return overlay;
            }
        }
        logger.warn("Invalid bossbar.style '{}' in {}, defaulting to SOLID (PROGRESS)", raw, configPath);
        return BossEvent.BossBarOverlay.PROGRESS;
    }

    public record ConfigData(
            String requiredRoleId,
            String discordInviteLink,
            BossbarConfig bossbar,
            MessagesConfig messages,
            ModalConfig modal,
            RestrictionsConfig restrictions,
            EffectsConfig effects,
            LuckPermsConfig luckPerms
    ) {
        private static final List<String> DEFAULT_EFFECT_NAMES = List.of(
                "minecraft:saturation",
                "minecraft:invisibility"
        );
        private static final List<EffectSpec> DEFAULT_EFFECT_SPECS = List.of(
                new EffectSpec(Identifier.parse("minecraft:saturation"), 0),
                new EffectSpec(Identifier.parse("minecraft:invisibility"), 0)
        );

        public static ConfigData defaults() {
            return new ConfigData(
                    "",
                    "",
                    new BossbarConfig(
                            true,
                            "sdlinkrolegate:unverified",
                            "To interact with the server, please join our Discord!",
                            "RED",
                            "SOLID",
                            BossEvent.BossBarColor.RED,
                            BossEvent.BossBarOverlay.PROGRESS
                    ),
                    new MessagesConfig(
                            "&cYou cannot break blocks! Join our Discord to get access.",
                            "&cYou cannot place blocks! Join our Discord to get access.",
                            "&cYou cannot interact with this! Join our Discord to get access."
                    ),
                    new ModalConfig(
                            true,
                            "Discord Verification Required",
                            "Join our Discord server and verify your account to interact with the server.",
                            "Join Discord",
                            "Close"
                    ),
                    new RestrictionsConfig(true),
                    new EffectsConfig(true, false, false, DEFAULT_EFFECT_NAMES, DEFAULT_EFFECT_SPECS),
                    new LuckPermsConfig(true, "unverified")
            );
        }
    }

    public record BossbarConfig(
            boolean enabled,
            String id,
            String text,
            String colorName,
            String styleName,
            BossEvent.BossBarColor color,
            BossEvent.BossBarOverlay style
    ) {
    }

    public record MessagesConfig(
            String blockBreakDenied,
            String blockPlaceDenied,
            String interactionDenied
    ) {
    }

    public record ModalConfig(
            boolean enabled,
            String title,
            String body,
            String inviteButtonText,
            String closeButtonText
    ) {
    }

    public record RestrictionsConfig(
            boolean blockItemPickup
    ) {
    }

    public record EffectSpec(
            Identifier effectId,
            int amplifier
    ) {
    }

    public record EffectsConfig(
            boolean enabled,
            boolean showParticles,
            boolean showIcon,
            List<String> rawEffects,
            List<EffectSpec> effects
    ) {
    }

    public record LuckPermsConfig(
            boolean enabled,
            String unverifiedGroup
    ) {
    }
}
