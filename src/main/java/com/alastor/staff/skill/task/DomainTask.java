package com.alastor.staff.skill.task;

import com.alastor.staff.fx.ShadowFx;
import com.alastor.staff.util.Targeting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Shadow Domain: a 15x15 field of shadow; anything that enters is pinned in place by tentacles. */
public class DomainTask implements SkillTask {
    private static final double HALF = 7.5;     // 15x15 blocks
    private static final int DURATION = 200;    // 10 seconds

    private final Player owner;
    private final Vec3 center;
    private final Map<UUID, Vec3> heldAt = new HashMap<>();
    private final Map<UUID, LivingEntity> held = new HashMap<>();
    private int age;

    public DomainTask(Player owner, Vec3 center) {
        this.owner = owner;
        this.center = center;
    }

    @Override
    public boolean tick(ServerLevel level) {
        age++;
        RandomSource rnd = level.getRandom();

        if (age % 2 == 0) {
            double y = center.y + 0.1;
            for (double t = -HALF; t <= HALF; t += 0.5) {
                level.sendParticles(ShadowFx.BLACK, center.x + t, y, center.z - HALF, 1, 0, 0.3, 0, 0);
                level.sendParticles(ShadowFx.BLACK, center.x + t, y, center.z + HALF, 1, 0, 0.3, 0, 0);
                level.sendParticles(ShadowFx.BLACK, center.x - HALF, y, center.z + t, 1, 0, 0.3, 0, 0);
                level.sendParticles(ShadowFx.BLACK, center.x + HALF, y, center.z + t, 1, 0, 0.3, 0, 0);
            }
            for (int i = 0; i < 30; i++) {
                double x = center.x + (rnd.nextDouble() * 2 - 1) * HALF;
                double z = center.z + (rnd.nextDouble() * 2 - 1) * HALF;
                level.sendParticles(ShadowFx.BLACK, x, center.y + 0.1, z, 1, 0.2, 0.02, 0.2, 0);
            }
            level.sendParticles(ParticleTypes.SCULK_SOUL, center.x, center.y + 0.2, center.z, 2, HALF * 0.6, 0.1, HALF * 0.6, 0.01);
        }
        if (age % 6 == 0) ShadowFx.sigil(level, center, 6.0, age * 0.02);
        if (age % 40 == 1) {
            level.playSound(null, center.x, center.y, center.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.5f, 0.6f);
        }

        AABB box = new AABB(center.x - HALF, center.y - 2, center.z - HALF, center.x + HALF, center.y + 5, center.z + HALF);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> Targeting.isValid(owner, x))) {
            if (!held.containsKey(e.getUUID())) {
                held.put(e.getUUID(), e);
                heldAt.put(e.getUUID(), e.position());
                level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0f, 0.5f);
            }
        }

        Iterator<Map.Entry<UUID, LivingEntity>> it = held.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, LivingEntity> entry = it.next();
            LivingEntity e = entry.getValue();
            if (!e.isAlive() || e.isRemoved()) {
                heldAt.remove(entry.getKey());
                it.remove();
                continue;
            }
            Vec3 pin = heldAt.get(entry.getKey());
            if (e.position().distanceToSqr(pin) > 0.01) e.teleportTo(pin.x, pin.y, pin.z);
            e.setDeltaMovement(Vec3.ZERO);
            e.hurtMarked = true;
            e.fallDistance = 0;
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 10, false, false));
            if (e instanceof Mob mob) mob.getNavigation().stop();
            if (age % 20 == 0) Targeting.hurt(owner, e, 2f);

            if (age % 2 == 0) {
                double body = e.getBbHeight() * 0.6;
                for (int k = 0; k < 3; k++) {
                    double a = age * 0.15 + k * Math.PI * 2 / 3;
                    Vec3 base = new Vec3(pin.x + Math.cos(a) * 1.1, pin.y, pin.z + Math.sin(a) * 1.1);
                    ShadowFx.tentacle(level, base, pin.add(0, body, 0), a);
                }
            }
        }
        return age >= DURATION;
    }
}
