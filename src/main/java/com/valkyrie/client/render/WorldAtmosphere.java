package com.valkyrie.client.render;

import com.valkyrie.client.module.ModuleRegistry;
import com.valkyrie.client.module.impl.WorldColorModule;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.ViewportEvent;

/** Forge-only atmospheric hooks; server weather and dimension data remain untouched. */
public final class WorldAtmosphere {
    private static boolean registered;

    private WorldAtmosphere() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        ViewportEvent.RenderFog.BUS.addListener(WorldAtmosphere::onRenderFog);
    }

    private static boolean onRenderFog(ViewportEvent.RenderFog event) {
        WorldColorModule module = ModuleRegistry.get(WorldColorModule.class);
        Minecraft mc = Minecraft.getInstance();
        if (!module.isEnabled() || mc.level == null) return false;

        float rain = module.weatherBoost.value() ? mc.level.getRainLevel(event.getPartialTick()) : 0.0f;
        float colorStrength = Mth.clamp(module.strength.value() * 0.34f + rain * 0.12f, 0.0f, 0.52f);
        int target = module.color();
        float red = ((target >>> 16) & 0xFF) / 255.0f;
        float green = ((target >>> 8) & 0xFF) / 255.0f;
        float blue = (target & 0xFF) / 255.0f;
        event.getColor().x = Mth.lerp(colorStrength, event.getColor().x, red);
        event.getColor().y = Mth.lerp(colorStrength, event.getColor().y, green);
        event.getColor().z = Mth.lerp(colorStrength, event.getColor().z, blue);

        float density = Mth.clamp(module.fog.value() + rain * 0.20f, 0.0f, 0.78f);
        if (density <= 0.001f) return false;
        event.setNearPlaneDistance(event.getNearPlaneDistance() * (1.0f - density * 0.18f));
        event.setFarPlaneDistance(event.getFarPlaneDistance() * (1.0f - density * 0.54f));
        return true;
    }
}
