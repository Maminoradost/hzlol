package com.valkyrie.client.combat;

import java.util.random.RandomGenerator;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Выбор точки прицеливания внутри хитбокса цели.
 * <p>
 * Центр масс и голова — главный статистический маркер киллауры: у человека
 * попадание распределено по корпусу, у бота собрано в одну точку. Точка
 * пересчитывается редко (см. {@link #shouldResample}), потому что дрожание
 * каждый тик даёт «shake», который отдельно ловят Matrix и Grizzly.
 */
public final class AimPoint {
    private AimPoint() {
    }

    /**
     * Случайная точка в хитбоксе, поджатая к корпусу.
     * <p>
     * Края обрезаются: прицел точно по границе бокса выглядит как перебор
     * значений, да и сервер при своём reach-расчёте может такую атаку не зачесть.
     */
    public static Vec3 random(LivingEntity target, RandomGenerator random, float partialTick) {
        return randomSample(random).resolve(target, partialTick, Vec3.ZERO);
    }

    /** Геометрический центр — запасной вариант, когда рандом отключён. */
    public static Vec3 center(LivingEntity target, float partialTick) {
        AABB box = interpolatedBox(target, partialTick);
        return new Vec3(box.getCenter().x, box.minY + box.getYsize() * 0.7, box.getCenter().z);
    }

    /**
     * Хитбокс в позиции кадра.
     * <p>
     * {@code getBoundingBox()} отстаёт на тик, и по нему прицел тянулся бы за
     * бегущей целью.
     */
    public static AABB interpolatedBox(Entity entity, float partialTick) {
        Vec3 now = entity.getPosition(partialTick);
        AABB box = entity.getBoundingBox();
        return box.move(now.subtract(entity.position()));
    }

    /**
     * Пора ли выбрать новую точку.
     * <p>
     * Привязка к смене цели и к самому удару: между ударами взгляд человека
     * плавно ведёт одну точку, а перескакивает как раз в момент замаха.
     */
    public static boolean shouldResample(int ticksSinceSample, int minTicks) {
        return ticksSinceSample >= minTicks;
    }

    /** Точка хранится в долях хитбокса и поэтому не отстаёт от движущейся цели. */
    public static Relative randomSample(RandomGenerator random) {
        return new Relative(
            Mth.lerp(random.nextDouble(), 0.25, 0.75),
            Mth.lerp(random.nextDouble(), 0.45, 0.85),
            Mth.lerp(random.nextDouble(), 0.25, 0.75)
        );
    }

    public static Relative centerSample() {
        return new Relative(0.5, 0.7, 0.5);
    }

    public record Relative(double x, double y, double z) {
        public Vec3 resolve(LivingEntity target, float partialTick, Vec3 prediction) {
            AABB box = interpolatedBox(target, partialTick).move(prediction);
            return new Vec3(
                Mth.lerp(x, box.minX, box.maxX),
                Mth.lerp(y, box.minY, box.maxY),
                Mth.lerp(z, box.minZ, box.maxZ)
            );
        }
    }
}
