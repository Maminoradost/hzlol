package com.valkyrie.client.render;

import com.valkyrie.client.combat.KillAuraEngine;
import com.valkyrie.client.module.ModuleRegistry;
import com.valkyrie.client.module.impl.MemoryEchoModule;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Тиковый буфер позиций для Memory Echo. */
public final class MemoryEchoTracker {
    private static final Deque<Sample> SELF = new ArrayDeque<>();
    private static final Deque<Sample> TARGET = new ArrayDeque<>();
    private static int sampledTargetId = Integer.MIN_VALUE;
    private static int tick;

    private MemoryEchoTracker() {
    }

    public static void tick() {
        MemoryEchoModule module = ModuleRegistry.get(MemoryEchoModule.class);
        Minecraft mc = Minecraft.getInstance();
        if (!module.isEnabled() || mc.player == null || mc.level == null) {
            clear();
            return;
        }
        // Раз в два тика: силуэты не слипаются и буфер не шумит.
        if ((tick++ & 1) != 0) {
            prune(module.lifetime.value());
            return;
        }

        int limit = Math.round(module.echoes.value());
        if (!module.entities.is("Target")) {
            sample(SELF, mc.player, module.movingOnly.value(), limit);
        } else {
            SELF.clear();
        }

        LivingEntity target = KillAuraEngine.target();
        if (!module.entities.is("Self") && target != null) {
            if (target.getId() != sampledTargetId) {
                TARGET.clear();
                sampledTargetId = target.getId();
            }
            sample(TARGET, target, module.movingOnly.value(), limit);
        } else if (module.entities.is("Self")) {
            TARGET.clear();
            sampledTargetId = Integer.MIN_VALUE;
        }
        prune(module.lifetime.value());
    }

    private static void sample(Deque<Sample> samples, LivingEntity entity, boolean movingOnly, int limit) {
        Vec3 velocity = entity.position().subtract(entity.xOld, entity.yOld, entity.zOld);
        if (movingOnly && velocity.lengthSqr() < 1.0E-4) {
            return;
        }
        Vec3 point = entity.position().add(0.0, entity.getBbHeight() * 0.52, 0.0);
        samples.addFirst(new Sample(point, System.nanoTime()));
        while (samples.size() > limit) {
            samples.removeLast();
        }
    }

    private static void prune(float lifetime) {
        long maxAge = (long) (lifetime * 1_000_000_000L);
        long now = System.nanoTime();
        SELF.removeIf(sample -> now - sample.createdAtNs > maxAge);
        TARGET.removeIf(sample -> now - sample.createdAtNs > maxAge);
    }

    public static Deque<Sample> self() {
        return SELF;
    }

    public static Deque<Sample> target() {
        return TARGET;
    }

    public static void clear() {
        SELF.clear();
        TARGET.clear();
        sampledTargetId = Integer.MIN_VALUE;
    }

    public record Sample(Vec3 position, long createdAtNs) {
    }
}
