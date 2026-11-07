package com.alastor.staff.skill.task;

import com.alastor.staff.entity.ShadowFxEntity;
import com.alastor.staff.entity.TentacleEntity;
import com.alastor.staff.util.Targeting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** One 3D shadow tentacle bursting out of the ground: warning sigil -> rise -> sway -> sink. */
public class TentacleTask implements SkillTask {
    private static final int RISE = 6, HOLD = 16;

    private final Player owner;
    private final Vec3 base;
    private final double maxHeight;
    private final float damage;
    private final double radius;
    private final Set<UUID> hit = new HashSet<>();
    private int delay;
    private int age;
    private boolean warned;

    public TentacleTask(Player owner, Vec3 base, int delay, double maxHeight, float damage, double radius) {
        this.owner = owner;
        this.base = base;
        this.delay = delay;
        this.maxHeight = maxHeight;
        this.damage = damage;
        this.radius = radius;
    }

    @Override
    public boolean tick(ServerLevel level) {
        if (!warned) {
            warned = true;
            ShadowFxEntity.spawn(level, ShadowFxEntity.SIGIL, base, 1.2f, delay + RISE + HOLD + 4, Math.max(2, delay), 0f);
        }
        if (delay > 0) {
            delay--;
            return false;
        }
        age++;
        if (age == 1) {
            double lx = (owner.getRandom().nextDouble() - 0.5) * 1.6;
            double lz = (owner.getRandom().nextDouble() - 0.5) * 1.6;
            float thick = (float) (0.6 + maxHeight * 0.05);
            TentacleEntity.spawn(level, base, new Vec3(lx, maxHeight, lz), RISE + HOLD + TentacleEntity.FALL_TICKS, RISE, thick);
            level.sendParticles(ParticleTypes.SQUID_INK, base.x, base.y + 0.2, base.z, 12, 0.5, 0.1, 0.5, 0.05);
            level.sendParticles(ParticleTypes.SCULK_SOUL, base.x, base.y + 0.2, base.z, 4, 0.4, 0.1, 0.4, 0.03);
            level.playSound(null, base.x, base.y, base.z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 0.8f, 0.6f);
        }

        if (age <= RISE + 4) {
            double h = maxHeight * Math.min(1.0, (double) age / RISE);
            AABB box = new AABB(base.x - radius, base.y - 0.5, base.z - radius,
                    base.x + radius, base.y + h, base.z + radius);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> Targeting.isValid(owner, x))) {
                if (hit.add(e.getUUID())) {
                    Targeting.hurt(owner, e, damage);
                    Targeting.launch(e, e.getDeltaMovement().add(0, 0.9, 0));
                }
            }
        }
        return age >= RISE + 4;
    }
}
