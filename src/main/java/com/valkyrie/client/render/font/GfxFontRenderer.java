package com.valkyrie.client.render.font;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.slf4j.Logger;

import java.awt.*;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TTF рендерер: AWT glyph atlas → DynamicTexture → GuiGraphics.blit
 * Фикс: убрана scale-mul логика (она дублировала масштаб и ломала глифы),
 *       включён нормальный AA в GfxGlyphMap.
 */
public final class GfxFontRenderer implements Closeable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float ATLAS_SCALE = 2.0f;
    /**
     * Потолок динамических страниц атласа (сверх пребейк-набора).
     * Каждый незнакомый Unicode-блок (CJK-ник, арабская вязь и т.п.) выделяет
     * страницу в 256 глифов на GPU. Без предела длинная сессия на людном
     * сервере растит видеопамять неограниченно. Старейшая динамическая
     * страница вытесняется первой; пребейк (ASCII + кириллица) — постоянный.
     */
    private static final int MAX_DYNAMIC_PAGES = 12;
    private static int textureSerial;

    private final float sizePx;
    private final int   charsPerPage;
    private final int   padding;
    private final String prebakeGlyphs;

    private final Map<Character, GfxGlyph> allGlyphs = new HashMap<>();
    private int cachedLineHeight;
    private final List<GfxGlyphMap>        maps      = new ArrayList<>();
    /** Страницы, созданные пребейком, — их не вытесняем и не сбрасываем. */
    private final Set<GfxGlyphMap>         prebaked  = new HashSet<>();

    private Font    baseFont;
    private boolean initialized;

    // ─── конструкторы ────────────────────────────────────────────────────────

    public GfxFontRenderer(Font baseFont, float sizePx, int charsPerPage, int padding, String prebakeGlyphs) {
        this.baseFont      = baseFont;
        this.sizePx        = sizePx;
        this.charsPerPage  = charsPerPage;
        this.padding       = padding;
        this.prebakeGlyphs = prebakeGlyphs;
    }

    public GfxFontRenderer(Font baseFont, float sizePx) {
        this(baseFont, sizePx, 256, 4, GfxFontRenderers.DEFAULT_PREBAKE);
    }

    static int nextTextureSerial() { return textureSerial++; }

    // ─── init ────────────────────────────────────────────────────────────────

    public void ensureInitialized() {
        if (!initialized) init();
    }

    private void init() {
        // Масштабируем шрифт простым deriveFont — без умножения на guiScale.
        // guiScale уже учтён самим MC при рендере GuiGraphics.
        Font scaled = baseFont.deriveFont(Font.PLAIN, sizePx * ATLAS_SCALE);
        // создаём страницы с нормальным шрифтом
        this.baseFont   = scaled;
        this.initialized = true;
        if (prebakeGlyphs != null) {
            for (char c : prebakeGlyphs.toCharArray()) locateGlyph(c);
            prebaked.addAll(maps);
        }
    }

    // ─── глифы ───────────────────────────────────────────────────────────────

    private GfxGlyph locateGlyph0(char c) {
        for (GfxGlyphMap m : maps) {
            if (m.contains(c)) return m.getGlyph(c);
        }
        evictIfNeeded();
        int base = (c / charsPerPage) * charsPerPage;
        GfxGlyphMap page = new GfxGlyphMap(
            (char) base,
            (char)(base + charsPerPage),
            baseFont,
            GfxGlyphMap.nextTextureId(),
            padding
        );
        maps.add(page);
        return page.getGlyph(c);
    }

    /** Вытесняет старейшую динамическую страницу, когда достигнут потолок. */
    private void evictIfNeeded() {
        int dynamic = maps.size() - prebaked.size();
        if (dynamic < MAX_DYNAMIC_PAGES) return;
        for (int i = 0; i < maps.size(); i++) {
            GfxGlyphMap page = maps.get(i);
            if (prebaked.contains(page)) continue;
            maps.remove(i);
            page.destroy();
            // Глифы вытесненной страницы убираем из кэша, иначе drawString
            // будет блитить освобождённую текстуру.
            allGlyphs.values().removeIf(glyph -> glyph != null && glyph.owner() == page);
            return;
        }
    }

    /**
     * Сбрасывает только динамические страницы (не из пребейка), освобождая
     * GPU-атласы, накопленные за сессию. Вызывается при выходе из мира.
     */
    public void releaseDynamicPages() {
        if (maps.size() <= prebaked.size()) return;
        for (int i = maps.size() - 1; i >= 0; i--) {
            GfxGlyphMap page = maps.get(i);
            if (prebaked.contains(page)) continue;
            maps.remove(i);
            page.destroy();
            allGlyphs.values().removeIf(glyph -> glyph != null && glyph.owner() == page);
        }
    }

    private GfxGlyph locateGlyph(char c) {
        return allGlyphs.computeIfAbsent(c, this::locateGlyph0);
    }

    // ─── рендер ──────────────────────────────────────────────────────────────

    public void drawString(GuiGraphics g, String text, float x, float y, int color) {
        ensureInitialized();
        if (text == null || text.isEmpty()) return;

        float a = ((color >> 24) & 0xFF) / 255f;
        if (a <= 0.003f) return;
        float r = ((color >> 16) & 0xFF) / 255f;
        float gg = ((color >>  8) & 0xFF) / 255f;
        float b  = (color & 0xFF) / 255f;
        int tint = toTint(r, gg, b, a);

        // baseY небольшой сдвиг чтобы текст не был обрезан сверху
        float bx = x;
        float by = y - 1f;
        float ox = 0f, oy = 0f;

        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            if (c == '\n') {
                oy += getStringHeight(" ");
                ox = 0f;
                continue;
            }
            GfxGlyph glyph = locateGlyph(c);
            if (glyph == null) continue;
            if (glyph.value() == ' ') { ox += glyph.advance() / ATLAS_SCALE; continue; }

            int dx = Math.round(bx + ox);
            int dy = Math.round(by + oy);
            int gw = glyph.width();
            int gh = glyph.height();
            int drawW = Math.max(1, Math.round(gw / ATLAS_SCALE));
            int drawH = Math.max(1, Math.round(gh / ATLAS_SCALE));

            g.blit(
                RenderPipelines.GUI_TEXTURED,
                glyph.owner().textureId,
                dx, dy,
                (float) glyph.u(),
                (float) glyph.v(),
                drawW, drawH,
                gw, gh,
                glyph.owner().atlasWidth(),
                glyph.owner().atlasHeight(),
                tint
            );
            ox += glyph.advance() / ATLAS_SCALE;
        }
    }

    public void drawCenteredString(GuiGraphics g, String text, float cx, float y, int color) {
        drawString(g, text, cx - getStringWidth(text) / 2f, y, color);
    }

    // ─── размеры ─────────────────────────────────────────────────────────────

    public float getStringWidth(String text) {
        ensureInitialized();
        if (text == null || text.isEmpty()) return 0f;
        float line = 0f, max = 0f;
        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            if (c == '\n') { max = Math.max(max, line); line = 0f; continue; }
            GfxGlyph gl = locateGlyph(c);
            line += gl == null ? 0f : gl.advance() / ATLAS_SCALE;
        }
        return Math.max(max, line);
    }

    public float getStringHeight(String text) {
        ensureInitialized();
        if (text == null || text.isEmpty()) text = " ";
        float cur = 0f, total = 0f;
        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            if (c == '\n') { total += cur; cur = 0f; continue; }
            GfxGlyph gl = locateGlyph(c);
            if (gl != null) cur = Math.max(cur, gl.height() / ATLAS_SCALE);
        }
        return total + cur;
    }

    public int lineHeight() {
        if (cachedLineHeight == 0) cachedLineHeight = (int) Math.ceil(getStringHeight("Ay"));
        return cachedLineHeight;
    }

    // ─── close ───────────────────────────────────────────────────────────────

    @Override
    public void close() {
        for (GfxGlyphMap m : maps) m.destroy();
        maps.clear();
        prebaked.clear();
        allGlyphs.clear();
        cachedLineHeight = 0;
        initialized = false;
    }

    // ─── загрузка шрифта ─────────────────────────────────────────────────────

    public static GfxFontRenderer fromAsset(String assetPath, float sizePx, int style) {
        return new GfxFontRenderer(loadTrueType(assetPath, style), sizePx);
    }

    public static Font loadTrueType(String assetPath, int style) {
        String path = assetPath.startsWith("/") ? assetPath : "/assets/valkyrieclient/font/" + assetPath;
        try (InputStream in = GfxFontRenderer.class.getResourceAsStream(path)) {
            if (in != null) {
                Font f = Font.createFont(Font.TRUETYPE_FONT, in);
                return f.deriveFont(style);
            }
        } catch (FontFormatException | IOException e) {
            LOGGER.warn("[Valkyrie] Failed to load font {}: {}", assetPath, e.toString());
        }
        LOGGER.warn("[Valkyrie] Fallback SansSerif for {}", assetPath);
        return new Font(Font.SANS_SERIF, style, 12);
    }

    // ─── утилиты ─────────────────────────────────────────────────────────────

    private static int toTint(float r, float g, float b, float a) {
        return ((int)(Mth.clamp(a,0,1)*255) << 24)
             | ((int)(Mth.clamp(r,0,1)*255) << 16)
             | ((int)(Mth.clamp(g,0,1)*255) <<  8)
             |  (int)(Mth.clamp(b,0,1)*255);
    }
}
