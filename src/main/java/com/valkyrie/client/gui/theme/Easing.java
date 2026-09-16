package com.valkyrie.client.gui.theme;

import net.minecraft.util.Mth;

/** Функции сглаживания для анимаций интерфейса. Все принимают и возвращают 0..1. */
public final class Easing {
    private Easing() {
    }

    public static float linear(float t) {
        return Mth.clamp(t, 0.0f, 1.0f);
    }

    /** Мягкий старт и финиш — универсальный вариант. */
    public static float smoothStep(float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    /** Резкий старт, долгое затухание — хорош для появления панелей. */
    public static float outCubic(float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv;
    }

    /** Ещё более выраженное затухание — для смещений и «прилёта». */
    public static float outQuint(float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv * inv * inv;
    }

    public static float inOutCubic(float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        if (t < 0.5f) {
            return 4.0f * t * t * t;
        }
        float f = -2.0f * t + 2.0f;
        return 1.0f - f * f * f / 2.0f;
    }

    /** Лёгкий перелёт за цель — придаёт интерфейсу «живость». */
    public static float outBack(float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        float f = t - 1.0f;
        return 1.0f + c3 * f * f * f + c1 * f * f;
    }

    /** Затухающие колебания — для акцентных появлений. */
    public static float outElastic(float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        if (t == 0.0f || t == 1.0f) {
            return t;
        }
        float period = (float) (2.0 * Math.PI / 3.0);
        return (float) (Math.pow(2.0, -10.0 * t) * Math.sin((t * 10.0 - 0.75) * period) + 1.0);
    }
}
