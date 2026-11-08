package com.alastor.staff.skill;

import com.alastor.staff.entity.ShadowFxEntity;
import com.alastor.staff.fx.ShadowFx;
import com.alastor.staff.skill.task.DelayedTask;
import com.alastor.staff.skill.task.DomainTask;
import com.alastor.staff.skill.task.FractureTask;
import com.alastor.staff.skill.task.ShockwaveTask;
import com.alastor.staff.skill.task.SkillTaskManager;
import com.alastor.staff.skill.task.SummonTask;
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

/** Casting logic and choreography for skills 1–5 (skill 6 is the toggle aura in {@link RadioDemonAura}). */
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

    // ---- 1. Shadow Tentacles: a wave of huge tentacles erupts toward the target, then they slam down.
    private static boolean shadowTentacles(ServerLevel level, Player p) {
        RandomSource rnd = p.getRandom();
        Vec3 look = p.getLookAngle();
        Vec3 flat = Targeting.horizontalLook(p);
        HitResult hr = p.pick(24.0, 1.0f, false);
        Vec3 aim = hr.getLocation();

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(18),
                e -> Targeting.isValid(p, e) && p.hasLineOfSight(e)
                        && e.position().subtract(p.position()).normalize().dot(look) > 0.4);
        targets.sort(Comparator.comparingDouble((LivingEntity e) -> e.distanceToSqr(p)));

        // a path of smaller tentacles racing from the caster to the aim point
        Vec3 from = p.position();
        double dist = Math.min(20, Math.sqrt(from.distanceToSqr(aim)));
        for (int i = 2; i < dist; i += 3) {
            Vec3 pt = from.add(flat.scale(i)).add(new Vec3(-flat.z, 0, flat.x).scale((i / 3) % 2 == 0 ? 1.2 : -1.2));
            Vec3 base = new Vec3(pt.x, Targeting.groundY(level, pt.x, p.getY(), pt.z), pt.z);
            SkillTaskManager.add(level, new TentacleTask(p, base, i, 5.0 + rnd.nextDouble() * 2, 6f, 1.2));
        }

        int delay = (int) dist, made = 0;
        for (LivingEntity t : targets) {
            if (made >= 5) break;
            Vec3 base = new Vec3(t.getX(), Targeting.groundY(level, t.getX(), t.getY(), t.getZ()), t.getZ());
            SkillTaskManager.add(level, new TentacleTask(p, base, delay, 14.0 + rnd.nextDouble() * 3, 12f, 1.8, flat));
            delay += 2;
            made++;
        }
        for (int i = made; i < 6; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = 1.5 + rnd.nextDouble() * 3.5;
            double x = aim.x + Math.cos(a) * r, z = aim.z + Math.sin(a) * r;
            Vec3 base = new Vec3(x, Targeting.groundY(level, x, aim.y, z), z);
            SkillTaskManager.add(level, new TentacleTask(p, base, delay, 12.0 + rnd.nextDouble() * 4, 12f, 1.8, flat));
            delay += 2;
        }
        ShadowFxEntity.spawn(level, ShadowFxEntity.SIGIL, groundAt(level, p), 3.0f, 36, 5, 0f);
        level.sendParticles(ShadowFx.GREEN, p.getX(), p.getY() + 0.3, p.getZ(), 30, 1.2, 0.2, 1.2, 0);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 1.2f, 0.5f);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 0.5f, 1.4f);
        return true;
    }

    // ---- 2. Shadow Summoning: three summoning rituals, one after another.
    private static boolean shadowSummoning(ServerLevel level, Player p) {
        Vec3 c = groundAt(level, p);
        ShadowFxEntity.spawn(level, ShadowFxEntity.SIGIL, c, 4.5f, 70, 10, 0f);
        for (int i = 0; i < 3; i++) {
            double a = Math.toRadians(p.getYRot() + 90 + i * 120);
            double x = p.getX() + Math.cos(a) * 3.0;
            double z = p.getZ() + Math.sin(a) * 3.0;
            Vec3 at = new Vec3(x, Targeting.groundY(level, x, p.getY(), z), z);
            SkillTaskManager.add(level, new DelayedTask(i * 6, l -> SkillTaskManager.add(l, new SummonTask(p, at))));
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.2f, 0.6f);
        return true;
    }

    // ---- 3. Radio Sound: staff planted -> pillar of light, three waves, rings of tentacles going outward.
    private static boolean radioSound(ServerLevel level, Player p) {
        RandomSource rnd = p.getRandom();
        Vec3 c = groundAt(level, p);

        ShadowFxEntity.spawn(level, ShadowFxEntity.SIGIL, c, 5.0f, 60, 5, 0f);
        ShadowFxEntity.spawn(level, ShadowFxEntity.PILLAR, c, 1.4f, 36, 6, 0f);
        level.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2.0f, 0.6f);
        SkillTaskManager.add(level, new ShockwaveTask(p, c));
        for (int w = 0; w < 3; w++) {
            final int wave = w;
            SkillTaskManager.add(level, new DelayedTask(w * 7, l -> {
                ShadowFxEntity.spawn(l, ShadowFxEntity.RING, c, 10.5f, 22, 14, 0f);
                l.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
                if (wave > 0) l.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2f, 0.8f + wave * 0.15f);
            }));
        }

        // concentric rings of tentacles erupting outward
        double[] radii = {3, 6, 9};
        int[] counts = {6, 9, 12};
        for (int ring = 0; ring < radii.length; ring++) {
            for (int k = 0; k < counts[ring]; k++) {
                double a = k * Math.PI * 2 / counts[ring] + ring * 0.4;
                double x = c.x + Math.cos(a) * radii[ring], z = c.z + Math.sin(a) * radii[ring];
                Vec3 base = new Vec3(x, Targeting.groundY(level, x, c.y, z), z);
                SkillTaskManager.add(level, new TentacleTask(p, base, 3 + ring * 5 + rnd.nextInt(2),
                        9.0 + ring + rnd.nextDouble() * 2, 9f, 1.5));
            }
        }
        AABB box = new AABB(c.x - 10, c.y - 2, c.z - 10, c.x + 10, c.y + 5, c.z + 10);
        int made = 0;
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, box, e -> Targeting.isValid(p, e))) {
            if (made++ >= 6) break;
            Vec3 base = new Vec3(t.getX(), Targeting.groundY(level, t.getX(), t.getY(), t.getZ()), t.getZ());
            SkillTaskManager.add(level, new TentacleTask(p, base, 8 + made * 2, 11.0, 10f, 1.6));
        }
        return true;
    }

    // ---- 4. Shadow Fracture: a 15-block glowing rift that swallows, drags and erupts.
    private static boolean shadowFracture(ServerLevel level, Player p) {
        Vec3 dir = Targeting.horizontalLook(p);
        double gy = Targeting.groundY(level, p.getX(), p.getY(), p.getZ());
        Vec3 origin = new Vec3(p.getX(), gy, p.getZ()).add(dir);
        ShadowFxEntity.spawn(level, ShadowFxEntity.SIGIL, new Vec3(p.getX(), gy, p.getZ()), 2.8f, 40, 4, 0f);
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        ShadowFxEntity.spawn(level, ShadowFxEntity.RIFT, origin, 15f, 40, 15, yaw);
        level.sendParticles(ParticleTypes.SQUID_INK, origin.x, gy + 0.2, origin.z, 30, 0.6, 0.1, 0.6, 0.1);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_DIG, SoundSource.PLAYERS, 1.5f, 0.6f);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.0f, 1.2f);
        SkillTaskManager.add(level, new FractureTask(p, origin, dir));
        return true;
    }

    // ---- 5. Shadow Domain: circular shadow field, 15 blocks across, that pins everything inside.
    private static boolean shadowDomain(ServerLevel level, Player p) {
        Vec3 c = groundAt(level, p);
        ShadowFxEntity.spawn(level, ShadowFxEntity.DOMAIN, c, 7.5f, 200, 14, 0f);
        ShadowFxEntity.spawn(level, ShadowFxEntity.PILLAR, c, 1.6f, 30, 6, 0f);
        ShadowFxEntity.spawn(level, ShadowFxEntity.RING, c, 8f, 16, 12, 0f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 1.5f, 0.8f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.0f, 0.5f);
        level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y + 0.3, c.z, 120, 5, 0.1, 5, 0.08);
        SkillTaskManager.add(level, new DomainTask(p, c));
        return true;
    }

    private static Vec3 groundAt(ServerLevel level, Player p) {
        return new Vec3(p.getX(), Targeting.groundY(level, p.getX(), p.getY(), p.getZ()), p.getZ());
    }

    private Skills() {}
}
