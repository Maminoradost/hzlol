package com.valkyrie.client.hud;

import com.valkyrie.client.combat.KillAuraEngine;
import com.valkyrie.client.config.ValkyrieOptions;
import com.valkyrie.client.config.ValkyrieOptionsManager;
import com.valkyrie.client.gui.notify.Notifications;
import com.valkyrie.client.gui.theme.Animated;
import com.valkyrie.client.gui.theme.Easing;
import com.valkyrie.client.gui.theme.Theme;
import com.valkyrie.client.gui.thunder.ThunderRender;
import com.valkyrie.client.module.ModuleRegistry;
import com.valkyrie.client.module.impl.ArmorModule;
import com.valkyrie.client.module.impl.EffectsModule;
import com.valkyrie.client.module.impl.DamageTintModule;
import com.valkyrie.client.module.impl.HitMarkerModule;
import com.valkyrie.client.module.impl.InfoModule;
import com.valkyrie.client.module.impl.KeystrokesModule;
import com.valkyrie.client.module.impl.TargetHudModule;
import com.valkyrie.client.module.impl.WatermarkModule;
import com.valkyrie.client.render.ValkyrieFonts;
import com.valkyrie.client.render.CombatVisuals;
import com.valkyrie.client.render.ValkyrieLines;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeLayer;
import org.joml.Matrix3x2fStack;

public final class ValkyrieHudOverlay implements ForgeLayer {
    /** Подъём панелей HUD: тень мягкая, чтобы не спорить с игровым фоном. */
    private static final int ELEVATION_PANEL = 4;
    private static final int ELEVATION_KEY = 3;
    /** Клавиши мельче карточек, поэтому радиус свой, а не {@link Theme#RADIUS_CARD}. */
    private static final int RADIUS_KEY = 6;

    // Габариты блоков: единый источник истины для отрисовки и для хитбоксов
    // редактора HUD, иначе панель и её рамка перетаскивания разъезжаются.
    private static final int INFO_WIDTH = 118;
    private static final int INFO_HEIGHT = 42;
    private static final int ARMOR_MIN_WIDTH = 64;
    private static final int ARMOR_HEIGHT = 22;
    /** Ширина слота предмета (16px иконка + воздух до подписи). */
    private static final int ARMOR_ICON_WIDTH = 18;
    private static final int ARMOR_GAP = 8;
    private static final int ARMOR_PAD_X = 7;
    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET,
    };
    private static final int EFFECTS_WIDTH = 102;
    private static final int EFFECTS_ROW_HEIGHT = 12;
    private static final int EFFECTS_MAX_ROWS = 4;
    private static final int EFFECTS_PAD_Y = 4;
    private static final int TARGET_WIDTH = 142;
    private static final int TARGET_HEIGHT = 48;

    // ─── Появление панелей ───────────────────────────────────────────────────

    /** Панель выезжает к своему краю экрана, а не из случайной точки. */
    private static final int SLIDE_LEFT = -1;
    private static final int SLIDE_RIGHT = 1;
    private static final int SLIDE_DOWN = 0;
    private static final float SLIDE_DISTANCE = 18.0f;

    // ─── Спарклайн FPS в watermark ───────────────────────────────────────────

    private static final int GRAPH_WIDTH = 40;
    private static final int GRAPH_HEIGHT = 14;
    private static final int GRAPH_BAR_WIDTH = 1;
    /** Шаг между столбиками: 1px столбик + 1px воздух. */
    private static final int GRAPH_BAR_STEP = 2;
    private static final int GRAPH_GAP = 8;
    /** Интервал выборки в секундах — график покрывает ~4 секунды истории. */
    private static final float GRAPH_SAMPLE_INTERVAL = 1.0f / 5.0f;

    private static final int[] fpsHistory = new int[GRAPH_WIDTH / GRAPH_BAR_STEP];
    private static int graphHead;
    private static int graphFilled;
    private static float graphTimer;

    private float watermarkOpacity;
    private float keyOpacity;
    private float infoOpacity;
    private float armorOpacity;
    private float effectsOpacity;
    private float targetOpacity;
    private LivingEntity targetEntity;
    private long targetSeenAtNs;
    /** Анимация нажатия по каждой клавише: ключ — подпись клавиши. */
    private static final Map<String, Animated> KEY_PRESS = new HashMap<>();
    private static final long SESSION_STARTED_AT = System.currentTimeMillis();

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        ValkyrieOptions options = ValkyrieOptionsManager.OPTIONS;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) {
            // Поверх экранов HUD не нужен: там свой рендер, включая тосты.
            return;
        }

        Font font = minecraft.font;
        int height = guiGraphics.guiHeight();
        int accent = Theme.accent();
        float hudOpacity = Theme.hudOpacity();

        // HUD рисуется только когда экран закрыт, поэтому кадр анимации здесь
        // открывается без конфликта с экранами Valkyrie.
        ThunderRender.beginFrame();

        // Уведомления не зависят от модулей HUD: они сообщают в том числе о том,
        // что блок только что выключили.
        com.valkyrie.client.gui.notify.ValkyrieQuotes.render(guiGraphics, font, ThunderRender.frameSeconds());
        Notifications.render(guiGraphics, font);

        InfoModule info = ModuleRegistry.get(InfoModule.class);
        boolean focused = ModuleRegistry.isEnabled(com.valkyrie.client.module.impl.FocusModeModule.class);
        watermarkOpacity = ThunderRender.fast(watermarkOpacity, !focused && ModuleRegistry.isEnabled(WatermarkModule.class) ? 1.0f : 0.0f, 12.0f);
        keyOpacity = ThunderRender.fast(keyOpacity, !focused && ModuleRegistry.isEnabled(KeystrokesModule.class) ? 1.0f : 0.0f, 12.0f);
        infoOpacity = ThunderRender.fast(infoOpacity, !focused && info.isEnabled() && info.hasContent() ? 1.0f : 0.0f, 12.0f);
        armorOpacity = ThunderRender.fast(armorOpacity, !focused && ModuleRegistry.isEnabled(ArmorModule.class) ? 1.0f : 0.0f, 12.0f);
        effectsOpacity = ThunderRender.fast(effectsOpacity, !focused && ModuleRegistry.isEnabled(EffectsModule.class) ? 1.0f : 0.0f, 12.0f);

        TargetHudModule targetHud = ModuleRegistry.get(TargetHudModule.class);
        LivingEntity currentTarget = KillAuraEngine.target();
        if (currentTarget != null) {
            targetEntity = currentTarget;
            targetSeenAtNs = System.nanoTime();
        }
        boolean targetLingering = targetEntity != null && System.nanoTime() - targetSeenAtNs < 500_000_000L;
        targetOpacity = ThunderRender.fast(targetOpacity, targetHud.isEnabled() && (currentTarget != null || targetLingering) ? 1.0f : 0.0f, 10.0f);

        if (watermarkOpacity > 0.01f) {
            slideIn(guiGraphics, watermarkOpacity, SLIDE_LEFT);
            renderWatermark(guiGraphics, font, minecraft, accent, watermarkOpacity * hudOpacity, options);
            guiGraphics.pose().popMatrix();
        }

        if (keyOpacity > 0.01f && minecraft.player != null) {
            slideIn(guiGraphics, keyOpacity, SLIDE_LEFT);
            renderKeystrokes(guiGraphics, font, minecraft, height, accent, keyOpacity * hudOpacity, options);
            guiGraphics.pose().popMatrix();
        }

        if (infoOpacity > 0.01f && minecraft.player != null) {
            slideIn(guiGraphics, infoOpacity, SLIDE_LEFT);
            renderInfo(guiGraphics, font, minecraft, accent, infoOpacity * hudOpacity, options);
            guiGraphics.pose().popMatrix();
        }

        if (armorOpacity > 0.01f && minecraft.player != null) {
            slideIn(guiGraphics, armorOpacity, SLIDE_DOWN);
            renderArmor(guiGraphics, font, minecraft, guiGraphics.guiWidth(), height, accent, armorOpacity * hudOpacity, options);
            guiGraphics.pose().popMatrix();
        }

        if (effectsOpacity > 0.01f && minecraft.player != null) {
            slideIn(guiGraphics, effectsOpacity, SLIDE_RIGHT);
            renderEffects(guiGraphics, font, minecraft, guiGraphics.guiWidth(), accent, effectsOpacity * hudOpacity, options);
            guiGraphics.pose().popMatrix();
        }

        if (targetOpacity > 0.01f && targetEntity != null && minecraft.player != null) {
            slideIn(guiGraphics, targetOpacity, SLIDE_DOWN);
            renderTargetHud(guiGraphics, font, minecraft, targetHud, targetEntity, accent, targetOpacity * hudOpacity, options);
            guiGraphics.pose().popMatrix();
        } else if (targetOpacity <= 0.01f && !targetLingering) {
            targetEntity = null;
        }

        renderCombatVisuals(guiGraphics, minecraft, accent);
    }

    /** Ненавязчивые боевые визуалы поверх HUD: маркер удара и рамка урона. */
    private static void renderCombatVisuals(GuiGraphics guiGraphics, Minecraft minecraft, int accent) {
        HitMarkerModule marker = ModuleRegistry.get(HitMarkerModule.class);
        float hit = marker.isEnabled() ? CombatVisuals.hitFade() : 0.0f;
        if (hit > 0.01f) {
            float size = marker.size.value();
            float gap = 2.5f;
            float cx = guiGraphics.guiWidth() * 0.5f;
            float cy = guiGraphics.guiHeight() * 0.5f;
            int color = Theme.alpha(accent, hit * 0.72f);
            ValkyrieLines.drawLine(guiGraphics, cx - gap - size, cy - gap - size, cx - gap, cy - gap, color);
            ValkyrieLines.drawLine(guiGraphics, cx + gap, cy - gap, cx + gap + size, cy - gap - size, color);
            ValkyrieLines.drawLine(guiGraphics, cx - gap - size, cy + gap + size, cx - gap, cy + gap, color);
            ValkyrieLines.drawLine(guiGraphics, cx + gap, cy + gap, cx + gap + size, cy + gap + size, color);
        }

        DamageTintModule tint = ModuleRegistry.get(DamageTintModule.class);
        if (tint.isEnabled() && minecraft.player != null && minecraft.player.hurtTime > 0) {
            float hurt = Mth.clamp(minecraft.player.hurtTime / 10.0f, 0.0f, 1.0f);
            int color = Theme.alpha(Theme.DANGER(), hurt * tint.opacity.value());
            int w = guiGraphics.guiWidth();
            int h = guiGraphics.guiHeight();
            int edge = 3;
            guiGraphics.fill(0, 0, w, edge, color);
            guiGraphics.fill(0, h - edge, w, h, color);
            guiGraphics.fill(0, edge, edge, h - edge, color);
            guiGraphics.fill(w - edge, edge, w, h - edge, color);
        }
    }

    /** Sakura-карточка текущей цели KillAura. */
    private static void renderTargetHud(
        GuiGraphics guiGraphics,
        Font font,
        Minecraft minecraft,
        TargetHudModule module,
        LivingEntity target,
        int accent,
        float opacity,
        ValkyrieOptions options
    ) {
        int x = options.hudTargetX >= 0 ? options.hudTargetX : (guiGraphics.guiWidth() - TARGET_WIDTH) / 2;
        int y = options.hudTargetY >= 0 ? options.hudTargetY : guiGraphics.guiHeight() - 112;

        ThunderRender.drawFloatingPanel(guiGraphics, x, y, TARGET_WIDTH, TARGET_HEIGHT, Theme.RADIUS_CARD, Theme.HUD_SURFACE(), ELEVATION_PANEL, opacity);
        ThunderRender.drawAccentBar(guiGraphics, x + 44, y + TARGET_HEIGHT - 1, TARGET_WIDTH - 54, 1, accent, 0.32f * opacity);

        int contentX = x + 9;
        if (module.model.value()) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                guiGraphics,
                x + 4,
                y + 3,
                x + 39,
                y + TARGET_HEIGHT - 3,
                20,
                0.0625f,
                x + 21.5f,
                y + 24.0f,
                target
            );
            contentX = x + 45;
        }

        String name = target.getDisplayName().getString();
        int maxNameWidth = x + TARGET_WIDTH - 8 - contentX;
        while (ValkyrieFonts.width(font, name) > maxNameWidth && name.length() > 3) {
            name = name.substring(0, name.length() - 2) + "…";
        }
        ValkyrieFonts.draw(guiGraphics, font, name, contentX, y + 8, Theme.alpha(Theme.TEXT_PRIMARY(), opacity));

        float health = Math.max(0.0f, target.getHealth());
        float maxHealth = Math.max(1.0f, target.getMaxHealth());
        float healthRatio = Mth.clamp(health / maxHealth, 0.0f, 1.0f);
        String info = Math.round(health) + " / " + Math.round(maxHealth) + " hp";
        if (module.distance.value()) {
            double distance = minecraft.player.position().distanceTo(target.position());
            info += "  ·  " + Math.round(distance) + "m";
        }
        ValkyrieFonts.draw(guiGraphics, font, info, contentX, y + 22, Theme.alpha(Theme.TEXT_SECONDARY(), opacity * 0.88f));

        int barX = contentX;
        int barY = y + 36;
        int barW = x + TARGET_WIDTH - 9 - barX;
        ThunderRender.drawRoundRect(guiGraphics, barX, barY, barW, 3, 2, Theme.alpha(Theme.TRACK_OFF(), opacity * 0.65f));
        int fillW = Math.max(1, Math.round(barW * healthRatio));
        int healthColor = healthRatio < 0.30f ? Theme.DANGER() : Theme.mix(accent, Theme.SUCCESS(), healthRatio * 0.28f);
        ThunderRender.drawRoundRect(guiGraphics, barX, barY, fillW, 3, 2, Theme.alpha(healthColor, opacity * 0.82f));
    }

    /**
     * Смещает панель к её краю экрана на время появления.
     * <p>
     * Сдвиг делается матрицей, а не правкой координат в каждом {@code render*}:
     * так позиции блоков и хитбоксы редактора остаются нетронутыми.
     * Вызывающий обязан сделать {@code popMatrix()} после отрисовки.
     *
     * @param direction единичный вектор направления выезда по X (или 0 для вертикали)
     */
    private static void slideIn(GuiGraphics guiGraphics, float progress, int direction) {
        float offset = (1.0f - Easing.outQuint(Mth.clamp(progress, 0.0f, 1.0f))) * SLIDE_DISTANCE;
        Matrix3x2fStack pose = guiGraphics.pose();
        pose.pushMatrix();
        if (direction == SLIDE_DOWN) {
            pose.translate(0.0f, offset);
        } else {
            pose.translate(offset * direction, 0.0f);
        }
    }

    public static int resolveWatermarkWidth(Font font, Minecraft minecraft) {
        WatermarkModule module = ModuleRegistry.get(WatermarkModule.class);
        String name = "Valkyrie";
        int paddingX = 7;
        int text = ValkyrieFonts.width(font, name);
        if (module.showFps.value()) {
            text = Math.max(text, ValkyrieFonts.width(font, minecraft.getFps() + " fps"));
        }
        if (!module.showGraph.value() || !module.showFps.value()) {
            return text + paddingX * 2;
        }
        // График живёт справа от текста, поэтому входит в ширину панели —
        // иначе он вылезал бы за скругление, как это было с панелью брони.
        return text + GRAPH_GAP + GRAPH_WIDTH + paddingX * 2;
    }

    /**
     * Кольцевой буфер истории FPS.
     * <p>
     * Пишем не каждый кадр, а раз в {@link #GRAPH_SAMPLE_INTERVAL}: иначе на
     * 300 FPS график отражал бы четверть секунды и дёргался.
     */
    private static void sampleFps(Minecraft minecraft) {
        graphTimer += ThunderRender.frameSeconds();
        if (graphTimer < GRAPH_SAMPLE_INTERVAL) {
            return;
        }
        graphTimer = 0.0f;
        fpsHistory[graphHead] = minecraft.getFps();
        graphHead = (graphHead + 1) % fpsHistory.length;
        if (graphFilled < fpsHistory.length) {
            graphFilled++;
        }
    }

    /** Спарклайн истории FPS: столбики с высотой по нормализованному значению. */
    private static void renderFpsGraph(GuiGraphics guiGraphics, int x, int y, int accent, float opacity) {
        if (graphFilled < 2) {
            return;
        }

        int peak = 1;
        for (int value : fpsHistory) {
            peak = Math.max(peak, value);
        }
        // Шкала округляется вверх до шага, чтобы график не прыгал от каждого кадра.
        int scale = Math.max(60, ((peak + 29) / 30) * 30);

        int bars = Math.min(GRAPH_WIDTH / GRAPH_BAR_STEP, graphFilled);
        for (int i = 0; i < bars; i++) {
            // Идём от свежих значений к старым: свежие рисуем справа.
            int index = Math.floorMod(graphHead - 1 - i, fpsHistory.length);
            int value = fpsHistory[index];
            if (value <= 0) {
                continue;
            }
            int barHeight = Math.max(1, Math.round(GRAPH_HEIGHT * Mth.clamp(value / (float) scale, 0.0f, 1.0f)));
            int barX = x + GRAPH_WIDTH - (i + 1) * GRAPH_BAR_STEP;
            int barY = y + GRAPH_HEIGHT - barHeight;
            // Старые столбики бледнее — получается «шлейф» вместо ровной стены.
            float fade = 1.0f - i / (float) bars * 0.55f;
            guiGraphics.fill(
                barX,
                barY,
                barX + GRAPH_BAR_WIDTH,
                y + GRAPH_HEIGHT,
                Theme.alpha(accent, 0.75f * fade * opacity)
            );
        }
    }

    public static int watermarkHeight() {
        return 28;
    }

    public static int keystrokesWidth() {
        return 57;
    }

    public static int keystrokesHeight() {
        return 57;
    }

    public static int infoWidth() {
        return INFO_WIDTH;
    }

    public static int infoHeight() {
        return INFO_HEIGHT;
    }

    /** Ширина панели брони для хитбокса редактора: зависит от текущей экипировки. */
    public static int armorWidth(Font font, Minecraft minecraft) {
        return resolveArmorWidth(font, minecraft);
    }

    public static int armorHeight() {
        return ARMOR_HEIGHT;
    }

    public static int effectsWidth() {
        return EFFECTS_WIDTH;
    }

    public static int targetWidth() {
        return TARGET_WIDTH;
    }

    public static int targetHeight() {
        return TARGET_HEIGHT;
    }

    /** Высота панели эффектов при полном списке — редактор двигает блок в максимуме. */
    public static int effectsHeight() {
        return effectsHeight(EFFECTS_MAX_ROWS);
    }

    private static int effectsHeight(int rows) {
        return EFFECTS_PAD_Y * 2 + rows * EFFECTS_ROW_HEIGHT;
    }

    private static void renderWatermark(GuiGraphics guiGraphics, Font font, Minecraft minecraft, int accent, float opacity, ValkyrieOptions options) {
        WatermarkModule module = ModuleRegistry.get(WatermarkModule.class);
        String name = "Valkyrie";
        int x = options.hudWatermarkX;
        int y = options.hudWatermarkY;
        int paddingX = 7;
        int width = resolveWatermarkWidth(font, minecraft);
        int height = watermarkHeight();

        ThunderRender.drawFloatingPanel(guiGraphics, x, y, width, height, Theme.RADIUS_CARD, Theme.HUD_SURFACE(), ELEVATION_PANEL, opacity);
        ThunderRender.drawAccentBar(guiGraphics, x + 8, y + height - 1, width - 16, 1, accent, 0.34f * opacity);

        boolean fps = module.showFps.value();
        // Без счётчика заголовок центрируется по высоте: иначе внизу зияет пустая полоса.
        int titleY = fps ? y + 5 : y + (height - ValkyrieFonts.lineHeight(font)) / 2;
        ValkyrieFonts.drawTitle(guiGraphics, font, name, x + paddingX, titleY, Theme.alpha(Theme.TEXT_PRIMARY(), opacity));
        if (fps) {
            ValkyrieFonts.draw(
                guiGraphics, font, minecraft.getFps() + " fps", x + paddingX, y + 16,
                Theme.alpha(Theme.TEXT_SECONDARY(), 0.88f * opacity)
            );
        }

        if (fps && module.showGraph.value()) {
            sampleFps(minecraft);
            renderFpsGraph(
                guiGraphics,
                x + width - paddingX - GRAPH_WIDTH,
                y + (height - GRAPH_HEIGHT) / 2,
                accent,
                opacity
            );
        }
    }

    private static void renderKeystrokes(GuiGraphics guiGraphics, Font font, Minecraft minecraft, int screenHeight, int accent, float opacity, ValkyrieOptions options) {
        int box = 17;
        int gap = 3;
        int x = options.hudKeysX;
        int y = options.hudKeysY >= 0 ? options.hudKeysY : screenHeight - 10 - keystrokesHeight();

        drawKey(guiGraphics, font, x + box + gap, y, box, box, minecraft.options.keyUp, "W", accent, opacity);
        drawKey(guiGraphics, font, x, y + box + gap, box, box, minecraft.options.keyLeft, "A", accent, opacity);
        drawKey(guiGraphics, font, x + box + gap, y + box + gap, box, box, minecraft.options.keyDown, "S", accent, opacity);
        drawKey(guiGraphics, font, x + (box + gap) * 2, y + box + gap, box, box, minecraft.options.keyRight, "D", accent, opacity);

        int mouseY = y + (box + gap) * 2;
        int mouseWidth = box + 9;
        drawMouse(guiGraphics, font, x, mouseY, mouseWidth, box, minecraft.mouseHandler.isLeftPressed(), "LMB", accent, opacity);
        drawMouse(guiGraphics, font, x + mouseWidth + gap, mouseY, mouseWidth, box, minecraft.mouseHandler.isRightPressed(), "RMB", accent, opacity);
    }

    private static void renderInfo(GuiGraphics guiGraphics, Font font, Minecraft minecraft, int accent, float opacity, ValkyrieOptions options) {
        int x = options.hudInfoX;
        int y = options.hudInfoY;
        int width = INFO_WIDTH;
        int height = INFO_HEIGHT;
        ThunderRender.drawFloatingPanel(guiGraphics, x, y, width, height, Theme.RADIUS_CARD, Theme.HUD_SURFACE(), ELEVATION_PANEL, opacity);
        ThunderRender.drawAccentBar(guiGraphics, x + 8, y + height - 1, width - 16, 1, accent, 0.28f * opacity);

        InfoModule module = ModuleRegistry.get(InfoModule.class);
        int line = y + 5;
        if (module.coords.value()) {
            ValkyrieFonts.draw(guiGraphics, font, "XYZ " + blockPos(minecraft), x + 7, line, Theme.alpha(Theme.TEXT_PRIMARY(), opacity));
            line += 11;
            ValkyrieFonts.draw(guiGraphics, font, "Ping " + ping(minecraft) + " ms", x + 7, line, Theme.alpha(Theme.TEXT_SECONDARY(), 0.9f * opacity));
            line += 11;
        }
        if (module.session.value()) {
            ValkyrieFonts.draw(guiGraphics, font, "Session " + sessionTime(), x + 7, line, Theme.alpha(Theme.TEXT_SECONDARY(), 0.9f * opacity));
        }
    }

    private static void renderArmor(GuiGraphics guiGraphics, Font font, Minecraft minecraft, int screenWidth, int screenHeight, int accent, float opacity, ValkyrieOptions options) {
        // Ширину считаем по фактическому содержимому: при полной экипировке
        // фиксированных ARMOR_WIDTH не хватало и проценты вылезали за панель.
        int width = resolveArmorWidth(font, minecraft);
        int height = ARMOR_HEIGHT;
        int x = options.hudArmorX >= 0 ? options.hudArmorX : (screenWidth - width) / 2;
        int y = options.hudArmorY >= 0 ? options.hudArmorY : screenHeight - 50;
        ThunderRender.drawFloatingPanel(guiGraphics, x, y, width, height, Theme.RADIUS_CARD, Theme.HUD_SURFACE(), ELEVATION_PANEL, opacity);

        int itemX = x + ARMOR_PAD_X;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = minecraft.player.getItemBySlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            guiGraphics.renderItem(stack, itemX, y + 3);
            itemX += ARMOR_ICON_WIDTH;
            if (stack.isDamageableItem()) {
                int durability = durabilityPercent(stack);
                int durabilityColor = durability < 35 ? Theme.DANGER() : Theme.mix(Theme.TEXT_PRIMARY(), accent, 0.45f);
                String label = durability + "%";
                ValkyrieFonts.draw(guiGraphics, font, label, itemX, y + 7, Theme.alpha(durabilityColor, opacity));
                itemX += ValkyrieFonts.width(font, label) + ARMOR_GAP;
            } else {
                itemX += ARMOR_GAP;
            }
        }
    }

    private static int durabilityPercent(ItemStack stack) {
        return Math.round((1.0f - stack.getDamageValue() / (float) stack.getMaxDamage()) * 100.0f);
    }

    /** Ширина панели брони по фактически надетым предметам и длине подписей. */
    public static int resolveArmorWidth(Font font, Minecraft minecraft) {
        if (minecraft.player == null) {
            return ARMOR_MIN_WIDTH;
        }
        int content = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = minecraft.player.getItemBySlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            content += ARMOR_ICON_WIDTH;
            if (stack.isDamageableItem()) {
                content += ValkyrieFonts.width(font, durabilityPercent(stack) + "%");
            }
            content += ARMOR_GAP;
        }
        if (content == 0) {
            return ARMOR_MIN_WIDTH;
        }
        // Последний GAP — это правый внутренний отступ, второй раз его не добавляем.
        return Math.max(ARMOR_MIN_WIDTH, content + ARMOR_PAD_X * 2 - ARMOR_GAP);
    }

    private static void renderEffects(GuiGraphics guiGraphics, Font font, Minecraft minecraft, int screenWidth, int accent, float opacity, ValkyrieOptions options) {
        int width = EFFECTS_WIDTH;
        int rowHeight = EFFECTS_ROW_HEIGHT;
        int x = options.hudEffectsX >= 0 ? options.hudEffectsX : screenWidth - width - 10;
        int y = options.hudEffectsY;
        int count = Math.min(EFFECTS_MAX_ROWS, minecraft.player.getActiveEffects().size());
        if (count <= 0) {
            return;
        }
        // Панель ужимается под фактическое число эффектов, но редактор резервирует
        // место под максимум — так рамка не прыгает при смене состава баффов.
        int height = effectsHeight(count);
        ThunderRender.drawFloatingPanel(guiGraphics, x, y, width, height, Theme.RADIUS_CARD, Theme.HUD_SURFACE(), ELEVATION_PANEL, opacity);
        ThunderRender.drawAccentBar(guiGraphics, x + 8, y + height - 1, width - 16, 1, accent, 0.25f * opacity);
        int line = y + 5;
        int rendered = 0;
        for (MobEffectInstance effect : minecraft.player.getActiveEffects()) {
            if (rendered >= EFFECTS_MAX_ROWS) {
                break;
            }
            String name = effect.getEffect().value().getDisplayName().getString();
            if (name.length() > 12) {
                name = name.substring(0, 12);
            }
            ValkyrieFonts.draw(guiGraphics, font, name, x + 7, line, Theme.alpha(Theme.TEXT_PRIMARY(), opacity));
            ValkyrieFonts.draw(guiGraphics, font, formatDuration(effect.getDuration()), x + width - 34, line, Theme.alpha(Theme.TEXT_SECONDARY(), 0.85f * opacity));
            line += rowHeight;
            rendered++;
        }
    }

    private static void drawKey(GuiGraphics guiGraphics, Font font, int x, int y, int width, int height, KeyMapping key, String label, int accent, float opacity) {
        drawBox(guiGraphics, font, x, y, width, height, key != null && key.isDown(), label, accent, opacity);
    }

    private static void drawMouse(GuiGraphics guiGraphics, Font font, int x, int y, int width, int height, boolean pressed, String label, int accent, float opacity) {
        drawBox(guiGraphics, font, x, y, width, height, pressed, label, accent, opacity);
    }

    /**
     * Клавиша keystrokes: акриловая карточка, которая «утапливается» и наливается
     * акцентом при нажатии. Состояние анимации хранится по подписи клавиши, поэтому
     * отпускание тоже проигрывается плавно, а не обрывается.
     */
    private static void drawBox(GuiGraphics guiGraphics, Font font, int x, int y, int width, int height, boolean pressed, String label, int accent, float opacity) {
        float press = KEY_PRESS
            .computeIfAbsent(label, key -> new Animated(0.0f, Theme.SPEED_INSTANT))
            .update(pressed);

        // Нажатая клавиша садится на пиксель вниз и теряет часть тени — как настоящая.
        int sink = Math.round(press);
        int elevation = Math.max(1, Math.round(ELEVATION_KEY * (1.0f - press * 0.6f)));
        int fill = Theme.mix(Theme.HUD_KEY_SURFACE(), Theme.alpha(accent, 0.32f), press * 0.42f);

        ThunderRender.drawFloatingPanel(
            guiGraphics,
            x,
            y + sink,
            width,
            height,
            RADIUS_KEY,
            fill,
            elevation,
            opacity
        );

        if (press > 0.01f) {
            ThunderRender.drawRoundOutline(
                guiGraphics,
                x,
                y + sink,
                width,
                height,
                RADIUS_KEY,
                Theme.alpha(accent, 0.34f * press * opacity)
            );
        }

        int labelWidth = ValkyrieFonts.width(font, label);
        int textX = x + (width - labelWidth) / 2;
        int textY = y + sink + (height - ValkyrieFonts.lineHeight(font)) / 2 + 1;
        int textColor = Theme.mix(Theme.TEXT_SECONDARY(), Theme.mix(Theme.TEXT_PRIMARY(), accent, 0.42f), press);
        ValkyrieFonts.draw(guiGraphics, font, label, textX, textY, Theme.alpha(textColor, opacity));
    }

    private static String blockPos(Minecraft minecraft) {
        return minecraft.player.getBlockX() + " " + minecraft.player.getBlockY() + " " + minecraft.player.getBlockZ();
    }

    private static int ping(Minecraft minecraft) {
        if (minecraft.getConnection() == null || minecraft.player == null) {
            return 0;
        }
        PlayerInfo info = minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID());
        return info == null ? 0 : info.getLatency();
    }

    private static String sessionTime() {
        long totalSeconds = Math.max(0L, (System.currentTimeMillis() - SESSION_STARTED_AT) / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return minutes + "m " + seconds + "s";
    }

    private static String formatDuration(int ticks) {
        int seconds = Math.max(0, ticks / 20);
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }
}
