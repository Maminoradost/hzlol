package com.valkyrie.launcher;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.Random;

final class SakuraBackground extends Canvas {
    private static final double LEN = 7.0;
    private static final double WIDTH = 3.2;
    private static final int AREA_PER = 5000;
    private static final double DENSITY = 0.55;
    private static final double ALPHA_MUL = 0.55;
    /* Тёмная тема: лепестки светлее и чуть плотнее — на #1c1621 прежние 0.55 почти не читаются. */
    private static final double ALPHA_MUL_DARK = 0.62;
    private static final Color LIGHT_PALE = Color.web("#F2CEDE");
    private static final Color LIGHT_DEEP = Color.web("#E8B4C8");
    private static final Color DARK_PALE = Color.web("#F7DDE8");
    private static final Color DARK_DEEP = Color.web("#E9A9C2");

    /* Canvas рисуется вручную, CSS-темизация до него не доходит: цвет лепестка
       выбирается здесь и должен переключаться вместе с .theme-dark. */
    private Color palePetal = LIGHT_PALE;
    private Color deepPetal = LIGHT_DEEP;
    private double alphaMultiplier = ALPHA_MUL;

    private final Random random = new Random(0x56414c4bL);
    private Petal[] petals = new Petal[0];
    private int lastWidth = -1;
    private int lastHeight = -1;
    private boolean reducedMotion;
    private double elapsed;
    private long lastFrame;
    /** Разовый порыв ветра: сила 0..1, затухает экспоненциально. */
    private double gustBoost;
    private final AnimationTimer timer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            if (lastFrame == 0) {
                lastFrame = now;
                return;
            }
            double dt = Math.min(0.05, (now - lastFrame) / 1_000_000_000.0);
            lastFrame = now;
            render(dt);
        }
    };

    SakuraBackground() {
        setMouseTransparent(true);
        widthProperty().addListener((ignored, old, value) -> rebuildIfNeeded());
        heightProperty().addListener((ignored, old, value) -> rebuildIfNeeded());
        timer.start();
    }

    void setReducedMotion(boolean reducedMotion) {
        this.reducedMotion = reducedMotion;
    }

    /**
     * Переключает палитру лепестков вместе с темой окна. Лепестки не
     * пересоздаются — у каждого хранится только «бледный/насыщенный» разряд,
     * а конкретный цвет берётся при отрисовке, поэтому смена темы не сбрасывает
     * положение частиц.
     */
    void setDark(boolean dark) {
        palePetal = dark ? DARK_PALE : LIGHT_PALE;
        deepPetal = dark ? DARK_DEEP : LIGHT_DEEP;
        alphaMultiplier = dark ? ALPHA_MUL_DARK : ALPHA_MUL;
    }

    void setPaused(boolean paused) {
        if (paused) timer.stop();
        else {
            lastFrame = 0;
            timer.start();
        }
    }

    void stop() {
        timer.stop();
    }

    /**
     * Разовый порыв ветра: лепестки подхватывает и уносит вбок, эффект
     * затухает сам за ~1.5 секунды. Вызывается на Play и наведение —
     * фон «отвечает» на действие пользователя.
     */
    void gust(double strength) {
        if (reducedMotion) return;
        gustBoost = Math.min(1.6, gustBoost + Math.max(0, strength));
    }

    private void rebuildIfNeeded() {
        int width = (int) Math.round(getWidth());
        int height = (int) Math.round(getHeight());
        if (width <= 0 || height <= 0 || width == lastWidth && height == lastHeight) return;
        lastWidth = width;
        lastHeight = height;
        int count = Math.max(10, Math.min(140, Math.round((float) (width * height / (double) AREA_PER * DENSITY))));
        petals = new Petal[count];
        for (int i = 0; i < count; i++) petals[i] = new Petal(width, height);
    }

    private void render(double dt) {
        rebuildIfNeeded();
        elapsed += dt;
        double motion = reducedMotion ? 0.08 : 1.0;
        // Экспоненциальное затухание разового порыва (~1.5 c до нуля).
        gustBoost *= Math.pow(0.08, dt);
        if (gustBoost < 0.01) gustBoost = 0;
        double gust = 0.72 + 0.28 * ((Math.sin(elapsed * 0.42) + 1.0) * 0.5) + gustBoost * 2.4;
        GraphicsContext g = getGraphicsContext2D();
        g.clearRect(0, 0, getWidth(), getHeight());
        for (Petal petal : petals) {
            petal.update(dt * motion, gust);
            petal.draw(g);
        }
    }

    private final class Petal {
        double x;
        double y;
        final double vy;
        final double vx;
        final double wavePhase;
        final double waveAmp;
        double rotation;
        final double rotationSpeed;
        final double flickerPhase;
        final double flickerSpeed;
        final double depth;
        final boolean pale;
        final int screenWidth;
        final int screenHeight;

        Petal(int width, int height) {
            screenWidth = width;
            screenHeight = height;
            double layer = random.nextDouble();
            depth = layer < 0.58 ? 0.55 : layer < 0.90 ? 1.0 : 1.45;
            x = random.nextDouble() * width;
            y = random.nextDouble() * height;
            vy = (7.0 + random.nextDouble() * 10.0) * depth;
            vx = (14.0 + random.nextDouble() * 18.0) * depth;
            wavePhase = random.nextDouble() * Math.PI * 2.0;
            waveAmp = 4.0 + random.nextDouble() * 8.0;
            rotation = random.nextDouble() * Math.PI * 2.0;
            rotationSpeed = (random.nextDouble() - 0.5) * 0.6;
            flickerPhase = random.nextDouble() * Math.PI * 2.0;
            flickerSpeed = 0.5 + random.nextDouble() * 0.9;
            pale = random.nextDouble() < 0.33;
        }

        void update(double seconds, double wind) {
            y += vy * seconds;
            x += vx * wind * seconds;
            x += Math.sin(wavePhase + elapsed * 1.6) * waveAmp * seconds * 0.6;
            rotation += rotationSpeed * seconds;
            if (y > screenHeight + LEN) {
                y = -LEN;
                x = random.nextDouble() < 0.5
                    ? random.nextDouble() * screenWidth * 0.5
                    : screenWidth * 0.5 + random.nextDouble() * screenWidth * 0.5;
                if (x > screenWidth + LEN) x = -LEN;
            }
            if (x > screenWidth + LEN) {
                x = -LEN;
                y = random.nextDouble() * screenHeight;
            }
        }

        void draw(GraphicsContext g) {
            double flicker = 0.35 + 0.22 * ((Math.sin(flickerPhase + elapsed * flickerSpeed) + 1.0) * 0.5);
            double layerAlpha = depth < 0.7 ? 0.55 : depth > 1.2 ? 0.62 : 1.0;
            Color color = pale ? palePetal : deepPetal;
            g.save();
            g.translate(x, y);
            g.rotate(Math.toDegrees(rotation));
            g.scale(depth, depth);
            g.setFill(new Color(color.getRed(), color.getGreen(), color.getBlue(), flicker * alphaMultiplier * layerAlpha));
            double half = LEN * 0.5;
            g.fillRect(-WIDTH * 0.5, 0, WIDTH, half);
            g.fillRect(-WIDTH * 0.5, -half, WIDTH, half);
            double edge = Math.ceil(WIDTH * 0.3);
            g.fillRect(-edge, half, edge * 2, Math.ceil(half * 1.4) - half);
            g.fillRect(-edge, -Math.ceil(half * 1.4), edge * 2, Math.ceil(half * 1.4) - half);
            g.restore();
        }
    }
}
