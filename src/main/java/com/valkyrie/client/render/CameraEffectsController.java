package com.valkyrie.client.render;

import com.valkyrie.client.module.ModuleRegistry;
import com.valkyrie.client.module.impl.CameraEffectsModule;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.RenderBlockScreenEffectEvent;

/** Временно переопределяет только публичные vanilla accessibility options. */
public final class CameraEffectsController {
    private static boolean registered;
    private static boolean overriding;
    private static double savedDamageTilt;
    private static boolean savedBobView;
    private static double savedScreenEffect;

    private CameraEffectsController() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        ComputeFovModifierEvent.BUS.addListener(CameraEffectsController::onFovModifier);
        RenderBlockScreenEffectEvent.BUS.addListener(CameraEffectsController::onScreenEffect);
    }

    public static void tick() {
        CameraEffectsModule module = ModuleRegistry.get(CameraEffectsModule.class);
        Minecraft mc = Minecraft.getInstance();
        if (module.isEnabled()) {
            if (!overriding) {
                savedDamageTilt = mc.options.damageTiltStrength().get();
                savedBobView = mc.options.bobView().get();
                savedScreenEffect = mc.options.screenEffectScale().get();
                overriding = true;
            }
            mc.options.damageTiltStrength().set(switch (module.hurtCam.value()) {
                case "Off" -> 0.0;
                case "Soft" -> 0.25;
                default -> savedDamageTilt;
            });
            mc.options.bobView().set(module.viewBob.is("Off") ? false : savedBobView);
            mc.options.screenEffectScale().set((double) module.portalScale.value());
        } else {
            restore();
        }
    }

    public static void restore() {
        if (!overriding) return;
        Minecraft mc = Minecraft.getInstance();
        mc.options.damageTiltStrength().set(savedDamageTilt);
        mc.options.bobView().set(savedBobView);
        mc.options.screenEffectScale().set(savedScreenEffect);
        overriding = false;
    }

    private static void onFovModifier(ComputeFovModifierEvent event) {
        CameraEffectsModule module = ModuleRegistry.get(CameraEffectsModule.class);
        if (module.isEnabled() && module.staticSprintFov.value() && event.getPlayer().isSprinting()) {
            event.setNewFovModifier(1.0f);
        }
    }

    /** true отменяет только vanilla water texture overlay. */
    private static boolean onScreenEffect(RenderBlockScreenEffectEvent event) {
        CameraEffectsModule module = ModuleRegistry.get(CameraEffectsModule.class);
        return module.isEnabled()
            && !module.waterOverlay.value()
            && event.getOverlayType() == RenderBlockScreenEffectEvent.OverlayType.WATER;
    }
}
