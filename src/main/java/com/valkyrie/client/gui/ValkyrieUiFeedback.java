package com.valkyrie.client.gui;

import com.valkyrie.client.ValkyrieSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

/**
 * Звуковое сопровождение интерфейса.
 * <p>
 * Пока в проекте один сэмпл {@code cream_click.ogg}, поэтому разные события
 * различаются питчем и громкостью. Когда появятся отдельные файлы, достаточно
 * зарегистрировать их в {@link ValkyrieSounds} и подменить событие в нужном
 * методе — вызывающий код менять не придётся.
 */
public final class ValkyrieUiFeedback {
    /** Ограничение частоты hover-звука: без него скольжение мышью трещит. */
    private static final long HOVER_COOLDOWN_MS = 55L;

    private static long lastHoverAtMs;

    private ValkyrieUiFeedback() {
    }

    /** Обычное нажатие. */
    public static void click() {
        play(1.06f, 1.0f);
    }

    /** Наведение на интерактивный элемент: тише и выше основного клика. */
    public static void hover() {
        long now = System.currentTimeMillis();
        if (now - lastHoverAtMs < HOVER_COOLDOWN_MS) {
            return;
        }
        lastHoverAtMs = now;
        play(1.42f, 0.28f);
    }

    /** Включение переключателя — восходящая интонация. */
    public static void toggleOn() {
        play(1.24f, 0.85f);
    }

    /** Выключение переключателя — нисходящая интонация. */
    public static void toggleOff() {
        play(0.92f, 0.85f);
    }

    /** Открытие экрана. */
    public static void open() {
        play(0.86f, 0.95f);
    }

    /** Закрытие экрана. */
    public static void close() {
        play(0.74f, 0.85f);
    }

    /** Шаг слайдера — очень тихий тик. */
    public static void tick() {
        play(1.62f, 0.16f);
    }

    private static void play(float pitch, float volume) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSoundManager() == null) {
            return;
        }
        ValkyrieSounds.CREAM_CLICK.getHolder().ifPresent(holder -> {
            SoundEvent event = holder.value();
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
        });
    }
}
