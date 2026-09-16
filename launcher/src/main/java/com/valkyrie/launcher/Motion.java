package com.valkyrie.launcher;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.util.Duration;

/**
 * Централизованные кривые сглаживания и хелперы анимации.
 *
 * <p>Штатные {@code Interpolator.EASE_*} в JavaFX реализуют SMIL 3.0 с коэффициентом
 * ускорения 0.2 и заметно слабее одноимённых кривых CSS. Константы ниже — прямые
 * эквиваленты CSS cubic-bezier через {@link Interpolator#SPLINE}.
 */
final class Motion {

    private Motion() {}

    /** Стандартная кривая затухания. Замена вялому EASE_OUT. */
    static final Interpolator OUT_CUBIC = Interpolator.SPLINE(0.215, 0.610, 0.355, 1.000);

    /** Короткое затухание для быстрых реакций (отпускание кнопки, уход курсора). */
    static final Interpolator OUT_QUAD = Interpolator.SPLINE(0.250, 0.460, 0.450, 0.940);

    /** Разгон. Для исчезающих элементов. */
    static final Interpolator IN_CUBIC = Interpolator.SPLINE(0.550, 0.055, 0.675, 0.190);

    /** Material standard. Основная кривая для панелей и выдвижных блоков. */
    static final Interpolator STANDARD = Interpolator.SPLINE(0.4, 0.0, 0.2, 1.0);

    /** Чистое торможение без разгона. Для появления всплывающих элементов. */
    static final Interpolator DECELERATE = Interpolator.SPLINE(0.0, 0.0, 0.2, 1.0);

    /**
     * Мягкий перелёт (~7%). Только для scaleX/Y, translateX/Y и rotate.
     * К opacity неприменим: значения выше 1.0 обрезаются и дают видимую паузу.
     *
     * <p>Реализован отдельным классом, а не через {@link Interpolator#SPLINE}:
     * JavaFX требует, чтобы контрольные точки лежали в диапазоне [0,1], поэтому
     * перелёт кривой Безье, как в CSS cubic-bezier, здесь недостижим.
     */
    static final Interpolator OUT_BACK_SOFT = new Interpolator() {
        private static final double TENSION = 1.2;

        @Override
        protected double curve(double t) {
            if (t <= 0.0) return 0.0;
            if (t >= 1.0) return 1.0;
            double p = t - 1.0;
            return p * p * ((TENSION + 1) * p + TENSION) + 1.0;
        }
    };

    private static volatile boolean reduced;

    static void setReduced(boolean value) {
        reduced = value;
    }

    static boolean reduced() {
        return reduced;
    }

    /** Длительность, обнуляемая в режиме пониженной анимации. */
    static Duration d(double millis) {
        return reduced ? Duration.ZERO : Duration.millis(millis);
    }

    /** Длительность с укороченным, но ненулевым вариантом для режима пониженной анимации. */
    static Duration d(double millis, double reducedMillis) {
        return Duration.millis(reduced ? reducedMillis : millis);
    }

    /**
     * Воспроизводит анимацию либо, в режиме пониженной анимации, мгновенно переводит
     * её в конечное состояние. Обработчик onFinished срабатывает в обоих случаях.
     */
    static void play(Animation animation) {
        if (reduced) {
            animation.jumpTo(animation.getTotalDuration());
        }
        animation.play();
    }

    /**
     * Включает растровое кеширование на время трансформации.
     * SVGPath растрируется на CPU, поэтому без кеша каждый кадр масштабирования
     * перерисовывает геометрию заново.
     */
    static void cacheDuring(Node node, Animation animation) {
        if (reduced) return;
        node.setCache(true);
        node.setCacheHint(CacheHint.SCALE);
        animation.setOnFinished(event -> {
            node.setCacheHint(CacheHint.QUALITY);
            node.setCache(false);
        });
    }

    /** Останавливает анимацию и сбрасывает масштаб: stop() не восстанавливает значения. */
    static void stopAndReset(Animation animation, Node node) {
        if (animation != null) {
            animation.stop();
        }
        if (node != null) {
            node.setScaleX(1.0);
            node.setScaleY(1.0);
        }
    }
}
