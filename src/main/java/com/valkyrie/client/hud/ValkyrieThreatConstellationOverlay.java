package com.valkyrie.client.hud;

import com.valkyrie.client.combat.KillAuraEngine;
import com.valkyrie.client.gui.theme.Theme;
import com.valkyrie.client.module.ModuleRegistry;
import com.valkyrie.client.module.impl.ThreatConstellationModule;
import com.valkyrie.client.render.ValkyrieLines;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.overlay.ForgeLayer;
import org.joml.Matrix3x2fStack;

/** Минималистичное кольцо направлений: лепестки, группы и текущая цель. */
public final class ValkyrieThreatConstellationOverlay implements ForgeLayer {
    private static final List<Marker> MARKERS = new ArrayList<>(32);

    @Override
    public void render(GuiGraphics gg, DeltaTracker deltaTracker) {
        ThreatConstellationModule module = ModuleRegistry.get(ThreatConstellationModule.class);
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!module.isEnabled() || player == null || mc.level == null || mc.screen != null || mc.options.hideGui) {
            return;
        }

        float maxRange = module.range.value();
        float maxRangeSqr = maxRange * maxRange;
        MARKERS.clear();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || living == player || !living.isAlive() || living.isInvisible()) {
                continue;
            }
            if (!module.targets.matches(living) || living instanceof Player other && player.isAlliedTo(other)) {
                continue;
            }
            double distanceSqr = player.distanceToSqr(living);
            if (distanceSqr > maxRangeSqr) {
                continue;
            }
            MARKERS.add(new Marker(living, (float) Math.sqrt(distanceSqr)));
        }
        MARKERS.sort(Comparator.comparingDouble(Marker::distance));
        int count = Math.min(MARKERS.size(), Math.round(module.maxMarkers.value()));
        if (count == 0) {
            return;
        }

        float radius = module.radius.value();
        float cx = gg.guiWidth() * 0.5f;
        float cy = gg.guiHeight() * 0.5f;
        LivingEntity auraTarget = KillAuraEngine.target();
        float time = System.nanoTime() / 1_000_000_000.0f;

        // Почти невидимое кольцо из восьми опорных точек, а не тяжёлая окружность.
        int guide = Theme.alpha(Theme.BORDER(), 0.18f);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            int x = Math.round(cx + (float) Math.sin(a) * radius);
            int y = Math.round(cy - (float) Math.cos(a) * radius);
            gg.fill(x, y, x + 1, y + 1, guide);
        }

        for (int i = 0; i < count; i++) {
            Marker marker = MARKERS.get(i);
            double dx = marker.entity.getX() - player.getX();
            double dz = marker.entity.getZ() - player.getZ();
            float worldYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            float relative = Mth.wrapDegrees(worldYaw - player.getYRot());
            float angle = relative * Mth.PI / 180.0f;
            marker.x = cx + Mth.sin(angle) * radius;
            marker.y = cy - Mth.cos(angle) * radius;
            marker.angle = angle;
        }

        if (module.groups.value()) {
            int lineColor = Theme.alpha(Theme.accent(), 0.10f);
            for (int i = 0; i < count; i++) {
                Marker a = MARKERS.get(i);
                for (int j = i + 1; j < count; j++) {
                    Marker b = MARKERS.get(j);
                    float dx = a.x - b.x;
                    float dy = a.y - b.y;
                    if (dx * dx + dy * dy <= 18.0f * 18.0f) {
                        ValkyrieLines.drawLine(gg, a.x, a.y, b.x, b.y, lineColor);
                    }
                }
            }
        }

        for (int i = 0; i < count; i++) {
            Marker marker = MARKERS.get(i);
            boolean selected = marker.entity == auraTarget;
            float threat = 1.0f - Mth.clamp(marker.distance / maxRange, 0.0f, 1.0f);
            float pulse = module.pulse.value() ? 0.88f + 0.12f * Mth.sin(time * 3.0f + i) * threat : 1.0f;
            float scale = (0.70f + threat * 0.52f + (selected ? 0.30f : 0.0f)) * pulse;
            int color = Theme.alpha(selected ? Theme.accent() : Theme.mix(Theme.TEXT_TERTIARY(), Theme.accent(), 0.45f), 0.42f + threat * 0.40f);
            drawPetal(gg, marker.x, marker.y, marker.angle, scale, color);

            if (selected && module.targetConnection.value()) {
                ValkyrieLines.drawLine(gg, cx, cy, marker.x, marker.y, Theme.alpha(Theme.accent(), 0.18f));
            }
        }
    }

    private static void drawPetal(GuiGraphics gg, float x, float y, float angle, float scale, int color) {
        Matrix3x2fStack pose = gg.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.rotate(angle + Mth.PI * 0.25f);
        pose.scale(scale, scale);
        gg.fill(-1, -3, 2, 3, color);
        gg.fill(0, -4, 1, -3, color);
        gg.fill(0, 3, 1, 4, color);
        pose.popMatrix();
    }

    private static final class Marker {
        final LivingEntity entity;
        final float distance;
        float x;
        float y;
        float angle;

        Marker(LivingEntity entity, float distance) {
            this.entity = entity;
            this.distance = distance;
        }

        float distance() {
            return distance;
        }
    }
}
