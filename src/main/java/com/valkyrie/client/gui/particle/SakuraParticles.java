package com.valkyrie.client.gui.particle;

import com.valkyrie.client.gui.thunder.ThunderRender;
import com.valkyrie.client.module.ModuleRegistry;
import com.valkyrie.client.module.impl.AppearanceModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;

/**
 * Падающие лепестки сакуры — мягкий фоновый эффект для меню и ClickGUI.
 * <p>
 * Лепесток — три повёрнутых залитых прямоугольника разной ширины: широкий
 * центр и сужающиеся края. Вместе они дают вытянутую «каплю», похожую на
 * настоящий лепесток, а не на пиксель. Поворот и масштаб — через pose-матрицу
 * {@code Matrix3x2fStack}: {@code fill} копирует текущую матрицу в элемент, so
 * поворот применяется к геометрии.
 * <p>
 * Полёт по диагонали: горизонтальная скорость постоянна («ветер»), вертикальная
 * — падение, плюс синусоидальный снос. Лепестки разлетаются по всему экрану,
 * respawn — сверху с разбросом по X.
 * <p>
 * Один пастельный розовый цвет + светлый вариант; мерцание прозрачности даёт
 * «дыхание». Система хранится на классе: экраны приходят и уходят, ветер живёт.
 */
public final class SakuraParticles {
    /** Розовый лепесток: пастельный, мягкий, читается на светлом фоне. */
    private static final int PETAL = 0xFFE8B4C8;
    /** Зеркальный более светлый тон — каждый третий лепесток чуть светлее. */
    private static final int PETAL_LIGHT = 0xFFF2CEDE;

    /** Длина лепестка вдоль оси. 7 пикселей — заметно, но не громоздко. */
    private static final float LEN = 7.0f;
    /** Ширина центрального сегмента. */
    private static final float W = 3.2f;

    /** Плотность: не больше одного лепестка на ~5000 пикселей площади экрана. */
    private static final int AREA_PER = 5000;

    /** Список живых лепестков. Пересоздаётся только при смене разрешения. */
    private static final List<Petal> PETALS = new ArrayList<>();
    private static int lastWidth = -1;
    private static int lastHeight = -1;
    private static String lastDensity = "";
    private static float elapsed;

    private SakuraParticles() {
    }

    /** Рисует лепестки и продвигает их по времени кадра. */
    public static void render(GuiGraphics gg, float partialTick, float alpha) {
        AppearanceModule appearance = ModuleRegistry.get(AppearanceModule.class);
        if (appearance.particles.is("Off")) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        if (w != lastWidth || h != lastHeight || !lastDensity.equals(appearance.particles.value())) {
            lastWidth = w;
            lastHeight = h;
            lastDensity = appearance.particles.value();
            rebuild(w, h, appearance.particles.is("Low") ? 0.55f : 1.0f);
        }

        float t = ThunderRender.frameSeconds();
        elapsed += t;
        float motion = appearance.reducedMotion.value() ? 0.45f : 1.0f;
        float wind = appearance.sakuraMotion.is("Windy") ? 1.55f : 1.0f;
        // Медленный порыв раз в несколько секунд, а не постоянный поток.
        float gust = 0.72f + 0.28f * ((Mth.sin(elapsed * 0.42f) + 1.0f) * 0.5f);
        for (Petal p : PETALS) {
            p.update(t * motion, wind * gust);
            p.draw(gg, alpha);
        }
    }

    /** Сброс при смене разрешения: новая плотность для новой площади. */
    private static void rebuild(int w, int h, float density) {
        int count = Mth.clamp(Math.round((w * h) / (float) AREA_PER * density), 10, 140);
        PETALS.clear();
        for (int i = 0; i < count; i++) {
            PETALS.add(new Petal(w, h));
        }
    }

    /** Один лепесток: позиция, диагональная скорость, волна, поворот, мерцание. */
    private static final class Petal {
        float x;
        float y;
        float vy;
        float vx;
        float wavePhase;
        float waveAmp;
        float rot;
        float rotSpeed;
        float flickerPhase;
        float flickerSpeed;
        float depth;
        int color;
        int screenWidth;
        int screenHeight;

        Petal(int w, int h) {
            screenWidth = w;
            screenHeight = h;
            // Три слоя: дальний, основной, передний.
            double layerRoll = Math.random();
            depth = layerRoll < 0.58 ? 0.55f : layerRoll < 0.90 ? 1.0f : 1.45f;
            x = (float) (Math.random() * w);
            y = (float) (Math.random() * h);
            // Падение вниз.
            vy = (7.0f + (float) (Math.random() * 10.0f)) * depth;
            // Ветер вбок: постоянный снос + индивидуальный разброс. Диагональ.
            vx = (14.0f + (float) (Math.random() * 18.0f)) * depth;
            wavePhase = (float) (Math.random() * 2.0 * Math.PI);
            waveAmp = 4.0f + (float) (Math.random() * 8.0f);
            // Поворот лепестка вдоль направления полёта + лёгкое вращение.
            rot = (float) (Math.random() * 2.0 * Math.PI);
            rotSpeed = (float) ((Math.random() - 0.5) * 0.6);
            flickerPhase = (float) (Math.random() * 2.0 * Math.PI);
            flickerSpeed = 0.5f + (float) (Math.random() * 0.9f);
            color = (Math.random() < 0.33) ? PETAL_LIGHT : PETAL;
        }

        /** Движение со времени последнего кадра: секунды → пиксели. */
        void update(float seconds, float wind) {
            y += vy * seconds;
            x += vx * wind * seconds;
            // Синусоидальный снос поверх ветра: траектория не прямая, а «плавная».
            x += Mth.sin(wavePhase + elapsed * 1.6f) * waveAmp * seconds * 0.6f;
            rot += rotSpeed * seconds;

            // Respawn сверху с разбросом по X — разлетаются по всему экрану.
            if (y > screenHeight + LEN) {
                y = -LEN;
                // Часть респаунится слева, часть справа: ветер разнонаправленный.
                x = (Math.random() < 0.5) ? (float) (Math.random() * screenWidth * 0.5) : (float) (screenWidth * 0.5 + Math.random() * screenWidth * 0.5);
                if (x > screenWidth + LEN) x = -LEN;
            }
            // Вылетевшие за бок respawn слева на случайном Y — заполнение экрана.
            if (x > screenWidth + LEN) {
                x = -LEN;
                y = (float) (Math.random() * screenHeight);
            }
        }

        /**
         * Лепесток как три повёрнутых сегмента: центр широкий, края узкие.
         * <p>
         * Каждый сегмент — {@code fill} в координатах, повёрнутых через pose.
         * {@code submitColoredRectangle} копирует {@code new Matrix3x2f(pose)},
         * так что поворот/масштаб применяются к прямоугольнику.
         */
        void draw(GuiGraphics gg, float alphaMul) {
            float flicker = 0.35f + 0.22f * ((Mth.sin(flickerPhase + elapsed * flickerSpeed) + 1.0f) * 0.5f);
            float a = flicker * alphaMul * (depth < 0.7f ? 0.55f : depth > 1.2f ? 0.62f : 1.0f);
            int argb = ThunderRender.withAlpha(color, a);

            Matrix3x2fStack pose = gg.pose();
            pose.pushMatrix();
            pose.translate(x, y);
            pose.rotate(rot);
            pose.scale(depth, depth);

            // Сегменты вдоль оси Y (до поворота): центр 0, верх +LEN/2, низ −LEN/2.
            float half = LEN * 0.5f;
            // Центр — широкий.
            gg.fill((int) (-W * 0.5f), 0, (int) Math.ceil(W * 0.5f), (int) half, argb);
            gg.fill((int) (-W * 0.5f), (int) -half, (int) Math.ceil(W * 0.5f), 0, argb);
            // Верхний край — сужающийся (примерно 60% ширины).
            int wEdge = (int) Math.ceil(W * 0.3f);
            gg.fill(-wEdge, (int) half, wEdge, (int) Math.ceil(half * 1.4f), argb);
            // Нижний край — сужающийся.
            gg.fill(-wEdge, (int) -Math.ceil(half * 1.4f), wEdge, (int) -half, argb);

            pose.popMatrix();
        }
    }
}
