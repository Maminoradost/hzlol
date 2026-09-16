package com.valkyrie.client.hud;

import com.valkyrie.client.render.CombatVisuals;
import com.valkyrie.client.render.ValkyrieProjection;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeLayer;
import org.joml.Matrix4f;

/** Экранный проход Sakura Combat Bloom, привязанный к мировой точке удара. */
public final class ValkyrieCombatBloomOverlay implements ForgeLayer {
    @Override
    public void render(GuiGraphics gg, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null || mc.options.hideGui) {
            return;
        }
        float partial = ValkyrieProjection.renderPartialTick(deltaTracker.getGameTimeDeltaPartialTick(false));
        Matrix4f matrix = ValkyrieProjection.viewProjection(partial);
        if (matrix != null) {
            CombatVisuals.renderBlooms(gg, matrix);
        }
    }
}
