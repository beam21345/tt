package com.alastor.staff.skill.task;

import com.alastor.staff.fx.ShadowFx;
import com.alastor.staff.util.Targeting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Radio Sound: a black/green wave expanding outward over a 20x20 area. */
public class ShockwaveTask implements SkillTask {
    private static final double HALF = 10.0;   // 20x20 blocks
    private static final int DURATION = 14;
    private static final float DAMAGE = 12f;

    private final Player owner;
    private final Vec3 center;
    private final Set<UUID> hit = new HashSet<>();
    private int age;

    public ShockwaveTask(Player owner, Vec3 center) {
        this.owner = owner;
        this.center = center;
    }

    @Override
    public boolean tick(ServerLevel level) {
        age++;
        double r = HALF * age / DURATION;
        int pts = (int) (r * 10) + 10;
        ShadowFx.ring(level, center.add(0, 0.3, 0), r, pts, ShadowFx.BLACK, age * 0.1);
        ShadowFx.ring(level, center.add(0, 0.8, 0), r, pts / 2, ShadowFx.GREEN, age * 0.1 + 0.2);
        if (age % 3 == 0) {
            ShadowFx.ring(level, center.add(0, 0.5, 0), r, pts / 4, ParticleTypes.SCULK_SOUL, 0);
        }

        boolean last = age >= DURATION;
        AABB box = new AABB(center.x - HALF, center.y - 2, center.z - HALF, center.x + HALF, center.y + 5, center.z + HALF);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> Targeting.isValid(owner, x))) {
            double dx = e.getX() - center.x, dz = e.getZ() - center.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if ((d <= r + 1.0 || last) && hit.add(e.getUUID())) {
                Vec3 away = d < 0.01 ? new Vec3(1, 0, 0) : new Vec3(dx / d, 0, dz / d);
                Targeting.hurt(owner, e, DAMAGE);
                Targeting.launch(e, away.scale(1.6).add(0, 0.5, 0));
                level.sendParticles(ShadowFx.GREEN, e.getX(), e.getY() + 1, e.getZ(), 8, 0.3, 0.5, 0.3, 0);
            }
        }
        return last;
    }
}
