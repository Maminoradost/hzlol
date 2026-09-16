package com.valkyrie.launcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class MultiVersionTest {
    private MultiVersionTest() {}

    public static void main(String[] args) throws Exception {
        Path expectedNatives = Path.of("native-test-directory").toAbsolutePath().normalize();
        List<String> nativeArguments = new ArrayList<>(List.of("-Djava.library.path=" + expectedNatives.resolve("java")));
        MinecraftLauncher.replaceNativeLibraryPath(nativeArguments, expectedNatives);
        require(nativeArguments.equals(List.of("-Djava.library.path=" + expectedNatives)), "Versioned native path was not normalized");

        var catalog = new VersionCatalogService().load();
        require(catalog.getFirst().valkyrie(), "Valkyrie is not first in the version catalog");
        require(catalog.stream().anyMatch(version -> !version.valkyrie() && version.id().equals(GameInstallationService.MINECRAFT_VERSION)),
            "Vanilla variant of the Valkyrie Minecraft version is missing");
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        LauncherConfig config = new LauncherConfig();
        config.nickname = "VanillaTest";
        config.contentDirectory = root.resolve("content");
        config.instanceDirectory = root.resolve("vanilla-instance");
        GameVersion vanilla = new GameVersion(GameInstallationService.MINECRAFT_VERSION, "release",
            GameInstallationService.VERSION_URL, GameInstallationService.VERSION_SHA1, false);
        config.selectedVersion = vanilla.id();
        config.selectedValkyrie = false;
        config.forgeProfile = vanilla.profileId();

        GameInstallationService installation = new GameInstallationService();
        installation.ensureInstalled(config, vanilla, System.out::println);
        require(installation.installed(config, vanilla), "Vanilla installation is incomplete");
        var command = new MinecraftLauncher().command(config);
        require(command.contains("net.minecraft.client.main.Main"), "Vanilla main class missing");
        require(!command.contains("net.minecraftforge.bootstrap.ForgeBootstrap"), "Forge leaked into vanilla launch");
        require(command.contains(config.instanceDirectory.toString()), "Isolated instance path missing");
        require(command.getFirst().toLowerCase(java.util.Locale.ROOT).contains("java"), "Automatic Java was not selected");
        require(!Files.exists(config.instanceDirectory.resolve("mods/valkyrieclient-0.9.0-beta.jar")), "Valkyrie leaked into vanilla instance");
        require(command.stream().noneMatch(value -> value.contains("${")), "Unresolved launch placeholder");
        String nativeDirectory = config.instanceDirectory.resolve("natives").resolve(vanilla.profileId()).toAbsolutePath().normalize().toString();
        require(command.contains("-Djava.library.path=" + nativeDirectory), "Native library path does not match extraction directory");
        System.out.println("Multi-version test passed: isolated vanilla " + vanilla.id() + " command is ready");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
