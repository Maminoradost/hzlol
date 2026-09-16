package com.valkyrie.client.hud;

import com.valkyrie.client.combat.KillAuraEngine;
import com.valkyrie.client.gui.theme.Theme;
import com.valkyrie.client.gui.thunder.ThunderRender;
import com.valkyrie.client.module.ModuleRegistry;
import com.valkyrie.client.module.impl.IntentLensModule;
import com.valkyrie.client.module.impl.KillAuraModule;
import com.valkyrie.client.module.impl.MemoryEchoModule;
import com.valkyrie.client.render.MemoryEchoTracker;
import com.valkyrie.client.render.ValkyrieLines;
import com.valkyrie.client.render.ValkyrieProjection;
import com.valkyrie.client.render.ValkyrieProjection.ScreenPos;
import java.util.Deque;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeLayer;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;

/** Общий лёгкий проход Intent Lens и Memory Echo. */
public final class ValkyrieIntentEchoOverlay implements ForgeLayer {
    @Override
    public void render(GuiGraphics gg, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        IntentLensModule intent = ModuleRegistry.get(IntentLensModule.class);
        MemoryEchoModule echo = ModuleRegistry.get(MemoryEchoModule.class);
        if ((!intent.isEnabled() && !echo.isEnabled()) || mc.player == null || mc.level == null || mc.screen != null || mc.options.hideGui) {
            return;
        }

        float partial = ValkyrieProjection.renderPartialTick(deltaTracker.getGameTimeDeltaPartialTick(false));
        Matrix4f matrix = ValkyrieProjection.viewProjection(partial);
        if (matrix == null) {
            return;
        }
        if (echo.isEnabled()) {
            renderEchoes(gg, matrix, echo, MemoryEchoTracker.self(), false);
            renderEchoes(gg, matrix, echo, MemoryEchoTracker.target(), true);
        }
        if (intent.isEnabled()) {
            renderIntent(gg, matrix, mc, intent);
        }
    }

    private static void renderIntent(GuiGraphics gg, Matrix4f matrix, Minecraft mc, IntentLensModule module) {
        LivingEntity target = KillAuraEngine.target();
        if (target == null) {
            return;
        }
        Vec3 current = target.position().add(0.0, target.getBbHeight() * 0.62, 0.0);
        Vec3 velocity = target.position().subtract(target.xOld, target.yOld, target.zOld);
        Vec3 future = current.add(velocity.scale(module.prediction.value()));
        ScreenPos now = ValkyrieProjection.project(matrix, current, gg.guiWidth(), gg.guiHeight());
        ScreenPos predicted = ValkyrieProjection.project(matrix, future, gg.guiWidth(), gg.guiHeight());
        if (now == null || predicted == null) {
            return;
        }

        int accent = Theme.accent();
        float opacity = module.opacity.value();
        float speed = (float) velocity.horizontalDistance();
        float motion = Mth.clamp(speed * 6.0f, 0.20f, 1.0f);
        int line = Theme.alpha(accent, opacity * 0.32f * motion);
        ValkyrieLines.drawLine(gg, now.x(), now.y(), predicted.x(), predicted.y(), line);

        // Прогнозная точка — маленькая Sakura-линза, не ещё один ESP-бокс.
        int px = Math.round(predicted.x());
        int py = Math.round(predicted.y());
        ThunderRender.drawRoundOutline(gg, px - 5, py - 5, 10, 10, 5, Theme.alpha(accent, opacity * 0.42f));
        ThunderRender.drawRoundRect(gg, px - 1, py - 1, 3, 3, 2, Theme.alpha(accent, opacity * 0.62f));

        if (module.attackWindow.value()) {
            boolean ready = KillAuraEngine.attackReady();
            if (ready) {
                int x = Math.round(now.x());
                int y = Math.round(now.y());
                float pulse = 0.58f + 0.18f * Mth.sin(System.nanoTime() / 1_000_000_000.0f * 4.0f);
                ThunderRender.drawRoundOutline(gg, x - 8, y - 8, 16, 16, 8, Theme.alpha(Theme.SUCCESS(), opacity * pulse));
            }
        }
    }

    private static void renderEchoes(GuiGraphics gg, Matrix4f matrix, MemoryEchoModule module, Deque<MemoryEchoTracker.Sample> samples, boolean target) {
        if (samples.isEmpty()) {
            return;
        }
        long nowNs = System.nanoTime();
        float lifetime = module.lifetime.value();
        ScreenPos previous = null;
        int index = 0;
        for (MemoryEchoTracker.Sample sample : samples) {
            float age = (nowNs - sample.createdAtNs()) / 1_000_000_000.0f;
            float fade = 1.0f - Mth.clamp(age / lifetime, 0.0f, 1.0f);
            ScreenPos point = ValkyrieProjection.project(matrix, sample.position(), gg.guiWidth(), gg.guiHeight());
            if (point == null) {
                continue;
            }
            int color = Theme.alpha(target ? Theme.accent() : Theme.TEXT_TERTIARY(), fade * (target ? 0.34f : 0.22f));
            if (module.style.is("Ribbon")) {
                if (previous != null) {
                    ValkyrieLines.drawLine(gg, previous.x(), previous.y(), point.x(), point.y(), color);
                }
                ThunderRender.drawRoundRect(gg, Math.round(point.x()) - 1, Math.round(point.y()) - 1, 3, 3, 2, color);
            } else {
                drawPetal(gg, point.x(), point.y(), index * 0.72f, 0.72f + fade * 0.48f, color);
            }
            previous = point;
            index++;
        }
    }

    private static void drawPetal(GuiGraphics gg, float x, float y, float rotation, float scale, int color) {
        Matrix3x2fStack pose = gg.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.rotate(rotation);
        pose.scale(scale, scale);
        gg.fill(-1, -3, 2, 3, color);
        gg.fill(0, -4, 1, -3, color);
        gg.fill(0, 3, 1, 4, color);
        pose.popMatrix();
    }
}
