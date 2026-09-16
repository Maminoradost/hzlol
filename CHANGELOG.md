# Changelog

## 1.0.0 - In development

The client mod and the launcher version independently. Client `1.0.0` is unreleased;
the current built artifact is `0.9.0-beta`. The launcher is at `1.0.6`.

### Launcher 1.0.6

- Added the standalone Sakura-themed Valkyrie Launcher.
- Added local offline profiles with deterministic Minecraft UUIDs.
- Added HTTPS update manifests, SHA-256 verification, atomic installation, and JAR backups.
- Added an in-launcher changelog and existing Forge 1.21.10 profile launch support.
- Added a portable launcher archive and smoke test for update and launch-command generation.
- Replaced the initial Swing prototype with the JavaFX Sakura Portal interface.
- Added original Valkyrie logos, bundled typography, Sakura Night, animated particles, drawer transitions, toasts, and synthesized UI audio.
- Refined the launcher to an ivory acrylic direction, removed photographic backgrounds, and replaced JavaFX Media playback with a reliable preloaded PCM clip.
- Matched the launcher's Sakura renderer, Nunito font, and UI sound variants to the Minecraft client, and added public-domain System UIcons.

## 0.9.0-beta - 2026-08-07

First public beta candidate.

### Added

- Modular registry and column-based ClickGUI.
- Animated Book Fold mode with persistent side spines.
- Custom main menu, Sakura themes, particles, quotes, and companion reactions.
- HUD editor, Target HUD, custom crosshair, hit marker, damage tint, and profiles.
- Valkyrie Flow KillAura with Legacy, Modern, and Hybrid timing.
- Aim Assist, TriggerBot, AutoWeapon, AutoTotem, and natural Criticals.
- ESP, Target Glow, Storage ESP, Block Highlight, and Threat Constellation.
- Pearl Prediction, Projectile Trails, Memory Echo, Intent Lens, and Combat Bloom.
- Item Animations, Camera Effects, World Time, World Color, and weather ambience.

### Notes

- Target platform: Minecraft 1.21.10, Forge 60.1.0, Java 21.
- This beta has no automated test suite; release validation is build-based plus manual in-game testing.
- Existing configurations from development builds are loaded on a best-effort basis.
