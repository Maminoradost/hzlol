package com.valkyrie.client.combat;

import com.valkyrie.client.module.ModuleRegistry;
import com.valkyrie.client.module.impl.KillAuraModule;
import java.util.Optional;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.ClientInput;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Valkyrie Flow: единый pipeline выбора, ведения, raycast и тайминга атаки. */
public final class KillAuraEngine {
    private static final Random RANDOM = new Random();
    private static final double TRACK_MARGIN = 0.75;

    private static LivingEntity target;
    private static AimPoint.Relative aimSample = AimPoint.centerSample();
    private static int aimAge;
    private static int nextAimAge;
    private static int sinceSwitch;
    private static long nextLegacyAttackNs;
    private static AttackState state = AttackState.NO_TARGET;

    private KillAuraEngine() {
    }

    public static LivingEntity target() {
        return target;
    }

    public static AttackState state() {
        return state;
    }

    public static boolean attackReady() {
        return state == AttackState.READY;
    }

    public static void reset() {
        target = null;
        aimAge = 0;
        nextAimAge = 0;
        sinceSwitch = 0;
        nextLegacyAttackNs = 0L;
        state = AttackState.NO_TARGET;
        ValkyrieRotation.reset();
    }

    public static void onPreTick(ClientInput input) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        KillAuraModule module = ModuleRegistry.get(KillAuraModule.class);
        if (!module.isEnabled() || player == null || level == null || mc.screen != null
            || !player.isAlive() || player.isSpectator()) {
            reset();
            return;
        }

        sinceSwitch++;
        LivingEntity next = findTarget(module, player, level);
        if (next == null) {
            finishDeadTarget();
            target = null;
            state = AttackState.NO_TARGET;
            ValkyrieRotation.apply(player);
            return;
        }
        if (next != target) {
            finishDeadTarget();
            target = next;
            sinceSwitch = 0;
            resample(module);
            com.valkyrie.client.gui.notify.ValkyrieQuotes.onTarget();
        } else if (++aimAge >= nextAimAge) {
            resample(module);
        }

        Vec3 velocity = target.position().subtract(target.xOld, target.yOld, target.zOld);
        Vec3 prediction = velocity.scale(module.predict.value());
        Vec3 aim = aimSample.resolve(target, 1.0f, prediction);
        Vec3 eyes = player.getEyePosition();
        float wantYaw = yawTo(eyes, aim);
        float wantPitch = pitchTo(eyes, aim);
        boolean snap = module.rotation.is(KillAuraModule.MODE_SNAP);
        ValkyrieRotation.request(
            wantYaw,
            wantPitch,
            snap ? 180.0f : module.rotationSpeed.value(),
            module.rotation.is(KillAuraModule.MODE_SILENT)
        );
        ValkyrieRotation.apply(player);
        tryAttack(module, mc, player, level, input);
    }

    public static void onPostTick() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) ValkyrieRotation.restore(player);
    }

    private static void tryAttack(KillAuraModule module, Minecraft mc, LocalPlayer player, ClientLevel level, ClientInput input) {
        if (target == null) {
            state = AttackState.NO_TARGET;
            return;
        }
        double distance = distanceToBox(player.getEyePosition(), target);
        if (distance > module.range.value()) {
            state = AttackState.TRACKING;
            return;
        }
        if (player.isUsingItem()) {
            state = AttackState.ITEM_USE;
            return;
        }
        if (!CombatActions.prepareWeapon(player)) {
            state = AttackState.COOLDOWN;
            return;
        }
        long now = System.nanoTime();
        boolean legacyReady = now >= nextLegacyAttackNs;
        boolean modernReady = player.getAttackStrengthScale(0.0f) >= module.cooldownThreshold.value();
        if (!module.timing.is(KillAuraModule.TIMING_MODERN) && !legacyReady) {
            state = AttackState.CPS;
            return;
        }
        if (!module.timing.is(KillAuraModule.TIMING_LEGACY) && !modernReady) {
            state = AttackState.COOLDOWN;
            return;
        }
        RayResult ray = rayTarget(level, player, target, module.range.value(), module.wallCheck.value());
        if (!ray.entityHit) {
            state = ray.blocked ? AttackState.WALL : AttackState.ROTATING;
            return;
        }
        if (!CriticalsController.allowAttack(player, target, input)) {
            state = AttackState.COOLDOWN;
            return;
        }

        state = AttackState.READY;
        CombatActions.attack(mc, player, target);
        long interval = nextLegacyInterval(module);
        // Сохраняем дробную фазу между 20 TPS тиками, но не догоняем пропущенные удары burst-серией.
        long base = nextLegacyAttackNs > 0L && now - nextLegacyAttackNs < interval ? nextLegacyAttackNs : now;
        nextLegacyAttackNs = base + interval;
    }

    private static long nextLegacyInterval(KillAuraModule module) {
        double min = module.minCps.value();
        double max = module.effectiveMaxCps();
        // Треугольное распределение избегает механических крайних значений.
        double cps = min + ((RANDOM.nextDouble() + RANDOM.nextDouble()) * 0.5) * (max - min);
        return (long) (1_000_000_000.0 / Math.max(1.0, cps));
    }

    private static RayResult rayTarget(ClientLevel level, LocalPlayer player, LivingEntity entity, double reach, boolean wallCheck) {
        if (!ValkyrieRotation.hasServed()) return RayResult.MISS;
        Vec3 eyes = player.getEyePosition();
        Vec3 direction = direction(ValkyrieRotation.servedYaw(), ValkyrieRotation.servedPitch());
        Vec3 end = eyes.add(direction.scale(reach));
        Optional<Vec3> entityHit = entity.getBoundingBox().inflate(0.03).clip(eyes, end);
        if (entityHit.isEmpty()) return RayResult.MISS;

        if (!wallCheck) return RayResult.HIT;
        BlockHitResult block = level.clipIncludingBorder(new ClipContext(
            eyes, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player
        ));
        double entityDistance = eyes.distanceToSqr(entityHit.get());
        boolean blocked = block.getType() != HitResult.Type.MISS
            && eyes.distanceToSqr(block.getLocation()) + 1.0E-5 < entityDistance;
        return blocked ? RayResult.BLOCKED : RayResult.HIT;
    }

    private static LivingEntity findTarget(KillAuraModule module, LocalPlayer player, ClientLevel level) {
        Vec3 eyes = player.getEyePosition();
        double scan = module.range.value() + TRACK_MARGIN;
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !valid(module, player, living, eyes, scan)) continue;
            double score = switch (module.priority.value()) {
                case KillAuraModule.PRIORITY_HEALTH -> living.getHealth() + living.getAbsorptionAmount();
                case KillAuraModule.PRIORITY_ANGLE -> angleTo(player, living, true);
                default -> distanceToBox(eyes, living);
            };
            if (score < bestScore) {
                bestScore = score;
                best = living;
            }
        }
        if (target != null && target != best && sinceSwitch < module.switchDelay.value()
            && valid(module, player, target, eyes, scan)) {
            return target;
        }
        return best;
    }

    private static boolean valid(KillAuraModule module, LocalPlayer player, LivingEntity living, Vec3 eyes, double scan) {
        if (living == player || !living.isAlive() || living.isInvisible() || living.isSpectator()) return false;
        if (!module.targets.matches(living)) return false;
        if (living instanceof Player other && player.isAlliedTo(other)) return false;
        if (distanceToBox(eyes, living) > scan || !withinFov(module, player, living)) return false;
        return !module.wallCheck.value() || player.hasLineOfSight(living);
    }

    private static boolean withinFov(KillAuraModule module, LocalPlayer player, LivingEntity entity) {
        return module.fov.value() >= 360.0f || angleTo(player, entity, false) <= module.fov.value() * 0.5f;
    }

    private static double angleTo(LocalPlayer player, LivingEntity entity, boolean serverRotation) {
        Vec3 eyes = player.getEyePosition();
        Vec3 center = AimPoint.center(entity, 1.0f);
        float baseYaw = serverRotation && ValkyrieRotation.hasSent() ? ValkyrieRotation.sentYaw() : player.getYRot();
        float basePitch = serverRotation && ValkyrieRotation.hasSent() ? ValkyrieRotation.sentPitch() : player.getXRot();
        double dyaw = Mth.wrapDegrees(yawTo(eyes, center) - baseYaw);
        double dpitch = pitchTo(eyes, center) - basePitch;
        return Math.sqrt(dyaw * dyaw + dpitch * dpitch);
    }

    private static void resample(KillAuraModule module) {
        aimSample = module.randomCenter.value() ? AimPoint.randomSample(RANDOM) : AimPoint.centerSample();
        aimAge = 0;
        nextAimAge = 8 + RANDOM.nextInt(11);
    }

    private static void finishDeadTarget() {
        if (target != null && !target.isAlive()) {
            com.valkyrie.client.render.CombatVisuals.kill(target);
            com.valkyrie.client.gui.notify.ValkyrieQuotes.onKill();
        }
    }

    private static Vec3 direction(float yaw, float pitch) {
        float yawRad = yaw * Mth.DEG_TO_RAD;
        float pitchRad = pitch * Mth.DEG_TO_RAD;
        float cosPitch = Mth.cos(pitchRad);
        return new Vec3(-Mth.sin(yawRad) * cosPitch, -Mth.sin(pitchRad), Mth.cos(yawRad) * cosPitch);
    }

    private static double distanceToBox(Vec3 eyes, LivingEntity entity) {
        AABB box = entity.getBoundingBox();
        double dx = Math.max(Math.max(box.minX - eyes.x, 0.0), eyes.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - eyes.y, 0.0), eyes.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - eyes.z, 0.0), eyes.z - box.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static float yawTo(Vec3 from, Vec3 to) {
        return (float) (Math.toDegrees(Math.atan2(to.z - from.z, to.x - from.x)) - 90.0);
    }

    private static float pitchTo(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return (float) -Math.toDegrees(Math.atan2(to.y - from.y, Math.sqrt(dx * dx + dz * dz)));
    }

    public enum AttackState {
        NO_TARGET, TRACKING, CPS, COOLDOWN, ITEM_USE, ROTATING, WALL, READY
    }

    private record RayResult(boolean entityHit, boolean blocked) {
        private static final RayResult HIT = new RayResult(true, false);
        private static final RayResult MISS = new RayResult(false, false);
        private static final RayResult BLOCKED = new RayResult(false, true);
    }
}
