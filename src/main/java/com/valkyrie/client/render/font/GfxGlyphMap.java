package com.valkyrie.client.render.font;

import com.mojang.blaze3d.platform.NativeImage;
import com.valkyrie.ValkyrieClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class GfxGlyphMap {
    final char fromIncl;
    final char toExcl;
    final Font font;
    final ResourceLocation textureId;
    final int pixelPadding;

    private final Map<Character, GfxGlyph> glyphs = new HashMap<>();
    private int atlasWidth;
    private int atlasHeight;
    private boolean generated;

    GfxGlyphMap(char from, char to, Font font, ResourceLocation textureId, int padding) {
        this.fromIncl = from;
        this.toExcl   = to;
        this.font     = font;
        this.textureId    = textureId;
        this.pixelPadding = padding;
    }

    int atlasWidth()  { return atlasWidth;  }
    int atlasHeight() { return atlasHeight; }

    boolean contains(char c) { return c >= fromIncl && c < toExcl; }

    GfxGlyph getGlyph(char c) {
        if (!generated) generate();
        return glyphs.get(c);
    }

    void destroy() {
        Minecraft.getInstance().getTextureManager().release(textureId);
        glyphs.clear();
        generated = false;
    }

    private void generate() {
        if (generated) return;

        // ── 1. измеряем все глифы ────────────────────────────────────────────
        AffineTransform   at  = new AffineTransform();
        FontRenderContext frc = new FontRenderContext(at, true, true); // AA=on, FM=on

        int range = toExcl - fromIncl;
        int cols  = (int) Math.ceil(Math.sqrt(range));

        List<int[]> metrics = new ArrayList<>(range);
        int colW = 0, rowH = 0;

        for (int i = 0; i < range; i++) {
            char c = (char)(fromIncl + i);
            Rectangle2D b = font.getStringBounds(String.valueOf(c), frc);
            int advance = Math.max(1, (int) Math.ceil(b.getWidth()));
            int gw = Math.max(1, advance + pixelPadding * 2);
            int gh = Math.max(1, (int) Math.ceil(b.getHeight()) + pixelPadding);
            metrics.add(new int[]{gw, gh, advance});
            colW = Math.max(colW, gw);
            rowH = Math.max(rowH, gh);
        }

        int rows = (int) Math.ceil((double) range / cols);
        atlasWidth  = colW * cols;
        atlasHeight = rowH * rows;
        if (atlasWidth  < 1) atlasWidth  = 1;
        if (atlasHeight < 1) atlasHeight = 1;

        // ── 2. рисуем atlas ──────────────────────────────────────────────────
        BufferedImage img = new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D    g2  = img.createGraphics();

        g2.setColor(new Color(0, 0, 0, 0));
        g2.fillRect(0, 0, atlasWidth, atlasHeight);

        // качественное сглаживание текста
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,       RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,   RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,           RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,       RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,   RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setColor(Color.WHITE);
        g2.setFont(font);

        FontMetrics fm = g2.getFontMetrics();

        for (int i = 0; i < range; i++) {
            char c   = (char)(fromIncl + i);
            int  col = i % cols;
            int  row = i / cols;
            int  px  = col * colW;
            int  py  = row * rowH;
            int  gw  = metrics.get(i)[0];
            int  gh  = metrics.get(i)[1];

            g2.drawString(String.valueOf(c), px, py + fm.getAscent());
            glyphs.put(c, new GfxGlyph(px, py, gw, gh, metrics.get(i)[2], c, this));
        }
        g2.dispose();

        // ── 3. заливаем в текстуру ───────────────────────────────────────────
        uploadAtlas(textureId, img);
        generated = true;
    }

    private static void uploadAtlas(ResourceLocation id, BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        NativeImage ni = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int gg = (argb >>  8) & 0xFF;
                int b = argb & 0xFF;
                // NativeImage хранит ABGR
                ni.setPixelABGR(x, y, (a << 24) | (b << 16) | (gg << 8) | r);
            }
        }
        DynamicTexture tex = new DynamicTexture(() -> id.toString(), ni);
        tex.setFilter(true, false);
        tex.upload();
        Minecraft.getInstance().getTextureManager().register(id, tex);
    }

    static ResourceLocation nextTextureId() {
        return ResourceLocation.fromNamespaceAndPath(
            ValkyrieClient.MODID,
            "gfxfont/page_" + Integer.toHexString(GfxFontRenderer.nextTextureSerial())
        );
    }
}
