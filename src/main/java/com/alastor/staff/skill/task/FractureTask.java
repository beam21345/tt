package com.alastor.staff.skill.task;

import com.alastor.staff.fx.ShadowFx;
import com.alastor.staff.util.Targeting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shadow Fracture: a green-lit crack runs 15 blocks forward. Everything it touches sinks into the
 * shadow and is dragged along to the end of the crack, where the shadow erupts.
 */
public class FractureTask implements SkillTask {
    private static final int LENGTH = 15;
    private static final int EXPLODE_AT = LENGTH + 8;
    private static final float SWALLOWED_DAMAGE = 22f;
    private static final float SPLASH_DAMAGE = 10f;

    private final Player owner;
    private final Vec3 origin;
    private final Vec3 dir;
    private final Vec3 perp;
    private final List<Vec3> path = new ArrayList<>();
    private final Map<UUID, LivingEntity> swallowed = new LinkedHashMap<>();
    private double curY;
    private int age;

    public FractureTask(Player owner, Vec3 origin, Vec3 dir) {
        this.owner = owner;
        this.origin = origin;
        this.dir = dir;
        this.perp = new Vec3(-dir.z, 0, dir.x);
        this.curY = origin.y;
        path.add(origin);
    }

    @Override
    public boolean tick(ServerLevel level) {
        age++;

        if (age <= LENGTH) {
            Vec3 flat = origin.add(dir.scale(age));
            curY = Targeting.groundY(level, flat.x, curY, flat.z);
            Vec3 head = new Vec3(flat.x, curY, flat.z);
            path.add(head);
            level.sendParticles(ParticleTypes.SQUID_INK, head.x, head.y + 0.2, head.z, 4, 0.3, 0.05, 0.3, 0.02);
            if (age % 3 == 0) {
                level.playSound(null, head.x, head.y, head.z, SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 1.0f, 0.5f);
            }

            AABB box = new AABB(head.x - 1.5, head.y - 1, head.z - 1.5, head.x + 1.5, head.y + 3, head.z + 1.5);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> Targeting.isValid(owner, x))) {
                if (!swallowed.containsKey(e.getUUID())) {
                    swallowed.put(e.getUUID(), e);
                    level.sendParticles(ShadowFx.BLACK, e.getX(), e.getY() + 0.5, e.getZ(), 20, 0.4, 0.6, 0.4, 0);
                    level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0f, 0.6f);
                }
            }
        }

        // The jagged crack: black rift with green light glowing out of it.
        if (age % 2 == 0) {
            for (int i = 0; i < path.size(); i++) {
                Vec3 p = path.get(i);
                double jag = Math.sin(i * 1.7) * 0.25;
                Vec3 q = p.add(perp.scale(jag)).add(0, 0.08, 0);
                level.sendParticles(ShadowFx.BLACK, q.x, q.y, q.z, 2, 0.2, 0.0, 0.2, 0);
                level.sendParticles(ShadowFx.GREEN_SMALL, q.x, q.y + 0.05, q.z, 1, 0.08, 0.0, 0.08, 0);
                if (i % 3 == 0) level.sendParticles(ParticleTypes.SCULK_SOUL, q.x, q.y + 0.1, q.z, 1, 0.1, 0.1, 0.1, 0.02);
            }
        }

        // Drag everything swallowed toward the current tip of the crack, half sunk into the ground.
        Vec3 tip = path.get(path.size() - 1);
        Iterator<LivingEntity> it = swallowed.values().iterator();
        while (it.hasNext()) {
            LivingEntity e = it.next();
            if (!e.isAlive() || e.isRemoved()) { it.remove(); continue; }
            Vec3 target = new Vec3(tip.x, tip.y - 0.4, tip.z);
            Vec3 cur = e.position();
            Vec3 next = cur.add(target.subtract(cur).scale(0.35));
            e.teleportTo(next.x, next.y, next.z);
            e.setDeltaMovement(Vec3.ZERO);
            e.fallDistance = 0;
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 6, false, false));
            level.sendParticles(ShadowFx.BLACK, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 6, 0.35, 0.5, 0.35, 0);
        }

        if (age == LENGTH + 1) {
            level.playSound(null, tip.x, tip.y, tip.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.5f, 0.8f);
        }
        if (age >= EXPLODE_AT) {
            explode(level, tip);
            return true;
        }
        return false;
    }

    private void explode(ServerLevel level, Vec3 c) {
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(ShadowFx.BLACK, c.x, c.y + 1, c.z, 150, 2.0, 1.5, 2.0, 0);
        level.sendParticles(ShadowFx.GREEN, c.x, c.y + 1, c.z, 100, 2.0, 1.5, 2.0, 0);
        level.sendParticles(ParticleTypes.SCULK_SOUL, c.x, c.y + 1, c.z, 30, 1.5, 1.0, 1.5, 0.1);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0f, 0.7f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.5f, 0.8f);

        for (LivingEntity e : swallowed.values()) {
            if (!e.isAlive()) continue;
            e.teleportTo(c.x, c.y + 0.2, c.z);
            Targeting.hurt(owner, e, SWALLOWED_DAMAGE);
            double a = owner.getRandom().nextDouble() * Math.PI * 2;
            Targeting.launch(e, new Vec3(Math.cos(a) * 0.6, 1.2, Math.sin(a) * 0.6));
        }
        AABB box = new AABB(c.x - 4, c.y - 2, c.z - 4, c.x + 4, c.y + 4, c.z + 4);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> Targeting.isValid(owner, x))) {
            if (swallowed.containsKey(e.getUUID())) continue;
            Targeting.hurt(owner, e, SPLASH_DAMAGE);
            Vec3 away = e.position().subtract(c);
            away = new Vec3(away.x, 0, away.z);
            if (away.lengthSqr() > 1.0E-4) Targeting.launch(e, away.normalize().scale(1.2).add(0, 0.6, 0));
        }
    }
}
