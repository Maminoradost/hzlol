# Valkyrie 1.0 Stable

## Product scope

Valkyrie 1.0 consists of two independently built artifacts:

- `valkyrieclient-1.0.0.jar`: the Forge 1.21.10 client mod.
- `valkyrie-launcher-1.0.6-windows.zip`: the Windows launcher with a bundled Java runtime.

The two version lines are independent: the launcher has already shipped past 1.0 and is at
`1.0.6`. Both versions come from `launcherVersion`/`clientVersion` in `launcher/build.gradle`
and `version` in the root `build.gradle` — update them there, not in this document.

The launcher remains intentionally small: offline nickname, verified client update, changelog, game-directory selection, and launch. It does not include accounts, stores, cosmetics, news feeds, or unrelated settings.

## Implemented launcher baseline

- Sakura-themed desktop UI matching the client palette.
- Centered JavaFX Sakura Portal with light and dark themes.
- Animated reusable Sakura background particles and reduced-motion support.
- Overlay changelog drawer, custom title bar, toasts, and interactive button states.
- Locally synthesized cream click audio with a persistent sound toggle.
- Offline profiles with deterministic `OfflinePlayer:<nickname>` UUIDs.
- Detection of the existing `C:\Games\Minecraft\game` installation.
- Manual selection of another Minecraft directory.
- Existing Forge 1.21.10 profile launch support.
- HTTPS update manifest with relative client URLs.
- SHA-256 verification before installation.
- Backup of previous Valkyrie JARs.
- Atomic JAR replacement when supported by the filesystem.
- Local changelog fallback when the update service is unavailable.
- Portable JAR and native Windows app-image builds.
- Smoke test for Forge command generation and verified installation.

## Required before publication

1. Build and freeze `valkyrieclient-1.0.0.jar`.
2. Regenerate `release/SHA256SUMS.txt` from the frozen artifacts and update
   `UpdateService.BUNDLED_SHA256`/`BUNDLED_VERSION` to match. Both are maintained by hand and
   have drifted from the shipped JAR before — verify every entry against the real file.
3. Choose the public HTTPS release host or GitHub Releases repository.
4. Publish the client JAR and `update-manifest.json` in the same HTTPS directory.
5. Set the production `manifestUrl` default in `LauncherConfig`.
6. Replace or confirm redistribution rights for unverified logo and sound assets.
7. Rotate any token previously exposed in local handoff material.
8. Build `nativeZip`, publish its SHA-256, and test on a clean Windows machine.

## Release manifest

```json
{
  "version": "1.0.0",
  "minecraftVersion": "1.21.10",
  "forgeProfile": "Forge 1.21.10",
  "jarUrl": "valkyrieclient-1.0.0.jar",
  "sha256": "64 lowercase hexadecimal characters",
  "changelog": [
    "Stable Valkyrie Client release"
  ]
}
```

Remote manifests and downloads require HTTPS. A local `file:` client URL is accepted only when the manifest itself is loaded from a local `update-manifest.json`, for release testing.

## Build verification

```powershell
.\gradlew.bat -p launcher clean build smokeTest portableZip nativeZip --offline
```

Expected output:

```text
Smoke test passed: 37 launch arguments, update SHA-256 verified
BUILD SUCCESSFUL
```
