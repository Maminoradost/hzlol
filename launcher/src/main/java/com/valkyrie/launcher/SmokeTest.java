package com.valkyrie.launcher;

import java.nio.file.Files;
import java.nio.file.Path;

public final class SmokeTest {
    private SmokeTest() {}

    public static void main(String[] args) throws Exception {
        Path game = Path.of(args[0]).toAbsolutePath();
        Path sourceJar = Path.of(args[1]).toAbsolutePath();
        if (!Files.isRegularFile(sourceJar)) throw new AssertionError("Missing test client JAR: " + sourceJar);

        LauncherConfig launchConfig = new LauncherConfig();
        launchConfig.nickname = "ValkyrieTest";
        launchConfig.contentDirectory = game;
        launchConfig.instanceDirectory = Files.createTempDirectory("valkyrie-launcher-smoke-instance-");
        launchConfig.forgeProfile = GameInstallationService.FORGE_PROFILE;
        launchConfig.selectedVersion = GameInstallationService.MINECRAFT_VERSION;
        var command = new MinecraftLauncher().command(launchConfig);
        require(command.contains("net.minecraftforge.bootstrap.ForgeBootstrap"), "Forge main class missing");
        require(command.contains("--username") && command.contains("ValkyrieTest"), "Offline nickname missing");
        require(command.stream().noneMatch(value -> value.contains("${")), "Unresolved launch placeholder");

        LauncherConfig updateConfig = new LauncherConfig();
        updateConfig.instanceDirectory = Files.createTempDirectory("valkyrie-launcher-test-");
        String hash = UpdateService.sha256(sourceJar);
        UpdateService.Manifest manifest = new UpdateService.Manifest(
            "smoke", "1.21.10", GameInstallationService.FORGE_PROFILE, sourceJar.toUri(), hash, java.util.List.of("Smoke test"));
        Path installed = new UpdateService().install(manifest, updateConfig);
        require(Files.isRegularFile(installed), "Client was not installed");
        require(UpdateService.sha256(installed).equals(hash), "Installed client checksum mismatch");
        for (String resource : java.util.List.of("/assets/launcher-logo.png", "/assets/launcher-icon.png", "/assets/launcher-icon.ico", "/bundled/valkyrieclient.jar", "/sounds/ui-click.wav", "/sounds/ui-type.wav",
            "/sounds/ui-open.wav", "/sounds/ui-close.wav", "/ui/launcher.css")) {
            require(SmokeTest.class.getResource(resource) != null, "Missing launcher resource: " + resource);
        }
        try (LauncherSounds sounds = new LauncherSounds(updateConfig)) {
            require(sounds.available(), "PCM UI sound could not be opened");
            sounds.press();
            Thread.sleep(140);
        }
        System.out.println("Smoke test passed: " + command.size() + " launch arguments, update SHA-256 verified");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
