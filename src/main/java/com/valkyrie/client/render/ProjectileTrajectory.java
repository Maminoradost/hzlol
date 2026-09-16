package com.valkyrie.client.render;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Детерминированная клиентская симуляция ThrowableProjectile без RNG-разброса. */
public final class ProjectileTrajectory {
    private static ClientLevel cachedLevel;
    private static long cachedTick = Long.MIN_VALUE;
    private static int cachedMaxTicks;
    private static float cachedYaw;
    private static float cachedPitch;
    private static Vec3 cachedPosition;
    private static Vec3 cachedMovement;
    private static Path cachedPath;

    private ProjectileTrajectory() {
    }

    /**
     * Сбрасывает кэш при выходе из мира. Без этого статическая ссылка на
     * ClientLevel удерживает весь покинутый мир в памяти до следующего
     * вызова pearl() — то есть потенциально навсегда.
     */
    public static void reset() {
        cachedLevel = null;
        cachedPath = null;
        cachedPosition = null;
        cachedMovement = null;
        cachedTick = Long.MIN_VALUE;
    }

    public static Path pearl(ClientLevel level, LocalPlayer player, int maxTicks) {
        Vec3 playerPosition = player.position();
        Vec3 playerMovement = player.getKnownMovement();
        long gameTick = level.getGameTime();
        if (cachedPath != null && cachedLevel == level && cachedTick == gameTick && cachedMaxTicks == maxTicks
            && cachedYaw == player.getYRot() && cachedPitch == player.getXRot()
            && playerPosition.equals(cachedPosition) && playerMovement.equals(cachedMovement)) {
            return cachedPath;
        }
        Vec3 position = new Vec3(player.getX(), player.getEyeY() - 0.1, player.getZ());
        Vec3 direction = Vec3.directionFromRotation(player.getXRot(), player.getYRot());
        Vec3 movement = player.getKnownMovement();
        Vec3 velocity = direction.normalize().scale(1.5).add(
            movement.x,
            player.onGround() ? 0.0 : movement.y,
            movement.z
        );

        List<Vec3> points = new ArrayList<>(maxTicks + 1);
        points.add(position);
        HitResult hit = null;
        for (int tick = 0; tick < maxTicks; tick++) {
            boolean water = level.getFluidState(BlockPos.containing(position)).is(FluidTags.WATER);
            velocity = velocity.add(0.0, -0.03, 0.0).scale(water ? 0.8 : 0.99);
            Vec3 requested = position.add(velocity);
            BlockHitResult blockHit = level.clipIncludingBorder(new ClipContext(
                position,
                requested,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                CollisionContext.empty()
            ));
            Vec3 end = blockHit.getType() == HitResult.Type.MISS ? requested : blockHit.getLocation();
            points.add(end);
            position = end;
            if (blockHit.getType() != HitResult.Type.MISS) {
                hit = blockHit;
                break;
            }
        }
        cachedLevel = level;
        cachedTick = gameTick;
        cachedMaxTicks = maxTicks;
        cachedYaw = player.getYRot();
        cachedPitch = player.getXRot();
        cachedPosition = playerPosition;
        cachedMovement = playerMovement;
        cachedPath = new Path(List.copyOf(points), hit);
        return cachedPath;
    }

    public record Path(List<Vec3> points, HitResult hit) {
        public Vec3 end() {
            return points.get(points.size() - 1);
        }
    }
}
