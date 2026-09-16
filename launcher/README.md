# Valkyrie Launcher

JavaFX 21 launcher for Valkyrie Client with the Sakura Portal interface.

The launcher installs the current Valkyrie mod into an existing Minecraft Forge 1.21.10 installation and starts it with a local offline profile. It does not download or redistribute Minecraft files.

## Interface

- Centered launch flow with the original Valkyrie logo and bundled fonts.
- Client-matched Sakura particles with layered depth, diagonal wind, gusts, wave drift, flicker, and reduced motion.
- Light Sakura and dark Sakura Night themes.
- Satin controls with public-domain System UIcons.
- Client `cream_click.ogg` converted into launcher-native PCM click, type, open, and close variants.
- Overlay changelog drawer that never shifts the primary launch content.
- In-app status messages instead of system dialog boxes.
- Compact settings menu for UI sounds, motion, and the Minecraft directory.

## Development

```powershell
..\gradlew.bat -p launcher run
..\gradlew.bat -p launcher portableZip
..\gradlew.bat -p launcher nativeZip
```

Runtime settings are stored in `%APPDATA%\ValkyrieLauncher\launcher.properties`. Set `manifestUrl` to the HTTPS URL of the published update manifest. A local `update-manifest.json` next to the launcher JAR is also supported for release testing.

The manifest can use a relative `jarUrl`; it is resolved against the manifest URL. Publish the manifest and client JAR in the same HTTPS directory, then configure the production launcher with that manifest endpoint.

## Update guarantees

- Remote manifests and client files require HTTPS.
- Every downloaded client JAR is verified with SHA-256 before installation.
- Previous Valkyrie JARs are moved to `mods/valkyrie-backup/`.
- Installation uses an atomic move when the filesystem supports it.
- The launcher never downloads Minecraft, Forge, assets, or Mojang libraries.

Offline profiles cannot join online-mode servers. Minecraft and Forge must already be installed legally by the user.

`nativeZip` creates a Windows package with `ValkyrieLauncher.exe`, JavaFX, and a bundled Java runtime, so end users do not need to install Java for the launcher itself. Minecraft still uses its own Java 21 runtime.

The launcher uses the same bundled Nunito face selected by the Minecraft client. System UIcons are distributed under The Unlicense; see `assets/SYSTEM-UICONS-UNLICENSE.txt`.
