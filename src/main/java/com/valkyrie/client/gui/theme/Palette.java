package com.valkyrie.client.gui.theme;

/**
 * Набор цветов одной темы.
 * <p>
 * Значения намеренно вынесены из {@link Theme} в отдельный неизменяемый объект:
 * тема переключается подменой ссылки, а не правкой десятков констант, и любая
 * новая тема — это один экземпляр этого класса.
 */
public final class Palette {
    // ─── Поверхности ─────────────────────────────────────────────────────────

    /** Основная панель поверх размытия. */
    public final int surface;
    /** Панель второго уровня — вложенные карточки, строки. */
    public final int surfaceRaised;
    /** Спокойный фон строки в состоянии покоя. */
    public final int surfaceMuted;
    /** Фон строки под курсором. */
    public final int surfaceHover;
    /** Разделители и тонкие обводки. */
    public final int border;
    /** Верхняя светлая грань, дающая эффект стекла. */
    public final int rimLight;

    // ─── Текст ───────────────────────────────────────────────────────────────

    public final int textPrimary;
    public final int textSecondary;
    public final int textTertiary;
    public final int textOnAccent;

    // ─── Прочее ──────────────────────────────────────────────────────────────

    public final int shadow;
    public final int danger;
    public final int success;

    /** Вуаль поверх размытого фона — повышает контраст текста на экранах. */
    public final int scrimTop;
    public final int scrimBottom;

    /** Плотная заливка панелей HUD: под ними нет размытия. */
    public final int hudSurface;
    /** Клавиша keystrokes в покое. */
    public final int hudKeySurface;

    /** Тёмная тема требует более плотных теней, чтобы подъём читался. */
    public final float shadowStrength;

    /** Ручка тумблера/слайдера: должна контрастировать с залитым акцентом треком. */
    public final int knob;
    /** Трек тумблера в выключенном состоянии. */
    public final int trackOff;

    /** Фон главного меню: дышащий градиент между двумя парами оттенков. */
    public final int menuTopA;
    public final int menuTopB;
    public final int menuBottomA;
    public final int menuBottomB;
    /** Цвет пылинок на фоне меню. */
    public final int menuParticle;
    /** Виньетка снизу меню. */
    public final int menuVignette;

    private Palette(Builder builder) {
        this.surface = builder.surface;
        this.surfaceRaised = builder.surfaceRaised;
        this.surfaceMuted = builder.surfaceMuted;
        this.surfaceHover = builder.surfaceHover;
        this.border = builder.border;
        this.rimLight = builder.rimLight;
        this.textPrimary = builder.textPrimary;
        this.textSecondary = builder.textSecondary;
        this.textTertiary = builder.textTertiary;
        this.textOnAccent = builder.textOnAccent;
        this.shadow = builder.shadow;
        this.danger = builder.danger;
        this.success = builder.success;
        this.scrimTop = builder.scrimTop;
        this.scrimBottom = builder.scrimBottom;
        this.hudSurface = builder.hudSurface;
        this.hudKeySurface = builder.hudKeySurface;
        this.shadowStrength = builder.shadowStrength;
        this.knob = builder.knob;
        this.trackOff = builder.trackOff;
        this.menuTopA = builder.menuTopA;
        this.menuTopB = builder.menuTopB;
        this.menuBottomA = builder.menuBottomA;
        this.menuBottomB = builder.menuBottomB;
        this.menuParticle = builder.menuParticle;
        this.menuVignette = builder.menuVignette;
    }

    /** Светлая акриловая тема — исходное оформление клиента. */
    public static final Palette LIGHT = new Builder()
        .surface(0xE8FFF9FC)
        .surfaceRaised(0xFFFFFCFD)
        .surfaceMuted(0x10E8B4C8)
        .surfaceHover(0x24F2CEDE)
        .border(0x28D9A8BA)
        .rimLight(0xA8FFFFFF)
        .textPrimary(0xFF2D2529)
        .textSecondary(0xFF75656C)
        .textTertiary(0xFFA4939B)
        .textOnAccent(0xFFFFFFFF)
        .shadow(0xFF5B4650)
        .danger(0xFFE0575F)
        .success(0xFF2FB98A)
        .scrim(0x36FFF9FC, 0x5CFFF4F8)
        .hudSurface(0xE8FFF9FC)
        .hudKeySurface(0xD9FFFBFD)
        .shadowStrength(0.76f)
        .knob(0xFFFFFFFF)
        .trackOff(0x30D9A8BA)
        .menu(0xFFFFFAFC, 0xFFFFF4F8, 0xFFF7EEF4, 0xFFFFF9FC)
        .menuParticle(0xFFD79BAD)
        .menuVignette(0x2AFFFFFF)
        .build();

    /**
     * Тёмная тема.
     * <p>
     * Это не инверсия светлой: на тёмном фоне белый блик стекла и тени работают
     * иначе, поэтому грань приглушена, а тени сделаны плотнее и холоднее.
     */
    public static final Palette DARK = new Builder()
        .surface(0xF01A1F2B)
        .surfaceRaised(0xFF222836)
        .surfaceMuted(0x1AFFFFFF)
        .surfaceHover(0x2E8FA6C8)
        .border(0x3AAEC0D8)
        .rimLight(0x40FFFFFF)
        .textPrimary(0xFFEDF1F7)
        .textSecondary(0xFFA6B2C6)
        .textTertiary(0xFF6E7A8E)
        .textOnAccent(0xFF0E1219)
        .shadow(0xFF05070C)
        .danger(0xFFFF6B73)
        .success(0xFF3FD3A0)
        .scrim(0x400B0E14, 0x660B0E14)
        .hudSurface(0xE61C2230)
        .hudKeySurface(0xD1232A3A)
        .shadowStrength(1.35f)
        .knob(0xFFF2F5FA)
        .trackOff(0x3D7C8CA6)
        .menu(0xFF11151E, 0xFF151A25, 0xFF0B0E15, 0xFF10141D)
        .menuParticle(0xFFAEBCD4)
        .menuVignette(0x2E000000)
        .build();

    private static final class Builder {
        private int surface;
        private int surfaceRaised;
        private int surfaceMuted;
        private int surfaceHover;
        private int border;
        private int rimLight;
        private int textPrimary;
        private int textSecondary;
        private int textTertiary;
        private int textOnAccent;
        private int shadow;
        private int danger;
        private int success;
        private int scrimTop;
        private int scrimBottom;
        private int hudSurface;
        private int hudKeySurface;
        private float shadowStrength = 1.0f;
        private int knob;
        private int trackOff;
        private int menuTopA;
        private int menuTopB;
        private int menuBottomA;
        private int menuBottomB;
        private int menuParticle;
        private int menuVignette;

        Builder surface(int v) { this.surface = v; return this; }
        Builder surfaceRaised(int v) { this.surfaceRaised = v; return this; }
        Builder surfaceMuted(int v) { this.surfaceMuted = v; return this; }
        Builder surfaceHover(int v) { this.surfaceHover = v; return this; }
        Builder border(int v) { this.border = v; return this; }
        Builder rimLight(int v) { this.rimLight = v; return this; }
        Builder textPrimary(int v) { this.textPrimary = v; return this; }
        Builder textSecondary(int v) { this.textSecondary = v; return this; }
        Builder textTertiary(int v) { this.textTertiary = v; return this; }
        Builder textOnAccent(int v) { this.textOnAccent = v; return this; }
        Builder shadow(int v) { this.shadow = v; return this; }
        Builder danger(int v) { this.danger = v; return this; }
        Builder success(int v) { this.success = v; return this; }
        Builder hudSurface(int v) { this.hudSurface = v; return this; }
        Builder hudKeySurface(int v) { this.hudKeySurface = v; return this; }
        Builder shadowStrength(float v) { this.shadowStrength = v; return this; }
        Builder knob(int v) { this.knob = v; return this; }
        Builder trackOff(int v) { this.trackOff = v; return this; }
        Builder menuParticle(int v) { this.menuParticle = v; return this; }
        Builder menuVignette(int v) { this.menuVignette = v; return this; }

        Builder menu(int topA, int topB, int bottomA, int bottomB) {
            this.menuTopA = topA;
            this.menuTopB = topB;
            this.menuBottomA = bottomA;
            this.menuBottomB = bottomB;
            return this;
        }

        Builder scrim(int top, int bottom) {
            this.scrimTop = top;
            this.scrimBottom = bottom;
            return this;
        }

        Palette build() {
            return new Palette(this);
        }
    }
}
