package com.valkyrie.launcher;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Scale;

final class LauncherIcons {
    private LauncherIcons() {}

    /** Коэффициент приведения сетки 24x24 (Lucide) к принятой здесь сетке 16x16. */
    private static final double LUCIDE_SCALE = 16.0 / 24.0;

    static Node play() {
        SVGPath path = path("M 3 1 L 12 7 L 3 13 Z", true);
        return icon(path);
    }

    /* --- Иконки состояний кнопки действия ---
     * Геометрия: Lucide (ISC), кроме stop() — Bootstrap Icons (MIT).
     * Тексты лицензий в SYSTEM-UICONS-UNLICENSE.txt и LUCIDE-ICONS-LICENSE.txt. */

    /** Lucide "download": стрелка вниз в лоток. */
    static Node download() {
        return scaled(path("M12 15V3 M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4 M7 10l5 5 5-5", false));
    }

    /** Lucide "rotate-cw": круговая стрелка обновления. */
    static Node update() {
        return scaled(path("M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8 M21 3v5h-5", false));
    }

    /**
     * Lucide "loader-circle": дуга 270 градусов для непрерывного вращения.
     *
     * <p>Дуга охватывает три квадранта, поэтому её габаритный прямоугольник
     * симметричен относительно центра окружности: вращение вокруг центра границ
     * узла совпадает с вращением вокруг (12,12) и происходит без биения.
     * Проверено замером: смещение центра равно нулю.
     */
    static Node spinner() {
        return scaled(path("M21 12a9 9 0 1 1-6.219-8.56", false));
    }

    /** Lucide "check": галочка. */
    static Node check() {
        return scaled(path("M20 6L9 17L4 12", false));
    }

    /**
     * Lucide "circle-alert": круг с восклицательным знаком.
     * Точка знака задана коротким отрезком: JavaFX не отрисовывает подпути нулевой
     * длины даже при скруглённых окончаниях, в отличие от браузеров.
     */
    static Node alert() {
        return scaled(path("M12 2A10 10 0 1 1 12 22A10 10 0 1 1 12 2Z M12 8L12 12 M12 15.99L12 16.01", false));
    }

    /**
     * Залитый квадрат со скруглением — пара к треугольнику play.
     * Сетка 24x24, как у остальных иконок состояний: вариант из Bootstrap Icons
     * рисуется в сетке 16x16 и на общем фоне выглядел бы вдвое мельче.
     */
    static Node stop() {
        return scaled(path("M7 5h10a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2 Z", true));
    }

    static Node updates() {
        SVGPath first = path("M 4.5 1.5 C 2.088 2.878 0.5 5.524 0.5 8.5 C 0.5 12.918 4.082 16.5 8.5 16.5", false);
        SVGPath second = path("M 12.5 15.5 C 14.787 14.092 16.5 11.383 16.5 8.5 C 16.5 4.082 12.918 0.5 8.5 0.5", false);
        SVGPath arrows = path("M 4.5 5.5 V 1.5 H 0.5 M 12.5 11.5 V 15.5 H 16.5", false);
        return icon(first, second, arrows);
    }

    static Node settings() {
        SVGPath gear = path("M 8.5 0.6 L 9.1 2.1 C 9.55 2.23 9.97 2.41 10.36 2.64 L 11.82 1.94 C 12.34 2.34 12.8 2.8 13.19 3.33 L 12.46 4.78 C 12.68 5.18 12.85 5.6 12.97 6.05 L 14.5 6.58 C 14.54 6.88 14.56 7.19 14.56 7.5 L 14.48 8.53 L 12.94 9.04 C 12.81 9.49 12.63 9.91 12.4 10.3 L 13.1 11.76 C 12.71 12.28 12.24 12.74 11.71 13.13 L 10.27 12.4 C 9.87 12.62 9.45 12.79 9 12.91 L 8.47 14.44 C 8.17 14.48 7.86 14.5 7.55 14.5 L 6.52 14.42 L 6.01 12.88 C 5.56 12.75 5.14 12.57 4.75 12.34 L 3.29 13.04 C 2.77 12.65 2.31 12.18 1.92 11.65 L 2.65 10.21 C 2.43 9.81 2.26 9.39 2.14 8.94 L 0.61 8.41 C 0.57 8.11 0.55 7.81 0.55 7.5 L 0.63 6.47 L 2.17 5.96 C 2.3 5.51 2.48 5.09 2.71 4.7 L 2.01 3.24 C 2.4 2.72 2.87 2.26 3.4 1.87 L 4.84 2.6 C 5.24 2.38 5.66 2.21 6.11 2.09 L 6.64 0.56 C 7.25 0.48 7.88 0.49 8.5 0.6 Z", false);
        Circle center = new Circle(7.55, 7.5, 2.8);
        center.getStyleClass().add("icon-circle");
        return icon(gear, center);
    }

    static Node folder() {
        return icon(path("M 0.5 1.5 V 10.5 C 0.5 11.6 1.4 12.5 2.5 12.5 H 12.5 C 13.6 12.5 14.5 11.6 14.5 10.5 V 4.5 C 14.5 3.4 13.6 2.5 12.5 2.5 H 7.5 L 5.5 0.5 H 1.5 C 0.95 0.5 0.5 0.95 0.5 1.5 Z M 0.5 2.5 H 7.5", false));
    }

    static Node volume() {
        return icon(path("M 1.5 5.5 H 4.5 L 9.5 0.5 V 16.5 L 4.5 11.5 H 1.5 C 0.95 11.5 0.5 11.05 0.5 10.5 V 6.5 C 0.5 5.95 0.95 5.5 1.5 5.5 Z M 11.5 13.5 C 12.83 12.5 13.5 10.83 13.5 8.5 C 13.5 6.17 12.83 4.5 11.5 3.5 M 11.5 6.5 V 10.5", false));
    }

    static Node motion() {
        return icon(path("M 18.5 9.5 H 0.5 M 9.5 0.5 V 18.5 M 15.5 12.5 L 18.5 9.5 L 15.5 6.5 M 3.5 12.5 L 0.5 9.5 L 3.5 6.5 M 6.5 3.5 L 9.5 0.5 L 12.5 3.5 M 6.5 15.5 L 9.5 18.5 L 12.5 15.5", false));
    }

    /** Lucide "moon": переключатель тёмной темы Sakura Night. */
    static Node theme() {
        return scaled(path("M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z", false));
    }

    static Node minimize() {
        return icon(path("M 2 7.5 H 12", false));
    }

    static Node close() {
        return icon(path("M 2 2 L 12 12 M 12 2 L 2 12", false));
    }

    private static SVGPath path(String content, boolean fill) {
        SVGPath path = new SVGPath();
        path.setContent(content);
        path.getStyleClass().add(fill ? "icon-fill" : "icon-stroke");
        return path;
    }

    private static Group icon(Node... nodes) {
        Group group = new Group(nodes);
        group.getStyleClass().add("button-icon");
        group.setMouseTransparent(true);
        return group;
    }

    /** Оборачивает путь в сетке 24x24, приводя его к принятому масштабу 16x16. */
    private static Group scaled(Node... nodes) {
        Group inner = new Group(nodes);
        inner.getTransforms().add(new Scale(LUCIDE_SCALE, LUCIDE_SCALE));
        Group group = new Group(inner);
        group.getStyleClass().add("button-icon");
        group.setMouseTransparent(true);
        return group;
    }


}
