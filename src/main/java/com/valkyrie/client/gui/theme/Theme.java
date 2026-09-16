package com.valkyrie.client.gui.theme;

import com.valkyrie.client.module.ModuleRegistry;
import com.valkyrie.client.module.impl.AppearanceModule;
import com.valkyrie.client.module.impl.DarkThemeModule;
import net.minecraft.util.Mth;

/**
 * Дизайн-токены светлой акриловой темы.
 * <p>
 * Все экраны берут цвета, радиусы, отступы и тайминги отсюда, чтобы правка в одном
 * месте меняла весь интерфейс. Цвета в формате ARGB.
 */
public final class Theme {
    private Theme() {
    }

    // ─── Активная палитра ────────────────────────────────────────────────────
    // Цвета — методы, а не константы: тема переключается в рантайме, а javac
    // подставил бы значения констант прямо в места вызова.

    /** Палитра, выбранная в конфиге. */
    public static Palette palette() {
        return isDark() ? Palette.DARK : Palette.LIGHT;
    }

    public static boolean isDark() {
        return ModuleRegistry.isEnabled(DarkThemeModule.class);
    }

    // ─── Поверхности ─────────────────────────────────────────────────────────
    // Акрил рассчитан на подложку из размытого кадра: панели намеренно
    // полупрозрачные, иначе размытие под ними не будет читаться.

    /** Основная панель поверх размытия. */
    public static int SURFACE() {
        return palette().surface;
    }

    /** Панель второго уровня — вложенные карточки, строки. */
    public static int SURFACE_RAISED() {
        return palette().surfaceRaised;
    }

    /** Спокойный фон строки в состоянии покоя. */
    public static int SURFACE_MUTED() {
        return palette().surfaceMuted;
    }

    /** Фон строки под курсором. */
    public static int SURFACE_HOVER() {
        return palette().surfaceHover;
    }

    /** Разделители и тонкие обводки. */
    public static int BORDER() {
        return palette().border;
    }

    /** Верхняя светлая грань, дающая эффект стекла. */
    public static int RIM_LIGHT() {
        return palette().rimLight;
    }

    // ─── Текст ───────────────────────────────────────────────────────────────

    public static int TEXT_PRIMARY() {
        return palette().textPrimary;
    }

    public static int TEXT_SECONDARY() {
        return palette().textSecondary;
    }

    public static int TEXT_TERTIARY() {
        return palette().textTertiary;
    }

    public static int TEXT_ON_ACCENT() {
        return palette().textOnAccent;
    }

    // ─── Тени ────────────────────────────────────────────────────────────────

    public static int SHADOW() {
        return palette().shadow;
    }

    /** Множитель плотности теней: на тёмном фоне подъём иначе не читается. */
    public static float shadowStrength() {
        return palette().shadowStrength;
    }

    // ─── Статусы ─────────────────────────────────────────────────────────────

    public static int DANGER() {
        return palette().danger;
    }

    public static int SUCCESS() {
        return palette().success;
    }

    // ─── Фон экранов и HUD ───────────────────────────────────────────────────

    public static int SCRIM_TOP() {
        return palette().scrimTop;
    }

    public static int SCRIM_BOTTOM() {
        return palette().scrimBottom;
    }

    /** Заливка панелей HUD — плотнее {@link #SURFACE()}, под ней нет размытия. */
    public static int HUD_SURFACE() {
        return palette().hudSurface;
    }

    public static int HUD_KEY_SURFACE() {
        return palette().hudKeySurface;
    }

    // ─── Элементы управления ─────────────────────────────────────────────────

    /** Ручка тумблера и слайдера. */
    public static int KNOB() {
        return palette().knob;
    }

    /** Трек тумблера в выключенном состоянии. */
    public static int TRACK_OFF() {
        return palette().trackOff;
    }

    // ─── Геометрия ───────────────────────────────────────────────────────────

    public static final int RADIUS_PANEL = 14;
    public static final int RADIUS_CARD = 11;
    public static final int RADIUS_ROW = 9;
    public static final int RADIUS_PILL = 999;

    public static final int PAD_PANEL = 12;
    public static final int PAD_ROW = 10;
    public static final int GAP_ROW = 4;
    public static final int GAP_PANEL = 10;

    // ─── Тайминги анимаций (скорость сходимости для ThunderRender.fast) ──────

    /** Мгновенная реакция: hover, нажатия. */
    public static final float SPEED_INSTANT = 22.0f;
    /** Обычные переходы: переключатели, подсветка. */
    public static final float SPEED_NORMAL = 14.0f;
    /** Неспешные переходы: появление панелей, плавные значения. */
    public static final float SPEED_SLOW = 9.0f;

    /** Длительность анимации открытия экрана в секундах. */
    public static final float OPEN_DURATION = 0.48f;
    /** Длительность анимации закрытия экрана в секундах. */
    public static final float CLOSE_DURATION = 0.32f;

    // ─── Акцент ──────────────────────────────────────────────────────────────

    /** Используется, если в конфиге лежит полностью прозрачный акцент. */
    public static final int ACCENT_FALLBACK = 0xFFE8A0B4;

    /** Палитра для быстрого перебора акцента кликом. */
    public static final int[] ACCENT_PALETTE = {
        0xFFE8A0B4, // sakura
        0xFFF2CEDE, // pale sakura
        0xFF6C8CFF, // indigo
        0xFF7A5CFF, // violet
        0xFF2FB98A, // emerald
        0xFF38BDF8, // sky
        0xFFF5A524, // amber
        0xFF101725, // graphite
    };

    /** Текущий акцент из конфига с защитой от прозрачного значения. */
    public static int accent() {
        int accent = ModuleRegistry.get(AppearanceModule.class).accent.value();
        return (accent >>> 24) == 0 ? ACCENT_FALLBACK : accent;
    }

    /** Общая прозрачность блоков HUD. */
    public static float hudOpacity() {
        return ModuleRegistry.get(AppearanceModule.class).hudOpacity.value();
    }

    /** Следующий цвет палитры — для переключения кликом. */
    public static int nextAccent(int current) {
        int rgb = current | 0xFF000000;
        for (int i = 0; i < ACCENT_PALETTE.length; i++) {
            if (ACCENT_PALETTE[i] == rgb) {
                return ACCENT_PALETTE[(i + 1) % ACCENT_PALETTE.length];
            }
        }
        return ACCENT_PALETTE[0];
    }

    // ─── Работа с цветом ─────────────────────────────────────────────────────

    /** Заменяет альфу, сохраняя RGB. */
    public static int alpha(int color, float alpha01) {
        int a = (int) (255.0f * Mth.clamp(alpha01, 0.0f, 1.0f));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    /** Умножает существующую альфу — нужно для общего затухания экрана. */
    public static int fade(int color, float factor) {
        int a = (int) (((color >>> 24) & 0xFF) * Mth.clamp(factor, 0.0f, 1.0f));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    /** Линейная интерполяция двух цветов по всем каналам. */
    public static int mix(int from, int to, float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        int a = (int) Mth.lerp(t, (from >>> 24) & 0xFF, (to >>> 24) & 0xFF);
        int r = (int) Mth.lerp(t, (from >>> 16) & 0xFF, (to >>> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (from >>> 8) & 0xFF, (to >>> 8) & 0xFF);
        int b = (int) Mth.lerp(t, from & 0xFF, to & 0xFF);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Осветляет цвет к белому. */
    public static int lighten(int color, float amount) {
        return mix(color, 0xFFFFFFFF | (color & 0xFF000000), amount);
    }

    /** Затемняет цвет к чёрному, сохраняя альфу. */
    public static int darken(int color, float amount) {
        return mix(color, 0xFF000000 | (color & 0xFF000000), amount);
    }
}
