package com.valkyrie.client.hud;

import com.valkyrie.client.gui.theme.Theme;
import com.valkyrie.client.module.ModuleRegistry;
import com.valkyrie.client.module.impl.WorldColorModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeLayer;

/** Едва заметный screen-space grade; собственные HUD/ESP слои рисуются поверх. */
public final class ValkyrieWorldColorOverlay implements ForgeLayer {
    @Override
    public void render(GuiGraphics gg, DeltaTracker deltaTracker) {
        WorldColorModule module = ModuleRegistry.get(WorldColorModule.class);
        Minecraft mc = Minecraft.getInstance();
        if (!module.isEnabled() || mc.level == null || mc.screen != null) return;

        float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
        float rain = module.weatherBoost.value() ? mc.level.getRainLevel(partial) : 0.0f;
        float alpha = Mth.clamp(module.strength.value() * 0.075f + rain * 0.025f, 0.0f, 0.10f);
        if (alpha <= 0.001f) return;
        gg.fill(0, 0, gg.guiWidth(), gg.guiHeight(), Theme.alpha(module.color(), alpha));
    }
}
