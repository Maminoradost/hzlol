package com.valkyrie.client.gui.thunder;

import com.mojang.blaze3d.platform.NativeImage;
import com.valkyrie.ValkyrieClient;
import com.valkyrie.client.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Низкоуровневый рендер UI: скругления со сглаживанием, панели, тени.
 */
public final class ThunderRender {
    public static final float PANEL_RADIUS = 14.0f;

    /** Шаг анимации для самого первого кадра, когда предыдущего замера ещё нет (~60 FPS). */
    private static final float DEFAULT_FRAME_SECONDS = 1.0f / 60.0f;
    /** Потолок шага анимации: фриз в 2 секунды не должен мгновенно доводить анимации до цели. */
    private static final float MAX_FRAME_SECONDS = 1.0f / 15.0f;
    private static final int MAX_CACHED_RADIUS = 32;
    private static final int TEXTURE_RADIUS_MIN = 4;
    private static final int CORNER_SUPERSAMPLE = 4;
    private static final int[][] ROUND_INSETS = buildRoundInsets();
    private static final ResourceLocation[] ROUND_TEXTURE_IDS = new ResourceLocation[MAX_CACHED_RADIUS + 1];

    private static long lastFrameNs;
    private static float frameSeconds = DEFAULT_FRAME_SECONDS;

    private ThunderRender() {}

    // ─── Утилиты ─────────────────────────────────────────────────────────────

    public static boolean isHovered(int mx, int my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    /**
     * Плавная экспоненциальная анимация, независимая от частоты кадров.
     * <p>
     * При 30 и при 240 FPS значение достигает цели за одинаковое время.
     * {@code speed} — скорость сходимости: чем больше, тем быстрее.
     * <p>
     * Метод вызывается много раз за кадр, поэтому длительность кадра берётся из
     * общего снимка {@link #beginFrame()}, а не замеряется на каждом вызове.
     */
    public static float fast(float current, float target, float speed) {
        if (Math.abs(target - current) < 0.001f) return target;
        float factor = 1.0f - (float) Math.exp(-speed * frameSeconds);
        return current + (target - current) * factor;
    }

    /**
     * Открывает кадр анимации: фиксирует его длительность один раз, чтобы все
     * последующие вызовы {@link #fast} в этом кадре шли с одним и тем же шагом.
     * Вызывается в начале отрисовки каждого экрана и HUD-слоя.
     */
    public static void beginFrame() {
        long now = System.nanoTime();
        long previous = lastFrameNs;
        lastFrameNs = now;
        if (previous == 0L) {
            frameSeconds = DEFAULT_FRAME_SECONDS;
            return;
        }
        // Верхний предел защищает от «телепортации» анимаций после фризов и загрузок.
        frameSeconds = Mth.clamp((now - previous) / 1_000_000_000.0f, 0.0f, MAX_FRAME_SECONDS);
    }

    /** Длительность текущего кадра в секундах — для анимаций по времени, а не по FPS. */
    public static float frameSeconds() {
        return frameSeconds;
    }

    public static int withAlpha(int color, float a) {
        return ((int)(255f * Mth.clamp(a, 0f, 1f)) << 24) | (color & 0x00FFFFFF);
    }

    // ─── Базовые примитивы ────────────────────────────────────────────────────

    /**
     * Скруглённый прямоугольник с попиксельным AA на углах.
     * r=0 → обычный fill; r>0 → плавные скругления.
     */
    public static void drawRoundRect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.max(0, (Math.min(w, h) - 1) / 2));
        if (r <= 0) { g.fill(x, y, x + w, y + h, color); return; }
        if (r >= TEXTURE_RADIUS_MIN && r <= MAX_CACHED_RADIUS) {
            drawTexturedRoundRect(g, x, y, w, h, r, color);
            return;
        }

        int[] insets = insetsFor(r);
        if (h > r * 2) {
            g.fill(x, y + r, x + w, y + h - r, color);
        }
        for (int row = 0; row < r; row++) {
            int inset = insets[row];
            int top = y + row;
            int bottom = y + h - row - 1;
            g.fill(x + inset, top, x + w - inset, top + 1, color);
            g.fill(x + inset, bottom, x + w - inset, bottom + 1, color);
        }
    }

    private static int[][] buildRoundInsets() {
        int[][] cache = new int[MAX_CACHED_RADIUS + 1][];
        for (int radius = 0; radius <= MAX_CACHED_RADIUS; radius++) {
            cache[radius] = buildInsets(radius);
        }
        return cache;
    }

    private static int[] insetsFor(int radius) {
        if (radius <= MAX_CACHED_RADIUS) {
            return ROUND_INSETS[radius];
        }
        return buildInsets(radius);
    }

    private static int[] buildInsets(int radius) {
        int[] insets = new int[Math.max(0, radius)];
        for (int row = 0; row < radius; row++) {
            float dy = radius - row - 0.5f;
            float inset = radius - (float) Math.sqrt(Math.max(0.0f, radius * radius - dy * dy));
            insets[row] = Math.max(0, (int) Math.ceil(inset));
        }
        return insets;
    }

    private static void drawTexturedRoundRect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        ResourceLocation textureId = roundTexture(r);
        int textureSize = r * 2 + 1;
        int middleW = w - r * 2;
        int middleH = h - r * 2;

        blitRound(g, textureId, x, y, r, r, 0, 0, r, r, textureSize, color);
        blitRound(g, textureId, x + r + middleW, y, r, r, r + 1, 0, r, r, textureSize, color);
        blitRound(g, textureId, x, y + r + middleH, r, r, 0, r + 1, r, r, textureSize, color);
        blitRound(g, textureId, x + r + middleW, y + r + middleH, r, r, r + 1, r + 1, r, r, textureSize, color);

        if (middleW > 0) {
            blitRound(g, textureId, x + r, y, middleW, r, r, 0, 1, r, textureSize, color);
            blitRound(g, textureId, x + r, y + r + middleH, middleW, r, r, r + 1, 1, r, textureSize, color);
        }
        if (middleH > 0) {
            blitRound(g, textureId, x, y + r, r, middleH, 0, r, r, 1, textureSize, color);
            blitRound(g, textureId, x + r + middleW, y + r, r, middleH, r + 1, r, r, 1, textureSize, color);
        }
        if (middleW > 0 && middleH > 0) {
            blitRound(g, textureId, x + r, y + r, middleW, middleH, r, r, 1, 1, textureSize, color);
        }
    }

    private static void blitRound(
        GuiGraphics g,
        ResourceLocation textureId,
        int x,
        int y,
        int w,
        int h,
        int u,
        int v,
        int sourceW,
        int sourceH,
        int textureSize,
        int color
    ) {
        if (w <= 0 || h <= 0 || sourceW <= 0 || sourceH <= 0) {
            return;
        }
        g.blit(
            RenderPipelines.GUI_TEXTURED,
            textureId,
            x,
            y,
            u,
            v,
            w,
            h,
            sourceW,
            sourceH,
            textureSize,
            textureSize,
            color
        );
    }

    private static ResourceLocation roundTexture(int radius) {
        ResourceLocation existing = ROUND_TEXTURE_IDS[radius];
        if (existing != null) {
            return existing;
        }

        int size = radius * 2 + 1;
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, size, size, false);
        int samples = CORNER_SUPERSAMPLE * CORNER_SUPERSAMPLE;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int covered = 0;
                for (int sy = 0; sy < CORNER_SUPERSAMPLE; sy++) {
                    for (int sx = 0; sx < CORNER_SUPERSAMPLE; sx++) {
                        float sampleX = x + (sx + 0.5f) / CORNER_SUPERSAMPLE;
                        float sampleY = y + (sy + 0.5f) / CORNER_SUPERSAMPLE;
                        float closestX = Mth.clamp(sampleX, radius, size - radius);
                        float closestY = Mth.clamp(sampleY, radius, size - radius);
                        float dx = sampleX - closestX;
                        float dy = sampleY - closestY;
                        if (dx * dx + dy * dy <= radius * radius) {
                            covered++;
                        }
                    }
                }
                int alpha = Math.round(covered * 255.0f / samples);
                image.setPixelABGR(x, y, (alpha << 24) | 0x00FFFFFF);
            }
        }

        ResourceLocation textureId = ResourceLocation.fromNamespaceAndPath(
            ValkyrieClient.MODID,
            "gui/round_" + radius
        );
        DynamicTexture texture = new DynamicTexture(() -> textureId.toString(), image);
        // Строго nearest: боковые грани растягиваются из полоски шириной 1 тексель,
        // и линейная фильтрация подмешивала бы соседние тексели угла — отсюда
        // размытые полосы вдоль краёв. Сглаживание уже запечено в альфу текстуры.
        texture.setFilter(false, false);
        texture.upload();
        Minecraft.getInstance().getTextureManager().register(textureId, texture);
        ROUND_TEXTURE_IDS[radius] = textureId;
        return textureId;
    }

    public static void drawRound(GuiGraphics g, float x, float y, float w, float h, float r, int color) {
        drawRoundRect(g, (int)x, (int)y, (int)w, (int)h, (int)r, color);
    }

    // ─── Обводка ─────────────────────────────────────────────────────────────

    /**
     * Обводка скруглённого прямоугольника толщиной 1px.
     * Рисуется как разница двух скруглений, поэтому углы остаются сглаженными.
     */
    public static void drawRoundOutline(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        // Прямые грани обрываются на границе скругления: если тянуть их во всю
        // ширину/высоту, концы вылезают за дугу и читаются как полосы у углов.
        r = Math.min(r, Math.max(0, (Math.min(w, h) - 1) / 2));
        g.fill(x + r, y, x + w - r, y + 1, color);
        g.fill(x + r, y + h - 1, x + w - r, y + h, color);
        g.fill(x, y + r, x + 1, y + h - r, color);
        g.fill(x + w - 1, y + r, x + w, y + h - r, color);
        if (r <= 0) {
            return;
        }
        // Дуги углов: покрытие пикселя кольцом толщиной 1px считается
        // суперсэмплингом. Ступенчатая выборка по одному пикселю на строку
        // (прежний вариант) рвала контур и давала зазубрины на больших радиусах.
        int baseAlpha = (color >>> 24) & 0xFF;
        int rgb = color & 0x00FFFFFF;
        float outer = r;
        float inner = r - 1.0f;
        int samples = CORNER_SUPERSAMPLE * CORNER_SUPERSAMPLE;

        for (int py = 0; py < r; py++) {
            for (int px = 0; px < r; px++) {
                int covered = 0;
                for (int sy = 0; sy < CORNER_SUPERSAMPLE; sy++) {
                    for (int sx = 0; sx < CORNER_SUPERSAMPLE; sx++) {
                        // Расстояние от центра скругления до подпикселя.
                        float dx = r - (px + (sx + 0.5f) / CORNER_SUPERSAMPLE);
                        float dy = r - (py + (sy + 0.5f) / CORNER_SUPERSAMPLE);
                        float dist = (float) Math.sqrt(dx * dx + dy * dy);
                        if (dist <= outer && dist >= inner) {
                            covered++;
                        }
                    }
                }
                if (covered == 0) {
                    continue;
                }
                int alpha = Math.round(baseAlpha * covered / (float) samples);
                if (alpha <= 0) {
                    continue;
                }
                int pixel = (alpha << 24) | rgb;
                int left = x + px;
                int right = x + w - px - 1;
                int top = y + py;
                int bottom = y + h - py - 1;
                g.fill(left, top, left + 1, top + 1, pixel);
                g.fill(right, top, right + 1, top + 1, pixel);
                g.fill(left, bottom, left + 1, bottom + 1, pixel);
                g.fill(right, bottom, right + 1, bottom + 1, pixel);
            }
        }
    }

    // ─── Тень ────────────────────────────────────────────────────────────────

    /**
     * Мягкая тень из нескольких расходящихся слоёв.
     * <p>
     * Каждый следующий слой шире и прозрачнее предыдущего, что даёт спад
     * плотности к краям вместо жёсткой границы.
     */
    public static void drawSoftShadow(GuiGraphics g, float x, float y, float w, float h, int spread, int color) {
        drawElevation(g, (int) x, (int) y, (int) w, (int) h, (int) PANEL_RADIUS, Math.max(2, spread), 1.0f);
    }

    /**
     * Тень «подъёма» под панелью.
     *
     * @param elevation высота подъёма в пикселях: чем больше, тем шире и мягче тень
     * @param strength  общий множитель плотности (для затухания вместе с экраном)
     */
    public static void drawElevation(GuiGraphics g, int x, int y, int w, int h, int radius, int elevation, float strength) {
        if (w <= 0 || h <= 0 || strength <= 0.0f) return;
        // Плотность и оттенок тени задаёт активная тема: на тёмном фоне слабая
        // светлая тень не читается вовсе.
        strength *= Theme.shadowStrength();
        int shadowTint = Theme.SHADOW();
        int layers = Mth.clamp(elevation / 2, 2, 5);
        for (int i = layers; i >= 1; i--) {
            float t = i / (float) layers;
            int grow = Math.round(elevation * t);
            int offsetY = Math.round(elevation * t * 0.55f);
            // Ближние слои плотнее: плотность падает квадратично с ростом радиуса.
            float density = 0.052f * (1.0f - t) * (1.0f - t) + 0.014f;
            drawRoundRect(
                g,
                x - grow,
                y - grow / 2 + offsetY,
                w + grow * 2,
                h + grow,
                radius + grow,
                withAlpha(shadowTint, density * strength)
            );
        }
    }

    // ─── Акриловые поверхности ───────────────────────────────────────────────

    /**
     * Акриловая панель: полупрозрачная заливка, светлая верхняя грань и мягкая обводка.
     * <p>
     * Рассчитана на подложку из размытого кадра — вызывающий код должен заранее
     * выполнить {@code GuiGraphics.blurBeforeThisStratum()}, иначе стекло будет
     * выглядеть просто как белый прямоугольник.
     *
     * @param opacity общее затухание (анимация открытия)
     */
    public static void drawAcrylicPanel(GuiGraphics g, int x, int y, int w, int h, int radius, int fill, float opacity) {
        if (w <= 0 || h <= 0 || opacity <= 0.0f) return;

        drawRoundRect(g, x, y, w, h, radius, fade(fill, opacity));
        // Блик и грань берём из палитры: на тёмной теме резкий белый кант
        // выглядит как обводка маркером, а не как стекло.
        int rim = Theme.RIM_LIGHT();
        float rimAlpha = ((rim >>> 24) & 0xFF) / 255.0f;
        // Мягкий вертикальный блик: сверху светлее, книзу сходит на нет.
        int highlightH = Math.max(1, Math.min(h / 2, 26));
        g.fillGradient(
            x + radius / 2,
            y + 1,
            x + w - radius / 2,
            y + highlightH,
            withAlpha(rim, 0.46f * rimAlpha * opacity),
            withAlpha(rim, 0.0f)
        );
        // Верхняя грань стекла и общая обводка.
        drawRoundRect(g, x + radius, y, w - radius * 2, 1, 0, withAlpha(rim, rimAlpha * opacity));
        drawRoundOutline(g, x, y, w, h, radius, withAlpha(Theme.BORDER(), 0.20f * opacity));
    }

    /** Панель с тенью подъёма — основной строительный блок интерфейса. */
    public static void drawFloatingPanel(GuiGraphics g, int x, int y, int w, int h, int radius, int fill, int elevation, float opacity) {
        drawElevation(g, x, y, w, h, radius, elevation, opacity);
        drawAcrylicPanel(g, x, y, w, h, radius, fill, opacity);
    }

    /**
     * Скруглённый прямоугольник, залитый вертикальным градиентом.
     * <p>
     * {@code GuiGraphics.fillGradient} умеет только прямые углы, поэтому форму
     * задаёт скругление, а градиент рисуется полосами внутри него: каждая строка
     * берёт свой цвет и свою ширину из таблицы insets. Так углы остаются
     * сглаженными, а заливка — плавной.
     */
    public static void drawGradientRound(GuiGraphics g, int x, int y, int w, int h, int r, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.max(0, (Math.min(w, h) - 1) / 2));
        int[] insets = insetsFor(r);

        for (int row = 0; row < h; row++) {
            // Вырез угла: сверху берём прямой inset, снизу — зеркальный.
            int inset = 0;
            if (row < r) {
                inset = insets[row];
            } else if (row >= h - r) {
                inset = insets[h - row - 1];
            }
            int color = mix(top, bottom, h == 1 ? 0.0f : row / (float) (h - 1));
            g.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
        }
    }

    /** Горизонтальный градиент акцента — для активных индикаторов и полос. */
    public static void drawAccentBar(GuiGraphics g, int x, int y, int w, int h, int accent, float opacity) {
        if (w <= 0 || h <= 0) return;
        int left = withAlpha(accent, 0.95f * opacity);
        int right = withAlpha(lighten(accent, 0.35f), 0.75f * opacity);
        int mid = x + w / 2;
        g.fillGradient(x, y, mid, y + h, left, right);
        g.fillGradient(mid, y, x + w, y + h, right, left);
    }

    /**
     * Линейная интерполяция двух цветов по всем каналам.
     * <p>
     * Дублирует {@code Theme.mix}, но живёт здесь намеренно: {@code ThunderRender} —
     * низкоуровневый слой и не должен зависеть от слоя дизайн-токенов.
     */
    public static int mix(int from, int to, float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        int a = (int) Mth.lerp(t, (from >>> 24) & 0xFF, (to >>> 24) & 0xFF);
        int r = (int) Mth.lerp(t, (from >>> 16) & 0xFF, (to >>> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (from >>> 8) & 0xFF, (to >>> 8) & 0xFF);
        int b = (int) Mth.lerp(t, from & 0xFF, to & 0xFF);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Осветление цвета к белому. */
    public static int lighten(int color, float amount) {
        amount = Mth.clamp(amount, 0.0f, 1.0f);
        int a = (color >>> 24) & 0xFF;
        int r = (int) Mth.lerp(amount, (color >>> 16) & 0xFF, 255);
        int gg = (int) Mth.lerp(amount, (color >>> 8) & 0xFF, 255);
        int b = (int) Mth.lerp(amount, color & 0xFF, 255);
        return (a << 24) | (r << 16) | (gg << 8) | b;
    }

    /** Умножение существующей альфы. */
    public static int fade(int color, float factor) {
        int a = (int) (((color >>> 24) & 0xFF) * Mth.clamp(factor, 0.0f, 1.0f));
        return (a << 24) | (color & 0x00FFFFFF);
    }
}
