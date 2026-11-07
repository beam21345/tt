package com.alastor.staff.skill.task;

import com.alastor.staff.fx.ShadowFx;
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

/** One shadow tentacle bursting out of the ground: warning sigil -> rise -> sway -> sink. */
public class TentacleTask implements SkillTask {
    private static final int RISE = 6, HOLD = 16, FALL = 6;

    private final Player owner;
    private final Vec3 base;
    private final double maxHeight;
    private final float damage;
    private final double radius;
    private final double phase;
    private final Set<UUID> hit = new HashSet<>();
    private int delay;
    private int age;

    public TentacleTask(Player owner, Vec3 base, int delay, double maxHeight, float damage, double radius) {
        this.owner = owner;
        this.base = base;
        this.delay = delay;
        this.maxHeight = maxHeight;
        this.damage = damage;
        this.radius = radius;
        this.phase = owner.getRandom().nextDouble() * Math.PI * 2;
    }

    @Override
    public boolean tick(ServerLevel level) {
        if (delay > 0) {
            if (delay % 2 == 0) ShadowFx.ring(level, base.add(0, 0.05, 0), 0.9, 10, ShadowFx.GREEN_SMALL, delay * 0.3);
            delay--;
            return false;
        }
        age++;
        if (age == 1) {
            level.sendParticles(ParticleTypes.SQUID_INK, base.x, base.y + 0.2, base.z, 12, 0.5, 0.1, 0.5, 0.05);
            level.sendParticles(ParticleTypes.SCULK_SOUL, base.x, base.y + 0.2, base.z, 4, 0.4, 0.1, 0.4, 0.03);
            level.playSound(null, base.x, base.y, base.z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 0.8f, 0.6f);
        }

        double h;
        if (age <= RISE) h = maxHeight * age / RISE;
        else if (age <= RISE + HOLD) h = maxHeight;
        else h = maxHeight * (1.0 - (age - RISE - HOLD) / (double) FALL);

        double ph = phase + age * 0.35;
        Vec3 tip = base.add(Math.sin(ph) * 0.8, h, Math.cos(ph) * 0.8);
        ShadowFx.tentacle(level, base, tip, ph);
        ShadowFx.ring(level, base.add(0, 0.05, 0), 0.8, 10, ShadowFx.GREEN_SMALL, ph);

        if (age <= RISE + 4 && h > 0.5) {
            AABB box = new AABB(base.x - radius, base.y - 0.5, base.z - radius,
                    base.x + radius, base.y + h, base.z + radius);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> Targeting.isValid(owner, x))) {
                if (hit.add(e.getUUID())) {
                    Targeting.hurt(owner, e, damage);
                    Targeting.launch(e, e.getDeltaMovement().add(0, 0.9, 0));
                }
            }
        }
        return age >= RISE + HOLD + FALL;
    }
}
