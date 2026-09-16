package com.valkyrie.client.hud;

import com.mojang.logging.LogUtils;
import com.valkyrie.client.gui.theme.Theme;
import com.valkyrie.client.gui.thunder.ThunderRender;
import com.valkyrie.client.module.ModuleRegistry;
import com.valkyrie.client.module.impl.TracersModule;
import com.valkyrie.client.render.ValkyrieFonts;
import com.valkyrie.client.render.ValkyrieLines;
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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeLayer;
import org.joml.Matrix4f;
import org.slf4j.Logger;

public final class ValkyrieTracersOverlay implements ForgeLayer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_TRACERS = 32;
    private static final int MAX_DISTANCE_LABELS = 12;
    /** Подложка пилюли: плотнее панелей ClickGUI, потому что под ней нет размытия. */
    private static final int LABEL_SURFACE = 0xFFFCFDFF;
    private static final int LABEL_PAD_X = 5;
    private static final int LABEL_PAD_Y = 3;
    /** Отступ, за который прижимаются указатели на цели вне поля зрения. */
    private static final int EDGE_PADDING = 14;
    private static boolean loggedFirstRender = false;

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        TracersModule module = ModuleRegistry.get(TracersModule.class);
        if (!module.isEnabled()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null || minecraft.options.hideGui) {
            return;
        }

        if (!loggedFirstRender) {
            loggedFirstRender = true;
            LOGGER.info(
                "[Valkyrie] Tracers overlay enabled (players={}, hostiles={}, passives={}, range={})",
                module.targets.players(),
                module.targets.hostiles(),
                module.targets.passives(),
                module.range.value()
            );
        }

        float partialTick = ValkyrieProjection.renderPartialTick(deltaTracker.getGameTimeDeltaPartialTick(false));
        Matrix4f matrix = ValkyrieProjection.viewProjection(partialTick);
        if (matrix == null) {
            return;
        }

        LocalPlayer player = minecraft.player;
        int guiWidth = guiGraphics.guiWidth();
        int guiHeight = guiGraphics.guiHeight();
        int startX = guiWidth / 2;
        int startY = guiHeight / 2;
        int rendered = 0;
        float range = module.range.value();
        float baseOpacity = module.opacity.value();
        int tracerColor = module.color.value();

        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (rendered >= MAX_TRACERS) {
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

            // Целимся в середину хитбокса: у ног линия упирается в землю и теряется.
            Vec3 aim = living.getPosition(partialTick).add(0.0, living.getBbHeight() * 0.5, 0.0);
            ScreenPos point = ValkyrieProjection.projectClamped(matrix, aim, guiWidth, guiHeight, EDGE_PADDING);
            if (point == null) {
                continue;
            }

            float distance = Mth.sqrt((float) distanceSqr);
            float distanceFade = 1.0f - Mth.clamp(distance / range, 0.0f, 1.0f) * 0.78f;
            int color = Theme.alpha(tracerColor, baseOpacity * distanceFade);

            int pointX = Math.round(point.x());
            int pointY = Math.round(point.y());
            ValkyrieLines.drawLine(guiGraphics, startX, startY, pointX, pointY, color);
            // Подпись только у видимых целей: у прижатых к краю она врезается в рамку.
            if (module.distanceLabels.value() && rendered < MAX_DISTANCE_LABELS && point.visible()) {
                float labelOpacity = Mth.clamp(baseOpacity * distanceFade, 0.0f, 1.0f);
                drawDistance(guiGraphics, minecraft.font, pointX, pointY, distance, tracerColor, labelOpacity);
            }
            rendered++;
        }
    }

    /**
     * Пилюля с дистанцией. Как и остальной HUD, рисуется поверх мира без размытия,
     * поэтому подложка плотная; акцент трассера проступает только в обводке и тексте,
     * чтобы цифры оставались читаемыми на любом фоне.
     */
    private static void drawDistance(GuiGraphics guiGraphics, Font font, int x, int y, float distance, int tracerColor, float opacity) {
        if (opacity <= 0.01f) {
            return;
        }

        String label = Math.round(distance) + "m";
        int width = ValkyrieFonts.width(font, label);
        int labelX = x - width / 2;
        int labelY = y + 7;

        int pillX = labelX - LABEL_PAD_X;
        int pillY = labelY - LABEL_PAD_Y;
        int pillW = width + LABEL_PAD_X * 2;
        int pillH = font.lineHeight + LABEL_PAD_Y * 2 - 1;
        int radius = Math.max(3, pillH / 2);

        ThunderRender.drawRoundRect(guiGraphics, pillX, pillY, pillW, pillH, radius, Theme.alpha(LABEL_SURFACE, 0.66f * opacity));
        ThunderRender.drawRoundOutline(guiGraphics, pillX, pillY, pillW, pillH, radius, Theme.alpha(tracerColor, 0.26f * opacity));

        int textColor = Theme.mix(Theme.TEXT_PRIMARY(), tracerColor, 0.55f);
        ValkyrieFonts.draw(guiGraphics, font, label, labelX, labelY, Theme.alpha(textColor, opacity));
    }
}
