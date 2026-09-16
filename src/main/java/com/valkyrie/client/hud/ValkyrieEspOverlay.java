package com.valkyrie.client.hud;

import com.valkyrie.client.gui.theme.Theme;
import com.valkyrie.client.gui.thunder.ThunderRender;
import com.valkyrie.client.module.ModuleRegistry;
import com.valkyrie.client.module.impl.EspModule;
import com.valkyrie.client.render.ValkyrieFonts;
import com.valkyrie.client.render.ValkyrieProjection;
import com.valkyrie.client.render.ValkyrieProjection.ScreenPos;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeLayer;
import org.joml.Matrix4f;

/**
 * 2D-ESP: рамка по экранной проекции хитбокса, неймтег и полоса здоровья.
 * <p>
 * Рамка строится по восьми углам AABB: каждый угол проецируется отдельно, затем
 * берётся охватывающий прямоугольник. Это единственный способ получить корректный
 * бокс без 3D-прохода — в Forge 60.x нет {@code RenderLevelStageEvent}.
 */
public final class ValkyrieEspOverlay implements ForgeLayer {
    private static final int MAX_TARGETS = 48;
    /** Минимальный размер рамки: далёкие цели не должны схлопываться в точку. */
    private static final int MIN_BOX = 6;
    private static final int NAME_PAD_X = 4;
    private static final int NAME_PAD_Y = 2;
    private static final int HEALTH_BAR_WIDTH = 1;
    private static final int HEALTH_BAR_GAP = 1;

    /** Углы единичного куба — порядок не важен, нужен только охват. */
    private static final int[][] CORNERS = {
        {0, 0, 0}, {1, 0, 0}, {0, 1, 0}, {1, 1, 0},
        {0, 0, 1}, {1, 0, 1}, {0, 1, 1}, {1, 1, 1}
    };

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        EspModule module = ModuleRegistry.get(EspModule.class);
        if (!module.isEnabled()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null || minecraft.options.hideGui) {
            return;
        }

        float partialTick = ValkyrieProjection.renderPartialTick(deltaTracker.getGameTimeDeltaPartialTick(false));
        Matrix4f matrix = ValkyrieProjection.viewProjection(partialTick);
        if (matrix == null) {
            return;
        }

        LocalPlayer player = minecraft.player;
        Vec3 cameraPos = minecraft.gameRenderer.getMainCamera().getPosition();
        int guiWidth = guiGraphics.guiWidth();
        int guiHeight = guiGraphics.guiHeight();
        int drawn = 0;
        float range = module.range.value();

        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (drawn >= MAX_TARGETS) {
                break;
            }
            if (!(entity instanceof LivingEntity living) || entity == player || !living.isAlive()) {
                continue;
            }
            if (!module.targets.matches(living)) {
                continue;
            }

            double distanceSqr = player.distanceToSqr(living);
            if (distanceSqr > range * range) {
                continue;
            }

            // Дальние цели тускнеют, иначе горизонт превращается в кашу из рамок.
            float distance = Mth.sqrt((float) distanceSqr);
            float fade = 1.0f - Mth.clamp(distance / range, 0.0f, 1.0f) * 0.74f;
            float opacity = Mth.clamp(module.opacity.value() * fade, 0.0f, 1.0f);
            if (opacity <= 0.02f) {
                continue;
            }

            if (renderTarget(guiGraphics, minecraft.font, matrix, cameraPos, living, partialTick,
                guiWidth, guiHeight, distance, opacity, module)) {
                drawn++;
            }
        }
    }

    /** @return {@code true}, если цель попала на экран и что-то было нарисовано */
    private static boolean renderTarget(
        GuiGraphics guiGraphics,
        Font font,
        Matrix4f matrix,
        Vec3 cameraPos,
        LivingEntity living,
        float partialTick,
        int guiWidth,
        int guiHeight,
        float distance,
        float opacity,
        EspModule module
    ) {
        Box box = projectBox(matrix, cameraPos, living, partialTick, guiWidth, guiHeight);
        if (box == null) {
            return renderOffscreen(guiGraphics, font, matrix, living, partialTick, guiWidth, guiHeight, opacity, module);
        }

        int color = module.color.value();
        if (module.boxes.value()) {
            drawCornerBox(guiGraphics, box, color, opacity);
        }
        if (module.healthBars.value()) {
            drawHealthBar(guiGraphics, box, living, opacity);
        }
        if (module.nametags.value()) {
            drawNametag(guiGraphics, font, box.left + box.width() / 2, box.top - 3, living, distance, color, opacity);
        }
        return true;
    }

    /**
     * Экранная рамка по восьми углам хитбокса.
     *
     * @return {@code null}, если цель целиком за камерой или вне экрана
     */
    private static Box projectBox(
        Matrix4f matrix,
        Vec3 cameraPos,
        LivingEntity living,
        float partialTick,
        int guiWidth,
        int guiHeight
    ) {
        // Интерполированный AABB: getBoundingBox() отстаёт на кадр и рамка дрожит.
        Vec3 position = living.getPosition(partialTick);
        float halfWidth = living.getBbWidth() / 2.0f;
        AABB aabb = new AABB(
            position.x - halfWidth,
            position.y,
            position.z - halfWidth,
            position.x + halfWidth,
            position.y + living.getBbHeight(),
            position.z + halfWidth
        );

        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        for (int[] corner : CORNERS) {
            Vec3 point = new Vec3(
                corner[0] == 0 ? aabb.minX : aabb.maxX,
                corner[1] == 0 ? aabb.minY : aabb.maxY,
                corner[2] == 0 ? aabb.minZ : aabb.maxZ
            );
            ScreenPos projected = ValkyrieProjection.project(matrix, cameraPos, point, guiWidth, guiHeight);
            if (projected == null) {
                // Хотя бы один угол за камерой — цель слишком близко, рамку не строим.
                return null;
            }
            minX = Math.min(minX, projected.x());
            minY = Math.min(minY, projected.y());
            maxX = Math.max(maxX, projected.x());
            maxY = Math.max(maxY, projected.y());
        }

        int left = Math.round(minX);
        int top = Math.round(minY);
        int right = Math.round(maxX);
        int bottom = Math.round(maxY);
        if (right - left < MIN_BOX) {
            int center = (left + right) / 2;
            left = center - MIN_BOX / 2;
            right = left + MIN_BOX;
        }
        if (bottom - top < MIN_BOX) {
            int center = (top + bottom) / 2;
            top = center - MIN_BOX / 2;
            bottom = top + MIN_BOX;
        }
        // Полностью ушедшие за край цели отбрасываем: рисовать нечего.
        if (right < 0 || bottom < 0 || left > guiWidth || top > guiHeight) {
            return null;
        }
        return new Box(left, top, right, bottom);
    }

    /** Указатель на цель вне экрана — только неймтег, прижатый к краю. */
    private static boolean renderOffscreen(
        GuiGraphics guiGraphics,
        Font font,
        Matrix4f matrix,
        LivingEntity living,
        float partialTick,
        int guiWidth,
        int guiHeight,
        float opacity,
        EspModule module
    ) {
        if (!module.offscreen.value() || !module.nametags.value()) {
            return false;
        }

        Vec3 position = living.getPosition(partialTick).add(0.0, living.getBbHeight() * 0.5, 0.0);
        ScreenPos pos = ValkyrieProjection.projectClamped(matrix, position, guiWidth, guiHeight, 16);
        if (pos == null) {
            return false;
        }

        // За экраном сама метка не нужна — достаточно точки нужного цвета.
        ThunderRender.drawRoundRect(
            guiGraphics,
            Math.round(pos.x()) - 2,
            Math.round(pos.y()) - 2,
            4,
            4,
            2,
            Theme.alpha(module.color.value(), 0.50f * opacity)
        );
        return true;
    }

    /**
     * Рамка «уголками»: сплошной контур на плотной сцене сливается с миром,
     * а уголки читаются на любом фоне и не закрывают модель.
     */
    private static void drawCornerBox(GuiGraphics guiGraphics, Box box, int color, float opacity) {
        int w = box.width();
        int h = box.height();
        // Длина уголка — четверть стороны, но не больше половины: на узких целях
        // два уголка иначе сомкнутся в сплошную линию.
        int lengthX = Mth.clamp(w / 4, 2, Math.max(2, w / 2));
        int lengthY = Mth.clamp(h / 4, 2, Math.max(2, h / 2));

        int outline = Theme.alpha(color, 0.76f * opacity);
        drawCorners(guiGraphics, box.left, box.top, w, h, lengthX, lengthY, outline);
    }

    private static void drawCorners(GuiGraphics g, int x, int y, int w, int h, int lx, int ly, int color) {
        int right = x + w;
        int bottom = y + h;
        // Верх
        g.fill(x, y, x + lx, y + 1, color);
        g.fill(right - lx, y, right, y + 1, color);
        // Низ
        g.fill(x, bottom - 1, x + lx, bottom, color);
        g.fill(right - lx, bottom - 1, right, bottom, color);
        // Лево
        g.fill(x, y, x + 1, y + ly, color);
        g.fill(x, bottom - ly, x + 1, bottom, color);
        // Право
        g.fill(right - 1, y, right, y + ly, color);
        g.fill(right - 1, bottom - ly, right, bottom, color);
    }

    /** Вертикальная полоса слева от рамки: зелёная сверху, красная снизу. */
    private static void drawHealthBar(GuiGraphics guiGraphics, Box box, LivingEntity living, float opacity) {
        float max = living.getMaxHealth();
        if (max <= 0.0f) {
            return;
        }
        float ratio = Mth.clamp(living.getHealth() / max, 0.0f, 1.0f);

        int x = box.left - HEALTH_BAR_GAP - HEALTH_BAR_WIDTH;
        int y = box.top;
        int h = box.height();
        if (h < 3) {
            return;
        }

        guiGraphics.fill(x, y, x + HEALTH_BAR_WIDTH, y + h, Theme.alpha(0xFF000000, 0.55f * opacity));

        int filled = Math.max(1, Math.round(h * ratio));
        // Цвет ведём через жёлтый: прямой микс зелёного с красным даёт грязный хаки.
        int color = ratio > 0.5f
            ? Theme.mix(0xFFF5A524, Theme.SUCCESS(), (ratio - 0.5f) * 2.0f)
            : Theme.mix(Theme.DANGER(), 0xFFF5A524, ratio * 2.0f);
        guiGraphics.fill(x, y + h - filled, x + HEALTH_BAR_WIDTH, y + h, Theme.alpha(color, 0.95f * opacity));
    }

    private static void drawNametag(
        GuiGraphics guiGraphics,
        Font font,
        int centerX,
        int bottomY,
        LivingEntity living,
        float distance,
        int accent,
        float opacity
    ) {
        String name = living.getDisplayName() != null
            ? living.getDisplayName().getString()
            : living.getType().getDescription().getString();
        String label = name + "  " + Math.round(distance) + "m";

        int textWidth = ValkyrieFonts.width(font, label);
        int lineHeight = ValkyrieFonts.lineHeight(font);
        int pillW = textWidth + NAME_PAD_X * 2;
        int pillH = lineHeight + NAME_PAD_Y * 2;
        int pillX = centerX - pillW / 2;
        int pillY = bottomY - pillH;
        int radius = Math.max(3, pillH / 2);

        ThunderRender.drawRoundRect(guiGraphics, pillX, pillY, pillW, pillH, radius, Theme.alpha(0xFF0B1220, 0.50f * opacity));
        ThunderRender.drawRoundOutline(guiGraphics, pillX, pillY, pillW, pillH, radius, Theme.alpha(accent, 0.30f * opacity));
        ValkyrieFonts.drawCentered(guiGraphics, font, label, centerX, pillY + NAME_PAD_Y, Theme.alpha(0xFFF3F6FB, opacity));
    }

    /** Экранная рамка цели. */
    private record Box(int left, int top, int right, int bottom) {
        int width() {
            return right - left;
        }

        int height() {
            return bottom - top;
        }
    }
}
