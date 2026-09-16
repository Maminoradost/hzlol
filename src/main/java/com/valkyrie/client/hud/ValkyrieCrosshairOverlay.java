package com.valkyrie.client.hud;

import com.valkyrie.client.gui.theme.Theme;
import com.valkyrie.client.gui.thunder.ThunderRender;
import com.valkyrie.client.module.ModuleRegistry;
import com.valkyrie.client.module.impl.CrosshairModule;
import com.valkyrie.client.render.CombatVisuals;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.client.gui.overlay.ForgeLayer;

/** Четыре тонких Sakura-штриха с реакцией на цель и готовность атаки. */
public final class ValkyrieCrosshairOverlay implements ForgeLayer {
    @Override
    public void render(GuiGraphics gg, DeltaTracker deltaTracker) {
        CrosshairModule module = ModuleRegistry.get(CrosshairModule.class);
        Minecraft mc = Minecraft.getInstance();
        if (!module.isEnabled() || mc.player == null || !mc.options.getCameraType().isFirstPerson()) {
            return;
        }

        boolean entity = mc.hitResult instanceof EntityHitResult;
        float ready = mc.player.getAttackStrengthScale(0.0f);
        float hit = CombatVisuals.hitFade();
        float reaction = module.dynamic.value() ? Math.max(entity ? 1.0f : 0.0f, hit) : 0.0f;
        int accent = Theme.accent();
        int color = Theme.mix(Theme.TEXT_PRIMARY(), accent, 0.48f + reaction * 0.42f);
        float alpha = 0.72f + ready * 0.20f;

        int cx = gg.guiWidth() / 2;
        int cy = gg.guiHeight() / 2;
        int size = Math.round(module.size.value());
        int gap = Math.round(module.gap.value() + (1.0f - ready) * (module.dynamic.value() ? 2.0f : 0.0f));
        int argb = Theme.alpha(color, alpha);

        gg.fill(cx - gap - size, cy, cx - gap, cy + 1, argb);
        gg.fill(cx + gap, cy, cx + gap + size, cy + 1, argb);
        gg.fill(cx, cy - gap - size, cx + 1, cy - gap, argb);
        gg.fill(cx, cy + gap, cx + 1, cy + gap + size, argb);

        if (entity || hit > 0.01f) {
            int dot = Theme.alpha(accent, (0.34f + reaction * 0.45f));
            ThunderRender.drawRoundRect(gg, cx - 1, cy - 1, 3, 3, 2, dot);
        }
    }
}
