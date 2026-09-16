package com.valkyrie.client.hud;

import com.valkyrie.client.combat.AimPoint;
import com.valkyrie.client.combat.KillAuraEngine;
import com.valkyrie.client.gui.theme.Theme;
import com.valkyrie.client.gui.thunder.ThunderRender;
import com.valkyrie.client.module.ModuleRegistry;
import com.valkyrie.client.module.impl.TargetGlowModule;
import com.valkyrie.client.render.ValkyrieProjection;
import com.valkyrie.client.render.ValkyrieProjection.ScreenPos;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeLayer;
import org.joml.Matrix4f;

/** Три тонких полупрозрачных rounded-outline слоя по экранной проекции цели. */
public final class ValkyrieTargetGlowOverlay implements ForgeLayer {
    @Override
    public void render(GuiGraphics gg, DeltaTracker deltaTracker) {
        TargetGlowModule module = ModuleRegistry.get(TargetGlowModule.class);
        LivingEntity target = KillAuraEngine.target();
        Minecraft mc = Minecraft.getInstance();
        if (!module.isEnabled() || target == null || mc.player == null || mc.screen != null || mc.options.hideGui) {
            return;
        }

        float partial = ValkyrieProjection.renderPartialTick(deltaTracker.getGameTimeDeltaPartialTick(false));
        Matrix4f matrix = ValkyrieProjection.viewProjection(partial);
        if (matrix == null) {
            return;
        }

        AABB box = AimPoint.interpolatedBox(target, partial);
        Vec3 center = box.getCenter();
        ScreenPos top = ValkyrieProjection.project(matrix, new Vec3(center.x, box.maxY, center.z), gg.guiWidth(), gg.guiHeight());
        ScreenPos bottom = ValkyrieProjection.project(matrix, new Vec3(center.x, box.minY, center.z), gg.guiWidth(), gg.guiHeight());
        ScreenPos side = ValkyrieProjection.project(matrix, new Vec3(box.maxX, center.y, center.z), gg.guiWidth(), gg.guiHeight());
        ScreenPos mid = ValkyrieProjection.project(matrix, center, gg.guiWidth(), gg.guiHeight());
        if (top == null || bottom == null || side == null || mid == null) {
            return;
        }

        int h = Math.max(8, Math.round(Math.abs(bottom.y() - top.y())));
        int halfW = Math.max(4, Math.round(Math.abs(side.x() - mid.x())));
        int x = Math.round(mid.x()) - halfW - 2;
        int y = Math.round(Math.min(top.y(), bottom.y())) - 2;
        int w = halfW * 2 + 4;
        int height = h + 4;
        float opacity = module.opacity.value();
        int accent = Theme.accent();

        // Внешние слои слабее: ощущение glow без тяжёлого bloom.
        ThunderRender.drawRoundOutline(gg, x - 2, y - 2, w + 4, height + 4, 6, Theme.alpha(accent, opacity * 0.10f));
        ThunderRender.drawRoundOutline(gg, x - 1, y - 1, w + 2, height + 2, 5, Theme.alpha(accent, opacity * 0.18f));
        ThunderRender.drawRoundOutline(gg, x, y, w, height, 4, Theme.alpha(accent, opacity * 0.42f));
    }
}
