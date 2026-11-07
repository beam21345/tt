package com.alastor.staff.skill;

import com.alastor.staff.entity.ShadowDemonEntity;
import com.alastor.staff.entity.ShadowFxEntity;
import com.alastor.staff.fx.ShadowFx;
import com.alastor.staff.registry.ModEntities;
import com.alastor.staff.skill.task.DomainTask;
import com.alastor.staff.skill.task.FractureTask;
import com.alastor.staff.skill.task.ShockwaveTask;
import com.alastor.staff.skill.task.SkillTaskManager;
import com.alastor.staff.skill.task.TentacleTask;
import com.alastor.staff.util.Targeting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/** Casting logic for skills 1–5 (skill 6 is the toggle aura in {@link RadioDemonAura}). */
public final class Skills {

    public static boolean cast(SkillType skill, ServerLevel level, Player p) {
        return switch (skill) {
            case SHADOW_TENTACLES -> shadowTentacles(level, p);
            case SHADOW_SUMMONING -> shadowSummoning(level, p);
            case RADIO_SOUND -> radioSound(level, p);
            case SHADOW_FRACTURE -> shadowFracture(level, p);
            case SHADOW_DOMAIN -> shadowDomain(level, p);
            case RADIO_DEMON -> false;
        };
    }

    // ---- 1. Shadow Tentacles: tentacles (~10 blocks tall) burst from the ground at enemies ahead.
    private static boolean shadowTentacles(ServerLevel level, Player p) {
        RandomSource rnd = p.getRandom();
        Vec3 look = p.getLookAngle();
        HitResult hr = p.pick(20.0, 1.0f, false);
        Vec3 aim = hr.getLocation();

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(16),
                e -> Targeting.isValid(p, e) && p.hasLineOfSight(e)
                        && e.position().subtract(p.position()).normalize().dot(look) > 0.4);
        targets.sort(Comparator.comparingDouble((LivingEntity e) -> e.distanceToSqr(p)));

        int delay = 0, made = 0;
        for (LivingEntity t : targets) {
            if (made >= 5) break;
            Vec3 base = new Vec3(t.getX(), Targeting.groundY(level, t.getX(), t.getY(), t.getZ()), t.getZ());
            SkillTaskManager.add(level, new TentacleTask(p, base, 4 + delay, 10.0, 10f, 1.3));
            delay += 2;
            made++;
        }
        for (int i = made; i < 7; i++) {
            double x = aim.x + (rnd.nextDouble() * 2 - 1) * 3.5;
            double z = aim.z + (rnd.nextDouble() * 2 - 1) * 3.5;
            Vec3 base = new Vec3(x, Targeting.groundY(level, x, aim.y, z), z);
            SkillTaskManager.add(level, new TentacleTask(p, base, 4 + delay, 8.0 + rnd.nextDouble() * 2.0, 10f, 1.3));
            delay += 2;
        }
        ShadowFxEntity.spawn(level, ShadowFxEntity.SIGIL, groundAt(level, p), 2.0f, 30, 5, 0f);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 1.0f, 0.6f);
        return true;
    }

    // ---- 2. Shadow Summoning: 3 shadow demons for 1 minute that hunt monsters only.
    private static boolean shadowSummoning(ServerLevel level, Player p) {
        for (int i = 0; i < 3; i++) {
            double a = Math.toRadians(p.getYRot() + 90 + i * 120);
            double x = p.getX() + Math.cos(a) * 2.2;
            double z = p.getZ() + Math.sin(a) * 2.2;
            double y = Targeting.groundY(level, x, p.getY(), z);
            ShadowDemonEntity demon = ModEntities.SHADOW_DEMON.get().create(level);
            if (demon == null) continue;
            demon.moveTo(x, y, z, p.getYRot(), 0);
            demon.setOwner(p);
            demon.setLifeTicks(ShadowDemonEntity.DEFAULT_LIFE);
            level.addFreshEntity(demon);

            Vec3 at = new Vec3(x, y, z);
            ShadowFxEntity.spawn(level, ShadowFxEntity.SIGIL, at, 1.6f, 40, 6, 0f);
            level.sendParticles(ParticleTypes.SQUID_INK, x, y + 0.5, z, 25, 0.4, 0.8, 0.4, 0.05);
            level.sendParticles(ShadowFx.BLACK, x, y + 1, z, 40, 0.4, 1.0, 0.4, 0);
            level.sendParticles(ShadowFx.GREEN, x, y + 1.6, z, 10, 0.2, 0.2, 0.2, 0);
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 1.2f, 0.6f);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 0.6f, 1.4f);
        return true;
    }

    // ---- 3. Radio Sound: plant the staff, a green/black wave + tentacles over 20x20 blocks.
    private static boolean radioSound(ServerLevel level, Player p) {
        RandomSource rnd = p.getRandom();
        Vec3 c = new Vec3(p.getX(), Targeting.groundY(level, p.getX(), p.getY(), p.getZ()), p.getZ());

        ShadowFxEntity.spawn(level, ShadowFxEntity.SIGIL, c, 3.0f, 34, 4, 0f);
        ShadowFxEntity.spawn(level, ShadowFxEntity.RING, c, 10.5f, 20, 14, 0f);
        level.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2.0f, 0.7f);
        SkillTaskManager.add(level, new ShockwaveTask(p, c));

        AABB box = new AABB(c.x - 10, c.y - 2, c.z - 10, c.x + 10, c.y + 5, c.z + 10);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box, e -> Targeting.isValid(p, e));
        int made = 0;
        for (LivingEntity t : targets) {
            if (made >= 6) break;
            Vec3 base = new Vec3(t.getX(), Targeting.groundY(level, t.getX(), t.getY(), t.getZ()), t.getZ());
            SkillTaskManager.add(level, new TentacleTask(p, base, 6 + made * 2, 7.0, 8f, 1.2));
            made++;
        }
        for (int i = made; i < 10; i++) {
            double x = c.x + (rnd.nextDouble() * 2 - 1) * 9;
            double z = c.z + (rnd.nextDouble() * 2 - 1) * 9;
            Vec3 base = new Vec3(x, Targeting.groundY(level, x, c.y, z), z);
            SkillTaskManager.add(level, new TentacleTask(p, base, 6 + i * 2, 5.0 + rnd.nextDouble() * 3, 8f, 1.2));
        }
        return true;
    }

    // ---- 4. Shadow Fracture: a 15-block crack that swallows and drags enemies, then erupts.
    private static boolean shadowFracture(ServerLevel level, Player p) {
        Vec3 dir = Targeting.horizontalLook(p);
        double gy = Targeting.groundY(level, p.getX(), p.getY(), p.getZ());
        Vec3 origin = new Vec3(p.getX(), gy, p.getZ()).add(dir);
        ShadowFxEntity.spawn(level, ShadowFxEntity.SIGIL, new Vec3(p.getX(), gy, p.getZ()), 1.8f, 30, 4, 0f);
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        ShadowFxEntity.spawn(level, ShadowFxEntity.RIFT, origin, 15f, 34, 15, yaw);
        level.sendParticles(ParticleTypes.SQUID_INK, origin.x, gy + 0.2, origin.z, 20, 0.4, 0.1, 0.4, 0.05);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_DIG, SoundSource.PLAYERS, 1.5f, 0.7f);
        SkillTaskManager.add(level, new FractureTask(p, origin, dir));
        return true;
    }

    // ---- 5. Shadow Domain: 15x15 shadow field that pins everything inside.
    private static boolean shadowDomain(ServerLevel level, Player p) {
        Vec3 c = new Vec3(p.getX(), Targeting.groundY(level, p.getX(), p.getY(), p.getZ()), p.getZ());
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 1.5f, 0.8f);
        level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y + 0.3, c.z, 80, 5, 0.1, 5, 0.05);
        ShadowFxEntity.spawn(level, ShadowFxEntity.DOMAIN, c, 7.5f, 200, 10, 0f);
        SkillTaskManager.add(level, new DomainTask(p, c));
        return true;
    }

    private static Vec3 groundAt(ServerLevel level, Player p) {
        return new Vec3(p.getX(), Targeting.groundY(level, p.getX(), p.getY(), p.getZ()), p.getZ());
    }

    private Skills() {}
}
