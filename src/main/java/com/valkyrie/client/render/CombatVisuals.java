package com.valkyrie.client.render;

import com.valkyrie.client.module.ModuleRegistry;
import com.valkyrie.client.module.impl.SakuraBloomModule;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;

/** Короткоживущие боевые отклики: hit marker и Sakura Combat Bloom. */
public final class CombatVisuals {
    private static final int MAX_BLOOMS = 10;
    private static final int PETAL = 0xFFE8B4C8;
    private static final int PETAL_LIGHT = 0xFFF2CEDE;
    private static final List<Bloom> BLOOMS = new ArrayList<>();
    private static long hitAtNs;

    private CombatVisuals() {
    }

    public static void hit(LivingEntity target) {
        hitAtNs = System.nanoTime();
        SakuraBloomModule module = ModuleRegistry.get(SakuraBloomModule.class);
        if (module.isEnabled() && module.hitBloom.value() && target != null) {
            spawn(target, false, module);
        }
    }

    public static void kill(LivingEntity target) {
        SakuraBloomModule module = ModuleRegistry.get(SakuraBloomModule.class);
        if (module.isEnabled() && module.killBloom.value() && target != null) {
            spawn(target, true, module);
        }
    }

    private static void spawn(LivingEntity target, boolean kill, SakuraBloomModule module) {
        if (BLOOMS.size() >= MAX_BLOOMS) {
            BLOOMS.remove(0);
        }
        Vec3 anchor = target.position().add(0.0, target.getBbHeight() * 0.62, 0.0);
        BLOOMS.add(new Bloom(anchor, module.petalCount(kill), kill, module.wind.value()));
    }

    /** 1 сразу после удара, 0 спустя 320 мс. */
    public static float hitFade() {
        float age = (System.nanoTime() - hitAtNs) / 1_000_000_000.0f;
        return Math.max(0.0f, 1.0f - age / 0.32f);
    }

    public static void renderBlooms(GuiGraphics gg, Matrix4f matrix) {
        long now = System.nanoTime();
        Iterator<Bloom> iterator = BLOOMS.iterator();
        while (iterator.hasNext()) {
            Bloom bloom = iterator.next();
            float age = (now - bloom.createdAtNs) / 1_000_000_000.0f;
            if (age >= bloom.life) {
                iterator.remove();
                continue;
            }
            ValkyrieProjection.ScreenPos anchor = ValkyrieProjection.project(matrix, bloom.anchor, gg.guiWidth(), gg.guiHeight());
            if (anchor == null) {
                continue;
            }
            bloom.render(gg, anchor.x(), anchor.y(), age);
        }
    }

    private static final class Bloom {
        final Vec3 anchor;
        final List<PetalState> petals;
        final long createdAtNs = System.nanoTime();
        final float life;
        final boolean wind;

        Bloom(Vec3 anchor, int count, boolean kill, boolean wind) {
            this.anchor = anchor;
            this.wind = wind;
            this.life = kill ? 1.35f : 0.82f;
            this.petals = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                double angle = Math.random() * Math.PI * 2.0;
                float speed = (kill ? 18.0f : 12.0f) + (float) Math.random() * (kill ? 24.0f : 15.0f);
                petals.add(new PetalState(
                    (float) Math.cos(angle) * speed,
                    (float) Math.sin(angle) * speed,
                    (float) (Math.random() * Math.PI * 2.0),
                    (float) ((Math.random() - 0.5) * 5.0),
                    Math.random() < 0.35 ? PETAL_LIGHT : PETAL,
                    0.72f + (float) Math.random() * 0.55f
                ));
            }
        }

        void render(GuiGraphics gg, float anchorX, float anchorY, float age) {
            float fade = 1.0f - Mth.clamp(age / life, 0.0f, 1.0f);
            float bloom = Mth.clamp(age / 0.14f, 0.0f, 1.0f);
            for (PetalState p : petals) {
                float x = anchorX + p.vx * age + (wind ? age * age * 16.0f : 0.0f);
                float y = anchorY + p.vy * age + age * age * 12.0f;
                float rotation = p.rotation + p.spin * age;
                int color = com.valkyrie.client.gui.theme.Theme.alpha(p.color, fade * bloom * 0.78f);
                drawPetal(gg, x, y, rotation, p.scale, color);
            }
        }
    }

    private record PetalState(float vx, float vy, float rotation, float spin, int color, float scale) {
    }

    /** Маленькая капля из трёх сегментов, как у фоновой Sakura-системы. */
    private static void drawPetal(GuiGraphics gg, float x, float y, float rotation, float scale, int color) {
        Matrix3x2fStack pose = gg.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.rotate(rotation);
        pose.scale(scale, scale);
        gg.fill(-1, -3, 2, 3, color);
        gg.fill(0, -4, 1, -3, color);
        gg.fill(0, 3, 1, 4, color);
        pose.popMatrix();
    }
}
