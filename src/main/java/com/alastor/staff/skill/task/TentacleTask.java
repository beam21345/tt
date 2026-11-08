package com.alastor.staff.skill.task;

import com.alastor.staff.entity.ShadowFxEntity;
import com.alastor.staff.entity.TentacleEntity;
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

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One big 3D shadow tentacle: warning sigil -> erupts out of the ground in green light -> sways ->
 * (optionally) whips down and SLAMS the ground in front of it -> sinks back.
 */
public class TentacleTask implements SkillTask {
    private static final int RISE = 6, HOLD = 12, SLAM = 5;

    private final Player owner;
    private final Vec3 base;
    private final double maxHeight;
    private final float damage;
    private final double radius;
    @Nullable private final Vec3 slamDir;
    private final Set<UUID> hit = new HashSet<>();
    private TentacleEntity tentacle;
    private Vec3 lean;
    private int delay;
    private int age;
    private boolean warned;

    public TentacleTask(Player owner, Vec3 base, int delay, double maxHeight, float damage, double radius) {
        this(owner, base, delay, maxHeight, damage, radius, null);
    }

    /** @param slamDir horizontal direction to slam toward at the end, or null for no slam. */
    public TentacleTask(Player owner, Vec3 base, int delay, double maxHeight, float damage, double radius, @Nullable Vec3 slamDir) {
        this.owner = owner;
        this.base = base;
        this.delay = delay;
        this.maxHeight = maxHeight;
        this.damage = damage;
        this.radius = radius;
        this.slamDir = slamDir;
    }

    private int total() {
        return RISE + HOLD + (slamDir != null ? SLAM + 4 : 0) + TentacleEntity.FALL_TICKS;
    }

    @Override
    public boolean tick(ServerLevel level) {
        if (!warned) {
            warned = true;
            ShadowFxEntity.spawn(level, ShadowFxEntity.SIGIL, base, (float) (1.2 + radius * 0.6), delay + total(), Math.max(2, delay), 0f);
        }
        if (delay > 0) {
            delay--;
            if (delay % 2 == 0) level.sendParticles(ShadowFx.GREEN_SMALL, base.x, base.y + 0.1, base.z, 4, 0.6, 0.02, 0.6, 0);
            return false;
        }
        age++;
        if (age == 1) {
            double lx = (owner.getRandom().nextDouble() - 0.5) * maxHeight * 0.18;
            double lz = (owner.getRandom().nextDouble() - 0.5) * maxHeight * 0.18;
            lean = new Vec3(lx, maxHeight, lz);
            float thick = (float) (1.0 + maxHeight * 0.06);
            tentacle = TentacleEntity.spawn(level, base, lean, total(), RISE, thick);
            level.sendParticles(ParticleTypes.SQUID_INK, base.x, base.y + 0.2, base.z, 25, 0.8, 0.1, 0.8, 0.08);
            level.sendParticles(ParticleTypes.SCULK_SOUL, base.x, base.y + 0.2, base.z, 8, 0.6, 0.1, 0.6, 0.06);
            level.sendParticles(ShadowFx.GREEN, base.x, base.y + 0.3, base.z, 20, 0.7, 0.4, 0.7, 0);
            if (owner.getRandom().nextFloat() < 0.5f) ShadowFx.crackle(level, base, 2.5, 1);
            level.playSound(null, base.x, base.y, base.z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 1.0f, 0.5f);
            level.playSound(null, base.x, base.y, base.z, SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 1.2f, 0.6f);
        }

        if (age <= RISE + 4) {
            double h = maxHeight * Math.min(1.0, (double) age / RISE);
            hitBox(level, new AABB(base.x - radius, base.y - 0.5, base.z - radius, base.x + radius, base.y + h, base.z + radius),
                    damage, new Vec3(0, 1.0, 0));
        }

        if (slamDir != null && tentacle != null) {
            int s = age - (RISE + HOLD);
            if (s > 0 && s <= SLAM) {
                // whip: rear back a little, then crash down in front
                double f = (double) s / SLAM;
                Vec3 ground = slamDir.scale(maxHeight * 0.75).add(0, 0.4, 0);
                Vec3 back = slamDir.scale(-maxHeight * 0.25).add(0, maxHeight * 1.05, 0);
                Vec3 tip = f < 0.4 ? lerp(lean, back, f / 0.4) : lerp(back, ground, (f - 0.4) / 0.6);
                tentacle.setTip(tip);
                if (s == SLAM) slam(level, base.add(slamDir.scale(maxHeight * 0.7)));
            }
        }
        return age >= total();
    }

    private void slam(ServerLevel level, Vec3 at) {
        double y = Targeting.groundY(level, at.x, at.y + 1, at.z);
        Vec3 p = new Vec3(at.x, y, at.z);
        ShadowFxEntity.spawn(level, ShadowFxEntity.RING, p, 9f, 16, 10, 0f);
        ShadowFx.crackle(level, p, 5, 3);
        level.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y + 0.5, p.z, 2, 0.8, 0.2, 0.8, 0);
        level.sendParticles(ParticleTypes.SQUID_INK, p.x, p.y + 0.3, p.z, 40, 1.5, 0.2, 1.5, 0.15);
        level.sendParticles(ShadowFx.GREEN, p.x, p.y + 0.5, p.z, 30, 1.5, 0.4, 1.5, 0);
        level.playSound(null, p.x, p.y, p.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.9f, 0.6f);
        hit.clear();
        hitBox(level, new AABB(p.x - 6, p.y - 1, p.z - 6, p.x + 6, p.y + 4, p.z + 6), damage * 0.8f, new Vec3(0, 0.7, 0));
    }

    private void hitBox(ServerLevel level, AABB box, float dmg, Vec3 push) {
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> Targeting.isValid(owner, x))) {
            if (hit.add(e.getUUID())) {
                e.invulnerableTime = 0;
                Targeting.hurt(owner, e, dmg);
                Targeting.launch(e, e.getDeltaMovement().add(push));
            }
        }
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return a.add(b.subtract(a).scale(t));
    }
}
