package com.valkyrie.client.module.impl;

import com.valkyrie.client.config.ValkyrieOptionsManager;
import com.valkyrie.client.gui.notify.Notifications;
import com.valkyrie.client.module.Category;
import com.valkyrie.client.module.Module;
import com.valkyrie.client.module.ModuleRegistry;
import com.valkyrie.client.module.Setting;
import java.util.List;

/** Явно применяемые наборы визуалов: профиль не перезаписывает настройки сам. */
public final class VisualProfilesModule extends Module {
    public final Setting.Mode profile;

    public VisualProfilesModule() {
        super("Visual profiles", "apply a visual preset", Category.CLIENT);
        profile = add(new Setting.Mode("profile", "Profile", List.of("Normal", "Performance", "Calm", "Combat", "Cinematic"), "Normal"));
    }

    @Override
    public boolean hasToggle() {
        return false;
    }

    @Override
    public boolean isAction() {
        return true;
    }

    @Override
    public String actionLabel() {
        return "APPLY";
    }

    @Override
    public void runAction() {
        switch (profile.value()) {
            case "Normal" -> applyNormal();
            case "Performance" -> applyPerformance();
            case "Calm" -> applyCalm();
            case "Cinematic" -> applyCinematic();
            default -> applyCombat();
        }
        ValkyrieOptionsManager.save();
        Notifications.success("Visual profile", profile.value() + " applied");
    }

    private static void applyNormal() {
        AppearanceModule appearance = ModuleRegistry.get(AppearanceModule.class);
        appearance.particles.set("Normal");
        appearance.sakuraMotion.set("Calm");
        appearance.reducedMotion.set(false);
        appearance.playerPreview.set(true);
        set(SakuraBloomModule.class, true);
        ModuleRegistry.get(SakuraBloomModule.class).amount.set("Normal");
        set(ProjectileTrailsModule.class, true);
        ProjectileTrailsModule trails = ModuleRegistry.get(ProjectileTrailsModule.class);
        trails.style.set("Ribbon");
        trails.lifetime.set(1.4f);
        set(ThreatConstellationModule.class, true);
        ThreatConstellationModule threat = ModuleRegistry.get(ThreatConstellationModule.class);
        threat.maxMarkers.set(14.0f);
        threat.groups.set(true);
        threat.pulse.set(true);
        set(TargetGlowModule.class, true);
        ModuleRegistry.get(PearlPredictionModule.class).ticks.set(90.0f);
    }

    private static void applyPerformance() {
        AppearanceModule appearance = ModuleRegistry.get(AppearanceModule.class);
        appearance.particles.set("Off");
        appearance.reducedMotion.set(true);
        appearance.playerPreview.set(false);
        set(SakuraBloomModule.class, false);
        set(ProjectileTrailsModule.class, false);
        set(ThreatConstellationModule.class, false);
        set(MemoryEchoModule.class, false);
        set(TargetGlowModule.class, false);
        PearlPredictionModule pearl = ModuleRegistry.get(PearlPredictionModule.class);
        pearl.ticks.set(50.0f);
        pearl.onlyHolding.set(true);
        StorageEspModule storage = ModuleRegistry.get(StorageEspModule.class);
        storage.style.set("Markers");
        storage.range.set(48.0f);
        storage.maxMarkers.set(24.0f);
        storage.labels.set(false);
    }

    private static void applyCalm() {
        AppearanceModule appearance = ModuleRegistry.get(AppearanceModule.class);
        appearance.particles.set("Low");
        appearance.sakuraMotion.set("Calm");
        appearance.quotes.set("Menu");
        set(SakuraBloomModule.class, true);
        ModuleRegistry.get(SakuraBloomModule.class).amount.set("Low");
        set(ProjectileTrailsModule.class, false);
        set(ThreatConstellationModule.class, false);
        set(MemoryEchoModule.class, false);
        set(TargetGlowModule.class, false);
        set(WorldColorModule.class, true);
        WorldColorModule world = ModuleRegistry.get(WorldColorModule.class);
        world.mood.set("Natural");
        world.strength.set(0.20f);
        world.fog.set(0.08f);
    }

    private static void applyCombat() {
        AppearanceModule appearance = ModuleRegistry.get(AppearanceModule.class);
        appearance.particles.set("Low");
        appearance.quotes.set("All");
        set(SakuraBloomModule.class, true);
        ModuleRegistry.get(SakuraBloomModule.class).amount.set("Normal");
        set(ProjectileTrailsModule.class, true);
        ModuleRegistry.get(ProjectileTrailsModule.class).style.set("Ribbon");
        set(ThreatConstellationModule.class, true);
        set(MemoryEchoModule.class, true);
        set(TargetGlowModule.class, true);
        set(WorldColorModule.class, true);
        WorldColorModule world = ModuleRegistry.get(WorldColorModule.class);
        world.mood.set("Sakura");
        world.strength.set(0.32f);
        world.fog.set(0.10f);
    }

    private static void applyCinematic() {
        AppearanceModule appearance = ModuleRegistry.get(AppearanceModule.class);
        appearance.particles.set("Normal");
        appearance.sakuraMotion.set("Windy");
        appearance.quotes.set("All");
        set(SakuraBloomModule.class, true);
        ModuleRegistry.get(SakuraBloomModule.class).amount.set("Rich");
        set(ProjectileTrailsModule.class, true);
        ModuleRegistry.get(ProjectileTrailsModule.class).style.set("Petals");
        set(ThreatConstellationModule.class, false);
        set(MemoryEchoModule.class, true);
        ModuleRegistry.get(MemoryEchoModule.class).style.set("Petals");
        set(TargetGlowModule.class, true);
        set(WorldColorModule.class, true);
        WorldColorModule world = ModuleRegistry.get(WorldColorModule.class);
        world.mood.set("Moonlit");
        world.strength.set(0.58f);
        world.fog.set(0.28f);
        CameraEffectsModule camera = ModuleRegistry.get(CameraEffectsModule.class);
        set(CameraEffectsModule.class, true);
        camera.hurtCam.set("Soft");
        camera.viewBob.set("Vanilla");
    }

    private static <T extends Module> void set(Class<T> type, boolean enabled) {
        ModuleRegistry.get(type).setEnabledSilently(enabled);
    }
}
