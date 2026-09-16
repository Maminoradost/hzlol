package com.valkyrie.client.gui.notify;

import com.valkyrie.client.gui.theme.Easing;
import com.valkyrie.client.gui.theme.Theme;
import com.valkyrie.client.gui.thunder.ThunderRender;
import com.valkyrie.client.render.ValkyrieFonts;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Всплывающие уведомления в правом верхнем углу.
 * <p>
 * Очередь глобальная и статическая: тост может быть создан из любого места
 * (переключение модуля, сохранение конфига), а рисуется в одном месте — как
 * поверх мира, так и поверх экранов Valkyrie.
 */
public final class Notifications {
    /** Больше четырёх плашек одновременно превращаются в стену текста. */
    private static final int MAX_VISIBLE = 4;
    /** Сверх лимита копим в очереди, чтобы не терять события. */
    private static final int MAX_QUEUED = 12;

    private static final int WIDTH = 168;
    private static final int HEIGHT = 34;
    private static final int MARGIN = 10;
    private static final int GAP = 6;
    private static final int ELEVATION = 8;
    /** Ширина цветной полосы статуса слева. */
    private static final int STRIPE = 3;

    private static final List<Toast> ACTIVE = new ArrayList<>();
    private static final Deque<Toast> PENDING = new ArrayDeque<>();

    private Notifications() {
    }

    public static void info(String title, String message) {
        push(new Toast(Kind.INFO, title, message));
    }

    public static void success(String title, String message) {
        push(new Toast(Kind.SUCCESS, title, message));
    }

    public static void warning(String title, String message) {
        push(new Toast(Kind.WARNING, title, message));
    }

    /** Уведомление о включении/выключении модуля — самый частый случай. */
    public static void toggle(String moduleName, boolean enabled) {
        push(new Toast(
            enabled ? Kind.SUCCESS : Kind.INFO,
            moduleName,
            enabled ? "enabled" : "disabled"
        ));
    }

    private static void push(Toast toast) {
        // Повторное переключение того же модуля заменяет плашку, а не плодит стопку.
        for (int i = 0; i < ACTIVE.size(); i++) {
            if (ACTIVE.get(i).title.equals(toast.title) && !ACTIVE.get(i).dismissing) {
                Toast existing = ACTIVE.get(i);
                existing.replaceWith(toast);
                return;
            }
        }
        if (ACTIVE.size() >= MAX_VISIBLE) {
            if (PENDING.size() < MAX_QUEUED) {
                PENDING.addLast(toast);
            }
            return;
        }
        ACTIVE.add(toast);
    }

    public static void clear() {
        ACTIVE.clear();
        PENDING.clear();
    }

    /**
     * Рисует и продвигает очередь.
     * <p>
     * Вызывающий обязан заранее выполнить {@code ThunderRender.beginFrame()} —
     * тосты пользуются общей кадровой дельтой.
     */
    public static void render(GuiGraphics guiGraphics, Font font) {
        if (ACTIVE.isEmpty() && PENDING.isEmpty()) {
            return;
        }

        while (ACTIVE.size() < MAX_VISIBLE && !PENDING.isEmpty()) {
            ACTIVE.add(PENDING.pollFirst());
        }

        float frame = ThunderRender.frameSeconds();
        int right = guiGraphics.guiWidth() - MARGIN;
        float y = MARGIN;

        for (int i = ACTIVE.size() - 1; i >= 0; i--) {
            Toast toast = ACTIVE.get(i);
            if (toast.update(frame)) {
                ACTIVE.remove(i);
            }
        }

        for (Toast toast : ACTIVE) {
            toast.render(guiGraphics, font, right, Math.round(y));
            y += (HEIGHT + GAP) * toast.appear;
        }
    }

    private enum Kind {
        INFO,
        SUCCESS,
        WARNING;

        int color() {
            return switch (this) {
                case INFO -> Theme.accent();
                case SUCCESS -> Theme.SUCCESS();
                case WARNING -> Theme.DANGER();
            };
        }
    }

    private static final class Toast {
        /** Сколько плашка висит до автоскрытия. */
        private static final float LIFETIME = 3.0f;
        private static final float APPEAR_SPEED = 5.5f;
        private static final float DISMISS_SPEED = 7.0f;

        private Kind kind;
        private String title;
        private String message;

        /** 0 — плашка за краем экрана, 1 — на месте. */
        private float appear;
        private float age;
        private boolean dismissing;

        private Toast(Kind kind, String title, String message) {
            this.kind = kind;
            this.title = title;
            this.message = message;
        }

        /** Переиспользует плашку под новое событие, сохраняя текущую анимацию. */
        private void replaceWith(Toast other) {
            this.kind = other.kind;
            this.message = other.message;
            this.age = 0.0f;
            this.dismissing = false;
        }

        /** @return {@code true}, когда плашка полностью уехала и её пора удалить */
        private boolean update(float frameSeconds) {
            age += frameSeconds;
            if (age >= LIFETIME) {
                dismissing = true;
            }
            float target = dismissing ? 0.0f : 1.0f;
            float speed = dismissing ? DISMISS_SPEED : APPEAR_SPEED;
            appear = Mth.lerp(Mth.clamp(frameSeconds * speed, 0.0f, 1.0f), appear, target);
            return dismissing && appear < 0.01f;
        }

        private void render(GuiGraphics guiGraphics, Font font, int right, int y) {
            // Выезд из-за правого края: с лёгким перелётом на появлении.
            float slide = dismissing
                ? Easing.outCubic(appear)
                : Easing.outBack(appear);
            int x = Math.round(right - WIDTH * slide);
            float alpha = Mth.clamp(appear, 0.0f, 1.0f);
            if (alpha <= 0.01f) {
                return;
            }

            int accent = kind.color();
            ThunderRender.drawFloatingPanel(
                guiGraphics,
                x,
                y,
                WIDTH,
                HEIGHT,
                Theme.RADIUS_CARD,
                Theme.HUD_SURFACE(),
                ELEVATION,
                alpha
            );

            // Полоса статуса слева и полоска оставшегося времени снизу.
            ThunderRender.drawRoundRect(
                guiGraphics,
                x + 1,
                y + 6,
                STRIPE,
                HEIGHT - 12,
                STRIPE / 2,
                Theme.alpha(accent, 0.95f * alpha)
            );

            float remaining = 1.0f - Mth.clamp(age / LIFETIME, 0.0f, 1.0f);
            int barWidth = Math.round((WIDTH - 20) * remaining);
            if (barWidth > 0) {
                ThunderRender.drawAccentBar(
                    guiGraphics,
                    x + 10,
                    y + HEIGHT - 3,
                    barWidth,
                    2,
                    accent,
                    0.55f * alpha
                );
            }

            int textX = x + STRIPE + 9;
            ValkyrieFonts.drawTitle(
                guiGraphics,
                font,
                title,
                textX,
                y + 7,
                Theme.alpha(Theme.TEXT_PRIMARY(), alpha)
            );
            ValkyrieFonts.draw(
                guiGraphics,
                font,
                message,
                textX,
                y + 19,
                Theme.alpha(Theme.TEXT_SECONDARY(), 0.9f * alpha)
            );
        }
    }

    /** Тосты рисуются и без игрока — например, на главном меню. */
    public static boolean hasAny() {
        return !ACTIVE.isEmpty() || !PENDING.isEmpty();
    }
}
