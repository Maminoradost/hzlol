package com.valkyrie.launcher;

import java.nio.file.Files;
import java.nio.file.Path;

public final class BootstrapTest {
    private BootstrapTest() {}

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        LauncherConfig config = new LauncherConfig();
        config.nickname = "ValkyrieTest";
        config.contentDirectory = root.resolve("content");
        config.instanceDirectory = root.resolve("instance");
        config.forgeProfile = GameInstallationService.FORGE_PROFILE;

        GameInstallationService installation = new GameInstallationService();
        installation.ensureInstalled(config, System.out::println);
        if (!installation.installed(config)) throw new AssertionError("Managed Minecraft installation is incomplete");
        new UpdateService().installBundled(config);
        var command = new MinecraftLauncher().command(config);
        require(command.contains("net.minecraftforge.bootstrap.ForgeBootstrap"), "Forge main class missing");
        require(command.stream().anyMatch(value -> value.contains("forge-1.21.10-60.1.0-client.jar")), "Patched Forge client missing from classpath");
        require(Files.isRegularFile(config.instanceDirectory.resolve("mods/valkyrieclient-0.9.0-beta.jar")), "Bundled Valkyrie client missing");
        require(command.stream().noneMatch(value -> value.contains("C:\\Games\\Minecraft")), "Legacy Minecraft path leaked into command");
        require(command.stream().noneMatch(value -> value.contains("${")), "Unresolved launch placeholder");
        System.out.println("Bootstrap test passed: clean managed Minecraft, Forge and Valkyrie installed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
