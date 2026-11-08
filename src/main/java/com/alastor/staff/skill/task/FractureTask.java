package com.alastor.staff.skill.task;

import com.alastor.staff.entity.ShadowFxEntity;
import com.alastor.staff.entity.TentacleEntity;
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
 * Shadow Fracture: a glowing rift races 30 blocks forward and swallows everything in its path.
 * Victims are dragged to the end of the rift, tentacles burst from the ground and hold them,
 * then everything erupts in a gigantic pillar of green light.
 */
public class FractureTask implements SkillTask {
    public static final int LENGTH = 30;
    private static final double WIDTH = 3.0;
    private static final int GRAB_AT = LENGTH + 6;
    private static final int EXPLODE_AT = GRAB_AT + 14;
    private static final float SWALLOWED_DAMAGE = 26f;
    private static final float SPLASH_DAMAGE = 12f;

    private final Player owner;
    private final Vec3 origin;
    private final Vec3 dir;
    private final List<Vec3> path = new ArrayList<>();
    private final Map<UUID, LivingEntity> swallowed = new LinkedHashMap<>();
    private final List<TentacleEntity> holders = new ArrayList<>();
    private double curY;
    private int age;

    public FractureTask(Player owner, Vec3 origin, Vec3 dir) {
        this.owner = owner;
        this.origin = origin;
        this.dir = dir;
        this.curY = origin.y;
        path.add(origin);
    }

    @Override
    public boolean tick(ServerLevel level) {
        age++;
        Vec3 tip = path.get(path.size() - 1);

        if (age <= LENGTH) {
            Vec3 flat = origin.add(dir.scale(age));
            curY = Targeting.groundY(level, flat.x, curY, flat.z);
            Vec3 head = new Vec3(flat.x, curY, flat.z);
            path.add(head);
            tip = head;
            level.sendParticles(ParticleTypes.SQUID_INK, head.x, head.y + 0.2, head.z, 14, 1.0, 0.05, 1.0, 0.08);
            level.sendParticles(ShadowFx.GREEN, head.x, head.y + 0.5, head.z, 10, 0.8, 0.5, 0.8, 0);
            if (age % 4 == 0) ShadowFx.crackle(level, head, 2.5, 1);
            if (age % 3 == 0) {
                level.playSound(null, head.x, head.y, head.z, SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 1.2f, 0.5f);
            }

            AABB box = new AABB(head.x - WIDTH, head.y - 1, head.z - WIDTH, head.x + WIDTH, head.y + 4, head.z + WIDTH);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> Targeting.isValid(owner, x))) {
                if (!swallowed.containsKey(e.getUUID())) {
                    swallowed.put(e.getUUID(), e);
                    level.sendParticles(ShadowFx.BLACK, e.getX(), e.getY() + 0.5, e.getZ(), 30, 0.5, 0.7, 0.5, 0);
                    level.sendParticles(ParticleTypes.SQUID_INK, e.getX(), e.getY() + 0.3, e.getZ(), 15, 0.4, 0.2, 0.4, 0.1);
                    level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0f, 0.6f);
                }
            }
        }

        if (age % 3 == 0) {
            for (int i = 0; i < path.size(); i += 2) {
                Vec3 q = path.get(i);
                level.sendParticles(ParticleTypes.SCULK_SOUL, q.x, q.y + 0.1, q.z, 1, 0.5, 0.1, 0.5, 0.03);
            }
        }

        // Drag everything swallowed toward the tip of the rift, half sunk into the shadow.
        Iterator<LivingEntity> it = swallowed.values().iterator();
        int idx = 0;
        while (it.hasNext()) {
            LivingEntity e = it.next();
            if (!e.isAlive() || e.isRemoved()) { it.remove(); continue; }
            double a = idx++ * 2.4;
            double spread = age >= GRAB_AT ? 1.6 : 0.6;
            Vec3 target = new Vec3(tip.x + Math.cos(a) * spread, tip.y - (age >= GRAB_AT ? -0.8 : 0.4), tip.z + Math.sin(a) * spread);
            Vec3 cur = e.position();
            Vec3 next = cur.add(target.subtract(cur).scale(0.35));
            e.teleportTo(next.x, next.y, next.z);
            e.setDeltaMovement(Vec3.ZERO);
            e.fallDistance = 0;
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 6, false, false));
            level.sendParticles(ShadowFx.BLACK, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 6, 0.35, 0.5, 0.35, 0);
        }

        if (age == LENGTH + 1) {
            level.playSound(null, tip.x, tip.y, tip.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 2.0f, 0.6f);
            ShadowFxEntity.spawn(level, ShadowFxEntity.SIGIL, tip, 8f, EXPLODE_AT - LENGTH + 30, 10, 0f);
        }

        // Tentacles erupt from the ground around the tip and seize the victims.
        if (age == GRAB_AT) {
            int n = Math.max(6, swallowed.size() * 2);
            List<LivingEntity> victims = new ArrayList<>(swallowed.values());
            for (int k = 0; k < n; k++) {
                double a = k * Math.PI * 2 / n;
                Vec3 root = new Vec3(tip.x + Math.cos(a) * 4.5, Targeting.groundY(level, tip.x + Math.cos(a) * 4.5, tip.y, tip.z + Math.sin(a) * 4.5), tip.z + Math.sin(a) * 4.5);
                Vec3 reach = victims.isEmpty() ? tip.add(0, 3.5, 0) : body(victims.get(k % victims.size()));
                TentacleEntity t = TentacleEntity.spawn(level, root, reach.subtract(root), EXPLODE_AT - GRAB_AT + 8, 4, 1.2f);
                if (t != null) holders.add(t);
            }
            level.playSound(null, tip.x, tip.y, tip.z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 1.5f, 0.5f);
            ShadowFx.crackle(level, tip, 5, 5);
        }
        if (age > GRAB_AT && age < EXPLODE_AT) {
            List<LivingEntity> victims = new ArrayList<>(swallowed.values());
            for (int k = 0; k < holders.size(); k++) {
                Vec3 reach = victims.isEmpty() ? tip.add(0, 3.5, 0) : body(victims.get(k % victims.size()));
                holders.get(k).reachTo(reach);
            }
            if (age % 3 == 0) ShadowFx.zap(level, tip.add(0, 0.2, 0), tip.add(0, 4 + age % 5, 0), 4);
            level.sendParticles(ShadowFx.GREEN, tip.x, tip.y + 0.5, tip.z, 8, 2.0, 0.2, 2.0, 0);
        }

        if (age >= EXPLODE_AT) {
            explode(level, tip);
            return true;
        }
        return false;
    }

    private static Vec3 body(LivingEntity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    private void explode(ServerLevel level, Vec3 c) {
        holders.forEach(TentacleEntity::retract);

        ShadowFxEntity.spawn(level, ShadowFxEntity.PILLAR, c, 5.0f, 60, 6, 0f);
        ShadowFxEntity.spawn(level, ShadowFxEntity.PILLAR, c, 2.0f, 50, 3, 0f);
        ShadowFxEntity.spawn(level, ShadowFxEntity.RING, c, 18f, 22, 14, 0f);
        ShadowFxEntity.spawn(level, ShadowFxEntity.RING, c, 12f, 18, 8, 0f);
        for (int k = 0; k < 12; k++) {
            double a = k * Math.PI * 2 / 12;
            ShadowFxEntity.bolt(level, c.add(0, 2 + (k % 4) * 3, 0),
                    c.add(Math.cos(a) * 9, 0.1, Math.sin(a) * 9), k % 4 == 0, 10);
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 1, c.z, 3, 1.5, 1.0, 1.5, 0);
        level.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y + 1.5, c.z, 5, 2.0, 1.0, 2.0, 0);
        level.sendParticles(ShadowFx.BLACK, c.x, c.y + 2, c.z, 250, 4.0, 3.0, 4.0, 0);
        level.sendParticles(ShadowFx.GREEN, c.x, c.y + 4, c.z, 250, 3.0, 6.0, 3.0, 0);
        level.sendParticles(ParticleTypes.SCULK_SOUL, c.x, c.y + 1, c.z, 60, 3.0, 2.0, 3.0, 0.15);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.0f, 0.5f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2.5f, 0.6f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.5f, 1.2f);

        for (LivingEntity e : swallowed.values()) {
            if (!e.isAlive()) continue;
            e.invulnerableTime = 0;
            Targeting.hurt(owner, e, SWALLOWED_DAMAGE);
            double a = owner.getRandom().nextDouble() * Math.PI * 2;
            Targeting.launch(e, new Vec3(Math.cos(a) * 0.8, 1.6, Math.sin(a) * 0.8));
        }
        AABB box = new AABB(c.x - 8, c.y - 2, c.z - 8, c.x + 8, c.y + 8, c.z + 8);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> Targeting.isValid(owner, x))) {
            if (swallowed.containsKey(e.getUUID())) continue;
            Targeting.hurt(owner, e, SPLASH_DAMAGE);
            Vec3 away = e.position().subtract(c);
            away = new Vec3(away.x, 0, away.z);
            if (away.lengthSqr() > 1.0E-4) Targeting.launch(e, away.normalize().scale(1.6).add(0, 0.8, 0));
        }
    }
}
