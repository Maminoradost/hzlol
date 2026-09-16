package com.valkyrie.client.module.impl;

import com.valkyrie.client.module.Category;
import com.valkyrie.client.module.Module;
import com.valkyrie.client.module.Setting;
import java.util.List;

/** Доступные без mixin настройки камеры и экранных искажений. */
public final class CameraEffectsModule extends Module {
    public final Setting.Mode hurtCam;
    public final Setting.Mode viewBob;
    public final Setting.Slider portalScale;
    public final Setting.Bool staticSprintFov;
    public final Setting.Bool waterOverlay;

    public CameraEffectsModule() {
        super("Camera effects", "motion accessibility", Category.VISUALS);
        hurtCam = add(new Setting.Mode("hurtcam", "Hurt cam", List.of("Vanilla", "Soft", "Off"), "Soft"));
        viewBob = add(new Setting.Mode("viewbob", "View bob", List.of("Vanilla", "Off"), "Vanilla"));
        portalScale = add(new Setting.Slider("portalscale", "Portal effect", 0.35f, 0.0f, 1.0f, Setting.Slider::percent));
        staticSprintFov = add(new Setting.Bool("staticfov", "Static sprint FOV", "remove sprint zoom", false));
        waterOverlay = add(new Setting.Bool("wateroverlay", "Water overlay", "vanilla underwater texture", true));
    }
}
