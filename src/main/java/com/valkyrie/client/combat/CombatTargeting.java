package com.valkyrie.client.combat;

import com.valkyrie.client.module.TargetFilter;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Общая геометрия и фильтры Combat-модулей. */
public final class CombatTargeting {
    private CombatTargeting() {
    }

    public static LivingEntity closestToCrosshair(
        ClientLevel level,
        LocalPlayer player,
        TargetFilter filter,
        double range,
        float fov,
        boolean wallCheck
    ) {
        LivingEntity best = null;
        double bestAngle = Double.MAX_VALUE;
        Vec3 eyes = player.getEyePosition();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)
                || !valid(player, living, filter, eyes, range, wallCheck)) {
                continue;
            }
            double angle = angle(player, living);
            if (angle <= fov * 0.5f && angle < bestAngle) {
                best = living;
                bestAngle = angle;
            }
        }
        return best;
    }

    public static boolean valid(
        LocalPlayer player,
        LivingEntity target,
        TargetFilter filter,
        double range,
        boolean wallCheck
    ) {
        return valid(player, target, filter, player.getEyePosition(), range, wallCheck);
    }

    private static boolean valid(
        LocalPlayer player,
        LivingEntity target,
        TargetFilter filter,
        Vec3 eyes,
        double range,
        boolean wallCheck
    ) {
        if (target == player || !target.isAlive() || target.isInvisible() || target.isSpectator()) return false;
        if (!filter.matches(target)) return false;
        if (target instanceof Player other && player.isAlliedTo(other)) return false;
        if (distanceToBox(eyes, target) > range) return false;
        return !wallCheck || player.hasLineOfSight(target);
    }

    public static double distanceToBox(Vec3 eyes, LivingEntity target) {
        AABB box = target.getBoundingBox();
        double dx = Math.max(Math.max(box.minX - eyes.x, 0.0), eyes.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - eyes.y, 0.0), eyes.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - eyes.z, 0.0), eyes.z - box.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static double angle(LocalPlayer player, LivingEntity target) {
        Vec3 eyes = player.getEyePosition();
        Vec3 point = AimPoint.center(target, 1.0f);
        double yaw = Mth.wrapDegrees(yawTo(eyes, point) - player.getYRot());
        double pitch = pitchTo(eyes, point) - player.getXRot();
        return Math.sqrt(yaw * yaw + pitch * pitch);
    }

    public static float yawTo(Vec3 from, Vec3 to) {
        return (float) (Math.toDegrees(Math.atan2(to.z - from.z, to.x - from.x)) - 90.0);
    }

    public static float pitchTo(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return (float) -Math.toDegrees(Math.atan2(to.y - from.y, Math.sqrt(dx * dx + dz * dz)));
    }
}
