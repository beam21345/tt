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
        HitResult hr = p.pick(48.0, 1.0f, false);
        Vec3 aim = hr.getLocation();

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(36),
                e -> Targeting.isValid(p, e) && p.hasLineOfSight(e)
                        && e.position().subtract(p.position()).normalize().dot(look) > 0.4);
        targets.sort(Comparator.comparingDouble((LivingEntity e) -> e.distanceToSqr(p)));

        // a path of smaller tentacles racing from the caster to the aim point
        Vec3 from = p.position();
        double dist = Math.min(40, Math.sqrt(from.distanceToSqr(aim)));
        for (int i = 2; i < dist; i += 4) {
            Vec3 pt = from.add(flat.scale(i)).add(new Vec3(-flat.z, 0, flat.x).scale((i / 3) % 2 == 0 ? 1.2 : -1.2));
            Vec3 base = new Vec3(pt.x, Targeting.groundY(level, pt.x, p.getY(), pt.z), pt.z);
            SkillTaskManager.add(level, new TentacleTask(p, base, i / 2, 5.0 + rnd.nextDouble() * 2, 6f, 2.4));
        }

        int delay = (int) dist, made = 0;
        for (LivingEntity t : targets) {
            if (made >= 8) break;
            Vec3 base = new Vec3(t.getX(), Targeting.groundY(level, t.getX(), t.getY(), t.getZ()), t.getZ());
            SkillTaskManager.add(level, new TentacleTask(p, base, delay, 14.0 + rnd.nextDouble() * 3, 12f, 3.6, flat));
            delay += 2;
            made++;
        }
        for (int i = made; i < 10; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = 3 + rnd.nextDouble() * 7;
            double x = aim.x + Math.cos(a) * r, z = aim.z + Math.sin(a) * r;
            Vec3 base = new Vec3(x, Targeting.groundY(level, x, aim.y, z), z);
            SkillTaskManager.add(level, new TentacleTask(p, base, delay, 12.0 + rnd.nextDouble() * 4, 12f, 3.6, flat));
            delay += 2;
        }
        ShadowFxEntity.spawn(level, ShadowFxEntity.SIGIL, groundAt(level, p), 6.0f, 36, 5, 0f);
        ShadowFx.crackle(level, groundAt(level, p), 4, 4);
        level.sendParticles(ShadowFx.GREEN, p.getX(), p.getY() + 0.3, p.getZ(), 30, 1.2, 0.2, 1.2, 0);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 1.2f, 0.5f);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 0.5f, 1.4f);
        return true;
    }

    // ---- 2. Shadow Summoning: three summoning rituals, one after another.
    private static boolean shadowSummoning(ServerLevel level, Player p) {
        Vec3 c = groundAt(level, p);
        ShadowFxEntity.spawn(level, ShadowFxEntity.SIGIL, c, 9f, 70, 10, 0f);
        ShadowFx.crackle(level, c, 8, 6);
        for (int i = 0; i < 3; i++) {
            double a = Math.toRadians(p.getYRot() + 90 + i * 120);
            double x = p.getX() + Math.cos(a) * 5.0;
            double z = p.getZ() + Math.sin(a) * 5.0;
            Vec3 at = new Vec3(x, Targeting.groundY(level, x, p.getY(), z), z);
            SkillTaskManager.add(level, new DelayedTask(i * 6, l -> SkillTaskManager.add(l, new SummonTask(p, at))));
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.2f, 0.6f);
        return true;
    }

    // ---- 3. Radio Sound: staff planted -> pillar of light, three waves of green lightning lashing out in every
    //         direction (40x40), and green light erupting from the ground wherever it strikes.
    private static boolean radioSound(ServerLevel level, Player p) {
        Vec3 c = groundAt(level, p);
        Vec3 staffTip = c.add(0, 2.4, 0);

        ShadowFxEntity.spawn(level, ShadowFxEntity.SIGIL, c, 10.0f, 70, 6, 0f);
        ShadowFxEntity.spawn(level, ShadowFxEntity.PILLAR, c, 2.2f, 50, 6, 0f);
        level.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2.0f, 0.6f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.2f, 1.4f);
        SkillTaskManager.add(level, new ShockwaveTask(p, c));

        for (int w = 0; w < 3; w++) {
            final int wave = w;
            SkillTaskManager.add(level, new DelayedTask(w * 8, l -> radioWave(l, p, c, staffTip, wave)));
        }
        return true;
    }

    private static void radioWave(ServerLevel level, Player p, Vec3 c, Vec3 staffTip, int wave) {
        RandomSource rnd = level.getRandom();
        ShadowFxEntity.spawn(level, ShadowFxEntity.RING, c, 21f, 24, 16, 0f);
        level.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.5f, 0.8f + wave * 0.2f);

        // strike points: enemies first, then fill every direction
        java.util.List<Vec3> strikes = new java.util.ArrayList<>();
        AABB box = new AABB(c.x - 20, c.y - 3, c.z - 20, c.x + 20, c.y + 6, c.z + 20);
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, box, e -> Targeting.isValid(p, e))) {
            if (strikes.size() >= 8) break;
            strikes.add(new Vec3(t.getX(), Targeting.groundY(level, t.getX(), t.getY(), t.getZ()), t.getZ()));
        }
        int n = 16;
        for (int k = 0; k < n; k++) {
            double a = k * Math.PI * 2 / n + wave * 0.4 + rnd.nextDouble() * 0.3;
            double r = 5 + rnd.nextDouble() * 15;
            double x = c.x + Math.cos(a) * r, z = c.z + Math.sin(a) * r;
            strikes.add(new Vec3(x, Targeting.groundY(level, x, c.y, z), z));
        }
        for (Vec3 s : strikes) {
            ShadowFxEntity.bolt(level, staffTip, s.add(0, 0.2, 0), rnd.nextFloat() < 0.18f, 7 + rnd.nextInt(4));
            // green light bursting up from the ground where the bolt lands
            ShadowFxEntity.spawn(level, ShadowFxEntity.PILLAR, s, 0.7f + rnd.nextFloat() * 0.4f, 18, 4, 0f);
            ShadowFxEntity.spawn(level, ShadowFxEntity.SIGIL, s, 1.6f, 18, 3, 0f);
            level.sendParticles(ShadowFx.GREEN, s.x, s.y + 0.5, s.z, 12, 0.5, 1.0, 0.5, 0);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(s.x - 2.5, s.y - 1, s.z - 2.5, s.x + 2.5, s.y + 4, s.z + 2.5), x -> Targeting.isValid(p, x))) {
                e.invulnerableTime = 0;
                Targeting.hurt(p, e, 6f);
                Targeting.launch(e, e.getDeltaMovement().add(0, 0.8, 0));
            }
        }
    }

    // ---- 4. Shadow Fracture: a 15-block glowing rift that swallows, drags and erupts.
    private static boolean shadowFracture(ServerLevel level, Player p) {
        Vec3 dir = Targeting.horizontalLook(p);
        double gy = Targeting.groundY(level, p.getX(), p.getY(), p.getZ());
        Vec3 origin = new Vec3(p.getX(), gy, p.getZ()).add(dir);
        ShadowFxEntity.spawn(level, ShadowFxEntity.SIGIL, new Vec3(p.getX(), gy, p.getZ()), 5.6f, 40, 4, 0f);
        ShadowFx.crackle(level, new Vec3(p.getX(), gy, p.getZ()), 4, 4);
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        ShadowFxEntity.spawn(level, ShadowFxEntity.RIFT, origin, (float) FractureTask.LENGTH, FractureTask.LENGTH + 36, FractureTask.LENGTH, yaw);
        level.sendParticles(ParticleTypes.SQUID_INK, origin.x, gy + 0.2, origin.z, 30, 0.6, 0.1, 0.6, 0.1);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_DIG, SoundSource.PLAYERS, 1.5f, 0.6f);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.0f, 1.2f);
        SkillTaskManager.add(level, new FractureTask(p, origin, dir));
        return true;
    }

    // ---- 5. Shadow Domain: circular shadow field, 15 blocks across, that pins everything inside.
    private static boolean shadowDomain(ServerLevel level, Player p) {
        Vec3 c = groundAt(level, p);
        ShadowFxEntity.spawn(level, ShadowFxEntity.DOMAIN, c, 15f, 200, 16, 0f);
        ShadowFxEntity.spawn(level, ShadowFxEntity.PILLAR, c, 2.4f, 36, 6, 0f);
        ShadowFxEntity.spawn(level, ShadowFxEntity.RING, c, 16f, 18, 14, 0f);
        ShadowFx.crackle(level, c, 12, 8);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 1.5f, 0.8f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.0f, 0.5f);
        level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y + 0.3, c.z, 200, 10, 0.1, 10, 0.08);
        SkillTaskManager.add(level, new DomainTask(p, c));
        return true;
    }

    private static Vec3 groundAt(ServerLevel level, Player p) {
        return new Vec3(p.getX(), Targeting.groundY(level, p.getX(), p.getY(), p.getZ()), p.getZ());
    }

    private Skills() {}
}
