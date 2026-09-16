package com.valkyrie.client.render;

import com.valkyrie.client.module.ModuleRegistry;
import com.valkyrie.client.module.impl.ProjectileTrailsModule;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractThrownPotion;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.phys.Vec3;

/** Ограниченные по времени мировые истории снарядов. */
public final class ProjectileTrailTracker {
    private static final Map<Integer, Trail> TRAILS = new HashMap<>();
    private static final Set<Integer> SEEN = new HashSet<>();

    private ProjectileTrailTracker() {
    }

    public static void tick() {
        ProjectileTrailsModule module = ModuleRegistry.get(ProjectileTrailsModule.class);
        Minecraft mc = Minecraft.getInstance();
        if (!module.isEnabled() || mc.level == null || mc.player == null) {
            TRAILS.clear();
            return;
        }
        SEEN.clear();
        double maxDistanceSqr = module.range.value() * module.range.value();
        for (Entity entity : mc.level.entitiesForRendering()) {
            Kind kind = kind(entity, module);
            if (kind == null || mc.player.distanceToSqr(entity) > maxDistanceSqr) {
                continue;
            }
            SEEN.add(entity.getId());
            Trail trail = TRAILS.computeIfAbsent(entity.getId(), id -> new Trail(kind));
            trail.samples.addFirst(new Sample(entity.position(), System.nanoTime()));
            while (trail.samples.size() > 60) {
                trail.samples.removeLast();
            }
        }
        long maxAge = (long) (module.lifetime.value() * 1_000_000_000L);
        long now = System.nanoTime();
        TRAILS.values().forEach(trail -> trail.samples.removeIf(sample -> now - sample.createdAtNs > maxAge));
        TRAILS.entrySet().removeIf(entry -> entry.getValue().samples.isEmpty() && !SEEN.contains(entry.getKey()));
    }

    private static Kind kind(Entity entity, ProjectileTrailsModule module) {
        if (entity instanceof ThrownEnderpearl) return module.pearls.value() ? Kind.PEARL : null;
        if (entity instanceof ThrownTrident) return module.tridents.value() ? Kind.TRIDENT : null;
        if (entity instanceof AbstractArrow) return module.arrows.value() ? Kind.ARROW : null;
        if (entity instanceof Snowball || entity instanceof AbstractThrownPotion) return module.throwables.value() ? Kind.THROWABLE : null;
        return null;
    }

    public static Map<Integer, Trail> trails() {
        return TRAILS;
    }

    public enum Kind { PEARL, ARROW, THROWABLE, TRIDENT }

    public static final class Trail {
        public final Kind kind;
        public final ArrayDeque<Sample> samples = new ArrayDeque<>();

        Trail(Kind kind) {
            this.kind = kind;
        }
    }

    public record Sample(Vec3 position, long createdAtNs) {
    }
}
