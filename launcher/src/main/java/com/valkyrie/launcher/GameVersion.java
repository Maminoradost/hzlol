package com.valkyrie.launcher;

import java.net.URI;

record GameVersion(String id, String type, URI metadataUri, String metadataSha1, boolean valkyrie) {
    static final GameVersion VALKYRIE = new GameVersion(
        GameInstallationService.MINECRAFT_VERSION,
        "release",
        GameInstallationService.VERSION_URL,
        GameInstallationService.VERSION_SHA1,
        true
    );

    String profileId() {
        return valkyrie ? GameInstallationService.FORGE_PROFILE : id;
    }

    String displayName() {
        return valkyrie ? "★ Valkyrie Client " + id + "  ·  Recommended" : "Minecraft " + id;
    }

    String instanceId() {
        return valkyrie ? "valkyrie-" + id : "vanilla-" + id.replaceAll("[^A-Za-z0-9._-]", "-");
    }

    @Override
    public String toString() {
        return displayName();
    }
}
