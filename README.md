# Valkyrie Client 0.9 Beta

Valkyrie Client is an experimental client-side Forge mod for Minecraft 1.21.10. It combines a compact Sakura-themed interface with HUD, visual, utility, and combat-automation features.

The project follows a "clean client" principle: a small set of focused modules instead of a large collection of overlapping options.

## Requirements

- Minecraft 1.21.10
- Forge 60.1.0 or newer within Minecraft 1.21.10
- Java 21

## Installation

1. Install Minecraft 1.21.10 with Forge 60.1.0.
2. Place `valkyrieclient-0.9.0-beta.jar` in the instance's `mods` directory.
3. Start the Forge instance.
4. Press Right Shift to open the Valkyrie ClickGUI.

## Highlights

### Combat

- Valkyrie Flow KillAura with Legacy, Modern, and Hybrid timing
- Aim Assist and TriggerBot
- AutoWeapon, AutoTotem, and natural-jump Criticals
- Shared target filtering, raycast validation, and coordinated attack ownership

### Visuals

- Sakura Combat Bloom, Threat Constellation, Intent Lens, and Memory Echo
- ESP, Target Glow, Storage ESP, and Block Highlight
- Pearl Prediction and Projectile Trails
- Item Animations, Camera Effects, World Color, and weather ambience

### Client And HUD

- Column-based ClickGUI with animated Book Fold side spines
- HUD editor, Target HUD, custom crosshair, hit marker, and damage tint
- Light and dark Sakura themes
- Focus Mode and Visual Profiles
- Player companion with restrained contextual reactions
- Custom main menu with Telegram channel link

## Build

Use the included Gradle wrapper:

```bash
./gradlew build
```

Windows:

```powershell
.\gradlew.bat build
```

Artifacts are written to `build/libs/`:

- `valkyrieclient-0.9.0-beta.jar`
- `valkyrieclient-0.9.0-beta-sources.jar`

### Launcher

The separate Java 21 launcher is developed in `launcher/`. It provides a Sakura-themed offline profile, changelog, verified client updates, and launches an existing Forge 1.21.10 installation. It does not redistribute or download Minecraft.

```powershell
.\gradlew.bat -p launcher build smokeTest portableZip nativeZip --offline
```

The portable archive is written to `launcher/build/distributions/`.

See `RELEASE_PLAN_1.0.md` for the stable release boundary and deployment checklist.

## Beta Notice

This is a beta release. The project has been compile- and build-verified, but automated tests and comprehensive multiplayer compatibility tests are not currently available. Back up your configuration before updating.

Combat automation may violate multiplayer server rules and may lead to sanctions. The user is responsible for where and how the client is used. No bypass, undetectability, or server acceptance is guaranteed.

## Configuration

Runtime settings are stored in:

```text
config/valkyrieclient.json
```

The file is created automatically. It is not part of the source release.

## Licensing And Attribution

Valkyrie Client is distributed under GPL-3.0-or-later. The interface was influenced by, and portions of interface/font-rendering work may have been adapted from, ThunderHack-Recode. See `LICENSE` and `THIRD_PARTY_NOTICES.md`.

Bundled fonts remain under SIL Open Font License 1.1. Minecraft, Forge, and all referenced projects remain the property of their respective owners. Valkyrie Client is not affiliated with or endorsed by Mojang Studios or Microsoft.

## Channel

https://t.me/+5N4FXvQT9C8wYjNi
