package com.valkyrie.client.gui.theme;

import com.valkyrie.client.gui.thunder.ThunderRender;

/**
 * Плавно догоняющее значение.
 * <p>
 * Избавляет экраны от россыпи полей вида {@code float hover} и ручных вызовов
 * интерполяции: достаточно указать цель и раз в кадр вызвать {@link #update}.
 */
public final class Animated {
    private final float speed;
    private float value;
    private float target;

    public Animated(float initial, float speed) {
        this.value = initial;
        this.target = initial;
        this.speed = speed;
    }

    public static Animated instant(float initial) {
        return new Animated(initial, Theme.SPEED_INSTANT);
    }

    public static Animated normal(float initial) {
        return new Animated(initial, Theme.SPEED_NORMAL);
    }

    public static Animated slow(float initial) {
        return new Animated(initial, Theme.SPEED_SLOW);
    }

    /** Задаёт цель и подтягивает значение за текущий кадр. */
    public float update(float target) {
        this.target = target;
        this.value = ThunderRender.fast(this.value, target, speed);
        return this.value;
    }

    /** Обновляет по булеву состоянию: {@code true} — к единице, {@code false} — к нулю. */
    public float update(boolean on) {
        return update(on ? 1.0f : 0.0f);
    }

    public float value() {
        return value;
    }

    public float target() {
        return target;
    }

    /** Мгновенно устанавливает значение без анимации. */
    public void snap(float value) {
        this.value = value;
        this.target = value;
    }

    public boolean isActive() {
        return Math.abs(target - value) > 0.001f;
    }
}
