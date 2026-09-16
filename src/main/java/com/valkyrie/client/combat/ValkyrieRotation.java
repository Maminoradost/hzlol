package com.valkyrie.client.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Управление углом обзора, который уходит на сервер.
 * <p>
 * Поворот выставляется игроку в {@code ClientTickEvent.Pre} — до
 * {@code level.tickEntities()}, внутри которого {@code LocalPlayer.sendPosition()}
 * отправляет {@code ServerboundMovePlayerPacket}. Отдельный пакет поворота не
     * шлётся: лишний пакет сам по себе — признак автоматики, а встроенный в общий
 * поток угол неотличим от движения мышью.
 * <p>
 * В 1.21.10 сервер получает и сырые нажатия клавиш
 * ({@code ServerboundPlayerInputPacket}), и позицию. Симуляционные античиты
 * (GrimAC) проверяют, что перемещение выводится из «клавиши + угол», поэтому
 * вектор ввода здесь намеренно НЕ подменяется: тело поворачивается вместе с
 * прицелом, и симуляция сходится. Классический MoveFix из эпохи 1.8 тут дал бы
 * ровно обратный эффект — рассинхрон позиции с симуляцией.
 */
public final class ValkyrieRotation {
    /** Угол, который игрок держал до вмешательства, — возвращается после тика. */
    private static float userYaw;
    private static float userPitch;
    private static float userYawO;
    private static float userPitchO;

    /** Последний угол, реально ушедший на сервер. */
    private static float sentYaw;
    private static float sentPitch;
    private static boolean hasSent;

    /**
     * Угол из предыдущего пакета — именно он у сервера на момент удара.
     * <p>
     * Атака отправляется раньше пакета движения (как и при обычном клике), так
     * что сервер проверяет попадание по прошлому направлению взгляда. Бить,
     * ориентируясь на угол, который сервер ещё не получил, — это промах и флаг
     * reach-проверки одновременно.
     */
    private static float servedYaw;
    private static float servedPitch;
    private static boolean hasServed;

    /** Заявка на текущий тик. */
    private static boolean requested;
    private static float wantYaw;
    private static float wantPitch;
    private static float maxStep;
    private static boolean silent;

    /** Был ли угол подменён в этом тике — определяет необходимость отката. */
    private static boolean applied;

    private ValkyrieRotation() {
    }

    /**
     * Запросить доворот в текущем тике.
     *
     * @param step   максимальное изменение угла за тик, градусы
     * @param quiet  вернуть камеру игроку после тика (серверу уйдёт наш угол)
     */
    public static void request(float yaw, float pitch, float step, boolean quiet) {
        requested = true;
        wantYaw = yaw;
        wantPitch = pitch;
        maxStep = Math.max(0.1f, step);
        silent = quiet;
    }

    /** Угол текущего тика — уйдёт на сервер пакетом движения ниже по тику. */
    public static float sentYaw() {
        return sentYaw;
    }

    public static float sentPitch() {
        return sentPitch;
    }

    public static boolean hasSent() {
        return hasSent;
    }

    /** Угол, уже известный серверу: с ним будет проверена атака этого тика. */
    public static float servedYaw() {
        return servedYaw;
    }

    public static float servedPitch() {
        return servedPitch;
    }

    public static boolean hasServed() {
        return hasServed;
    }

    public static void reset() {
        requested = false;
        applied = false;
        hasSent = false;
        hasServed = false;
    }

    /**
     * Шаг мыши игрока в градусах.
     * <p>
     * Повторяет {@code MouseHandler.turnPlayer}: {@code (s*0.6+0.2)^3 * 8} умножается
     * на {@code 0.15} в {@code Entity.turn}. Любое смещение взгляда человека кратно
     * этому шагу, поэтому дельты квантуются — Vulcan, Grizzly и MX ловят именно
     * «слишком точные» приращения, невозможные для целого числа отсчётов мыши.
     */
    public static float mouseStep() {
        double sensitivity = Minecraft.getInstance().options.sensitivity().get();
        double factor = sensitivity * 0.6 + 0.2;
        return (float) (factor * factor * factor * 8.0 * 0.15);
    }

    /** Вызывается внутри тика игрока: подменяет угол, если модуль его запросил. */
    public static void apply(LocalPlayer player) {
        // То, что сервер получил в прошлом тике, становится «известным» углом:
        // атака этого тика уйдёт раньше нового пакета движения.
        if (hasSent) {
            servedYaw = sentYaw;
            servedPitch = sentPitch;
            hasServed = true;
        }

        if (!requested) {
            // Без вмешательства сервер увидит собственный угол игрока — его и
            // запоминаем, иначе проверка «куда смотрел прошлый пакет» соврёт.
            sentYaw = player.getYRot();
            sentPitch = player.getXRot();
            hasSent = true;
            applied = false;
            return;
        }

        userYaw = player.getYRot();
        userPitch = player.getXRot();
        userYawO = player.yRotO;
        userPitchO = player.xRotO;

        // Доворот считается от последнего отправленного угла, а не от текущего
        // угла игрока: иначе каждый тик начинался бы со скачка на позицию мыши.
        float fromYaw = hasSent ? sentYaw : userYaw;
        float fromPitch = hasSent ? sentPitch : userPitch;

        float step = mouseStep();
        float nextYaw = fromYaw + quantize(Mth.clamp(Mth.wrapDegrees(wantYaw - fromYaw), -maxStep, maxStep), step);
        float nextPitch = Mth.clamp(fromPitch + quantize(Mth.clamp(wantPitch - fromPitch, -maxStep, maxStep), step), -90.0f, 90.0f);

        player.setYRot(nextYaw);
        player.setXRot(nextPitch);

        sentYaw = nextYaw;
        sentPitch = nextPitch;
        hasSent = true;
        applied = true;
    }

    /** Вызывается после тика: в тихом режиме возвращает игроку его собственный взгляд. */
    public static void restore(LocalPlayer player) {
        if (applied && silent) {
            player.setYRot(userYaw);
            player.setXRot(userPitch);
            // Предыдущий угол тоже откатывается: камера интерполирует кадр между
            // тиками именно по нему, и без отката взгляд дёргался бы на цель.
            player.yRotO = userYawO;
            player.xRotO = userPitchO;
        }
        requested = false;
        applied = false;
    }

    /**
     * Приведение дельты к целому числу шагов мыши.
     * <p>
     * Округление до нуля не «дотягивает» прицел на доли шага — так и должно быть:
     * человек физически не может сместить взгляд точнее одного отсчёта сенсора.
     */
    private static float quantize(float delta, float step) {
        if (step <= 0.0f) {
            return delta;
        }
        return Math.round(delta / step) * step;
    }
}
