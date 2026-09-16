package com.valkyrie.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.valkyrie.client.module.ModuleRegistry;
import com.valkyrie.client.module.impl.ItemAnimationsModule;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.RenderHandEvent;

/** Дополнительный transform всей first-person hand scene, один раз за кадр. */
public final class FirstPersonVisuals {
    private static boolean registered;
    private static long renderEpoch;
    private static long appliedEpoch = Long.MIN_VALUE;

    private FirstPersonVisuals() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        RenderHandEvent.BUS.addListener(FirstPersonVisuals::onHand);
    }

    public static void beginFrame() {
        renderEpoch++;
    }

    private static void onHand(RenderHandEvent event) {
        ItemAnimationsModule module = ModuleRegistry.get(ItemAnimationsModule.class);
        if (!module.isEnabled() || appliedEpoch == renderEpoch) {
            return;
        }
        appliedEpoch = renderEpoch;

        float scale = module.scale.value();
        float x = module.x.value();
        float y = module.y.value();
        float z = module.z.value();
        float styleTilt = 0.0f;
        if (module.style.is("Compact")) {
            scale *= 0.90f;
            y -= 0.08f;
            z -= 0.06f;
        } else if (module.style.is("Sakura")) {
            styleTilt = -3.0f;
            y -= 0.03f;
        }

        float swing = Mth.sin(event.getSwingProgress() * Mth.PI) * module.swing.value();
        PoseStack pose = event.getPoseStack();
        pose.translate(x, y + swing * 0.025f, z);
        pose.scale(scale, scale, scale);
        pose.mulPose(Axis.ZP.rotationDegrees(styleTilt + swing * 4.0f));
    }
}
