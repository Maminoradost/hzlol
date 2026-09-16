package com.valkyrie.client.hud;

import com.valkyrie.client.gui.theme.Theme;
import com.valkyrie.client.gui.thunder.ThunderRender;
import com.valkyrie.client.module.ModuleRegistry;
import com.valkyrie.client.module.impl.StorageEspModule;
import com.valkyrie.client.render.StorageIndex;
import com.valkyrie.client.render.ValkyrieFonts;
import com.valkyrie.client.render.ValkyrieProjection;
import com.valkyrie.client.render.ValkyrieProjection.ScreenPos;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeLayer;
import org.joml.Matrix4f;

/** Distance-faded экранные метки индексированных хранилищ. */
public final class ValkyrieStorageEspOverlay implements ForgeLayer {
    private static final int[][] CORNERS = {
        {0, 0, 0}, {1, 0, 0}, {0, 1, 0}, {1, 1, 0},
        {0, 0, 1}, {1, 0, 1}, {0, 1, 1}, {1, 1, 1}
    };
    private static final List<Candidate> CANDIDATES = new ArrayList<>(96);

    @Override
    public void render(GuiGraphics gg, DeltaTracker deltaTracker) {
        StorageEspModule module = ModuleRegistry.get(StorageEspModule.class);
        Minecraft mc = Minecraft.getInstance();
        if (!module.isEnabled() || mc.player == null || mc.level == null || mc.screen != null || mc.options.hideGui) {
            return;
        }
        float partial = ValkyrieProjection.renderPartialTick(deltaTracker.getGameTimeDeltaPartialTick(false));
        Matrix4f matrix = ValkyrieProjection.viewProjection(partial);
        if (matrix == null) return;

        float range = module.range.value();
        double rangeSqr = range * range;
        CANDIDATES.clear();
        for (StorageIndex.Entry entry : StorageIndex.entries()) {
            Vec3 center = Vec3.atCenterOf(entry.pos());
            double distanceSqr = mc.player.position().distanceToSqr(center);
            if (distanceSqr <= rangeSqr && mc.level.isLoaded(entry.pos())) {
                CANDIDATES.add(new Candidate(entry, center, distanceSqr));
            }
        }
        CANDIDATES.sort(Comparator.comparingDouble(Candidate::distanceSqr));
        int count = Math.min(CANDIDATES.size(), Math.round(module.maxMarkers.value()));
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        for (int i = 0; i < count; i++) {
            Candidate candidate = CANDIDATES.get(i);
            float distance = (float) Math.sqrt(candidate.distanceSqr);
            float start = range * 0.55f;
            float t = Mth.clamp((distance - start) / Math.max(1.0f, range - start), 0.0f, 1.0f);
            float fade = 1.0f - t * t * (3.0f - 2.0f * t);
            float opacity = module.opacity.value() * fade;
            if (opacity <= 0.02f) continue;

            int color = kindColor(candidate.entry.kind());
            if (module.style.is("Markers")) {
                ScreenPos point = ValkyrieProjection.project(matrix, candidate.center.add(0.0, 0.16, 0.0), gg.guiWidth(), gg.guiHeight());
                if (point == null) continue;
                int x = Math.round(point.x());
                int y = Math.round(point.y());
                ThunderRender.drawRoundRect(gg, x - 2, y - 2, 5, 5, 3, Theme.alpha(color, opacity * 0.70f));
                if (module.labels.value() && i < 12) {
                    drawLabel(gg, mc, x, y - 9, candidate.entry.kind().label, distance, color, opacity);
                }
            } else {
                Box box = projectBlock(matrix, camera, candidate.entry.pos(), gg.guiWidth(), gg.guiHeight());
                if (box == null) continue;
                drawCorners(gg, box, Theme.alpha(color, opacity * 0.72f));
                if (module.labels.value() && i < 12) {
                    drawLabel(gg, mc, box.centerX(), box.top - 8, candidate.entry.kind().label, distance, color, opacity);
                }
            }
        }
    }

    private static Box projectBlock(Matrix4f matrix, Vec3 camera, BlockPos pos, int width, int height) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (int[] corner : CORNERS) {
            ScreenPos point = ValkyrieProjection.project(
                matrix,
                camera,
                new Vec3(pos.getX() + corner[0], pos.getY() + corner[1], pos.getZ() + corner[2]),
                width,
                height
            );
            if (point == null) return null;
            minX = Math.min(minX, point.x());
            minY = Math.min(minY, point.y());
            maxX = Math.max(maxX, point.x());
            maxY = Math.max(maxY, point.y());
        }
        int left = Math.round(minX);
        int top = Math.round(minY);
        int w = Math.max(5, Math.round(maxX - minX));
        int h = Math.max(5, Math.round(maxY - minY));
        return new Box(left, top, w, h);
    }

    private static void drawCorners(GuiGraphics gg, Box box, int color) {
        int lx = Mth.clamp(box.w / 5, 2, 8);
        int ly = Mth.clamp(box.h / 5, 2, 8);
        int x = box.left;
        int y = box.top;
        int r = x + box.w;
        int b = y + box.h;
        gg.fill(x, y, x + lx, y + 1, color);
        gg.fill(x, y, x + 1, y + ly, color);
        gg.fill(r - lx, y, r, y + 1, color);
        gg.fill(r - 1, y, r, y + ly, color);
        gg.fill(x, b - 1, x + lx, b, color);
        gg.fill(x, b - ly, x + 1, b, color);
        gg.fill(r - lx, b - 1, r, b, color);
        gg.fill(r - 1, b - ly, r, b, color);
    }

    private static void drawLabel(GuiGraphics gg, Minecraft mc, int centerX, int y, String name, float distance, int color, float opacity) {
        String label = name + "  " + Math.round(distance) + "m";
        int width = ValkyrieFonts.width(mc.font, label);
        int x = centerX - width / 2;
        ThunderRender.drawRoundRect(gg, x - 3, y - 2, width + 6, mc.font.lineHeight + 3, 4, Theme.alpha(Theme.HUD_SURFACE(), opacity * 0.62f));
        ThunderRender.drawRoundOutline(gg, x - 3, y - 2, width + 6, mc.font.lineHeight + 3, 4, Theme.alpha(color, opacity * 0.24f));
        ValkyrieFonts.draw(gg, mc.font, label, x, y, Theme.alpha(Theme.TEXT_SECONDARY(), opacity * 0.90f));
    }

    private static int kindColor(StorageIndex.Kind kind) {
        return switch (kind) {
            case CHEST -> Theme.mix(0xFFF5A524, Theme.accent(), 0.26f);
            case TRAPPED -> Theme.mix(Theme.DANGER(), Theme.accent(), 0.30f);
            case ENDER -> Theme.mix(0xFF7A5CFF, Theme.accent(), 0.34f);
            case BARREL -> Theme.mix(0xFFB88A5A, Theme.accent(), 0.24f);
            case SHULKER -> Theme.accent();
            case FURNACE -> Theme.TEXT_TERTIARY();
            case HOPPER -> Theme.mix(Theme.TEXT_SECONDARY(), 0xFF38BDF8, 0.22f);
        };
    }

    private record Candidate(StorageIndex.Entry entry, Vec3 center, double distanceSqr) {
    }

    private record Box(int left, int top, int w, int h) {
        int centerX() { return left + w / 2; }
    }
}
