package com.valkyrie.client.hud;

import com.valkyrie.client.gui.theme.Theme;
import com.valkyrie.client.gui.thunder.ThunderRender;
import com.valkyrie.client.module.ModuleRegistry;
import com.valkyrie.client.module.impl.PearlPredictionModule;
import com.valkyrie.client.module.impl.ProjectileTrailsModule;
import com.valkyrie.client.render.ProjectileTrailTracker;
import com.valkyrie.client.render.ProjectileTrajectory;
import com.valkyrie.client.render.ValkyrieFonts;
import com.valkyrie.client.render.ValkyrieLines;
import com.valkyrie.client.render.ValkyrieProjection;
import com.valkyrie.client.render.ValkyrieProjection.ScreenPos;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeLayer;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;

/** Pearl Prediction и Projectile Trails в одном проходе мировой проекции. */
public final class ValkyrieProjectileOverlay implements ForgeLayer {
    @Override
    public void render(GuiGraphics gg, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        PearlPredictionModule pearl = ModuleRegistry.get(PearlPredictionModule.class);
        ProjectileTrailsModule trails = ModuleRegistry.get(ProjectileTrailsModule.class);
        if ((!pearl.isEnabled() && !trails.isEnabled()) || mc.player == null || mc.level == null || mc.screen != null || mc.options.hideGui) {
            return;
        }
        float partial = ValkyrieProjection.renderPartialTick(deltaTracker.getGameTimeDeltaPartialTick(false));
        Matrix4f matrix = ValkyrieProjection.viewProjection(partial);
        if (matrix == null) {
            return;
        }
        if (trails.isEnabled()) {
            renderTrails(gg, matrix, trails);
        }
        if (pearl.isEnabled()) {
            boolean holding = mc.player.getMainHandItem().is(Items.ENDER_PEARL) || mc.player.getOffhandItem().is(Items.ENDER_PEARL);
            if (!pearl.onlyHolding.value() || holding) {
                renderPearl(gg, matrix, mc, pearl);
            }
        }
    }

    private static void renderPearl(GuiGraphics gg, Matrix4f matrix, Minecraft mc, PearlPredictionModule module) {
        ProjectileTrajectory.Path path = ProjectileTrajectory.pearl(mc.level, mc.player, Math.round(module.ticks.value()));
        ScreenPos previous = null;
        int color = Theme.alpha(Theme.accent(), module.opacity.value() * 0.58f);
        int index = 0;
        for (Vec3 world : path.points()) {
            ScreenPos point = ValkyrieProjection.project(matrix, world, gg.guiWidth(), gg.guiHeight());
            if (point == null) {
                previous = null;
                continue;
            }
            if (previous != null) {
                ValkyrieLines.drawLine(gg, previous.x(), previous.y(), point.x(), point.y(), color);
            }
            if ((index++ % 5) == 0) {
                ThunderRender.drawRoundRect(gg, Math.round(point.x()) - 1, Math.round(point.y()) - 1, 3, 3, 2, Theme.alpha(Theme.accent(), module.opacity.value() * 0.40f));
            }
            previous = point;
        }

        if (path.hit() == null) {
            return;
        }
        ScreenPos end = ValkyrieProjection.project(matrix, path.end(), gg.guiWidth(), gg.guiHeight());
        if (end == null) {
            return;
        }
        int x = Math.round(end.x());
        int y = Math.round(end.y());
        float pulse = 0.70f + 0.20f * Mth.sin(System.nanoTime() / 1_000_000_000.0f * 3.0f);
        ThunderRender.drawRoundOutline(gg, x - 7, y - 4, 14, 8, 4, Theme.alpha(Theme.accent(), module.opacity.value() * pulse));
        ThunderRender.drawRoundRect(gg, x - 1, y - 1, 3, 3, 2, Theme.alpha(Theme.accent(), module.opacity.value() * 0.75f));

        if (module.distance.value()) {
            int meters = Math.round((float) path.points().get(0).distanceTo(path.end()));
            String label = meters + "m";
            ValkyrieFonts.drawCentered(gg, mc.font, label, x, y + 7, Theme.alpha(Theme.TEXT_SECONDARY(), module.opacity.value() * 0.85f));
        }
    }

    private static void renderTrails(GuiGraphics gg, Matrix4f matrix, ProjectileTrailsModule module) {
        long now = System.nanoTime();
        float lifetime = module.lifetime.value();
        for (Map.Entry<Integer, ProjectileTrailTracker.Trail> entry : ProjectileTrailTracker.trails().entrySet()) {
            ProjectileTrailTracker.Trail trail = entry.getValue();
            ScreenPos previous = null;
            int index = 0;
            int base = kindColor(trail.kind);
            for (ProjectileTrailTracker.Sample sample : trail.samples) {
                float age = (now - sample.createdAtNs()) / 1_000_000_000.0f;
                float fade = 1.0f - Mth.clamp(age / lifetime, 0.0f, 1.0f);
                ScreenPos point = ValkyrieProjection.project(matrix, sample.position(), gg.guiWidth(), gg.guiHeight());
                if (point == null) {
                    previous = null;
                    continue;
                }
                int color = Theme.alpha(base, fade * 0.55f);
                if (module.style.is("Ribbon")) {
                    if (previous != null) {
                        ValkyrieLines.drawLine(gg, previous.x(), previous.y(), point.x(), point.y(), color);
                    }
                } else if (module.style.is("Echo")) {
                    ThunderRender.drawRoundRect(gg, Math.round(point.x()) - 1, Math.round(point.y()) - 1, 3, 3, 2, color);
                } else {
                    drawPetal(gg, point.x(), point.y(), index * 0.55f, 0.58f + fade * 0.42f, color);
                }
                previous = point;
                index++;
            }
        }
    }

    private static int kindColor(ProjectileTrailTracker.Kind kind) {
        return switch (kind) {
            case PEARL -> Theme.accent();
            case ARROW -> Theme.mix(Theme.TEXT_TERTIARY(), Theme.accent(), 0.34f);
            case THROWABLE -> Theme.mix(Theme.SUCCESS(), Theme.accent(), 0.28f);
            case TRIDENT -> Theme.mix(0xFF38BDF8, Theme.accent(), 0.34f);
        };
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
