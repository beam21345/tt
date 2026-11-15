package com.alastor.staff.skill;

import com.alastor.staff.entity.ShadowFxEntity;
import com.alastor.staff.entity.TentacleEntity;
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
            case SHADOW_STEP -> shadowStep(level, p);
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

        // a path of smaller tentacles racing from the caster to the aim point; each one rises from the
        // real ground, so over a cliff or a drop they climb all the way up to the caster's level
        Vec3 from = p.position();
        double dist = Math.min(40, Math.sqrt(from.distanceToSqr(aim)));
        for (int i = 2; i < dist; i += 4) {
            Vec3 pt = from.add(flat.scale(i)).add(new Vec3(-flat.z, 0, flat.x).scale((i / 3) % 2 == 0 ? 1.2 : -1.2));
            double refY = from.y + (aim.y - from.y) * (i / Math.max(1.0, dist));
            Double g = Targeting.findGround(level, pt.x, refY, pt.z);
            if (g == null) continue;
            double h = Math.max(5.0 + rnd.nextDouble() * 2, refY - g + 4.0);
            SkillTaskManager.add(level, new TentacleTask(p, new Vec3(pt.x, g, pt.z), i / 2, h, 6f, 2.4));
        }

        int delay = (int) dist, made = 0;
        for (LivingEntity t : targets) {
            if (made >= 8) break;
            Double g = Targeting.findGround(level, t.getX(), t.getY(), t.getZ());
            if (g == null) continue;
            double h = Math.max(14.0 + rnd.nextDouble() * 3, t.getY() - g + 8.0);
            SkillTaskManager.add(level, new TentacleTask(p, new Vec3(t.getX(), g, t.getZ()), delay, h, 12f, 3.6, flat));
            delay += 2;
            made++;
        }
        for (int i = made; i < 10; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = 3 + rnd.nextDouble() * 7;
            double x = aim.x + Math.cos(a) * r, z = aim.z + Math.sin(a) * r;
            Double g = Targeting.findGround(level, x, aim.y, z);
            if (g == null) continue;
            double h = Math.max(12.0 + rnd.nextDouble() * 4, aim.y - g + 6.0);
            SkillTaskManager.add(level, new TentacleTask(p, new Vec3(x, g, z), delay, h, 12f, 3.6, flat));
            delay += 2;
        }
        ShadowFxEntity.spawn(level, ShadowFxEntity.SIGIL, groundAt(level, p), 6.0f, 36, 5, 0f);
        ShadowFx.crackle(level, groundAt(level, p), 4, 4);
        level.sendParticles(ShadowFx.GREEN, p.getX(), p.getY() + 0.3, p.getZ(), 30, 1.2, 0.2, 1.2, 0);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 1.2f, 0.5f);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 0.5f, 1.4f);
        com.alastor.staff.registry.ModSounds.play(level, p.position(), com.alastor.staff.registry.ModSounds.RADIO_STATIC, 1.0f, 1.3f);
        return true;
    }

    // ---- 2. Shadow Summoning: three summoning rituals, one after another.
    private static boolean shadowSummoning(ServerLevel level, Player p) {
        Vec3 c = groundAt(level, p);
        ShadowFxEntity.spawn(level, ShadowFxEntity.SIGIL, c, 9f, 70, 10, 0f);
        ShadowFx.crackle(level, c, 8, 6);
        float[] sizes = {0.75f, 1.15f, 1.7f}; // small, medium and large demon
        for (int i = 0; i < 3; i++) {
            final float size = sizes[i];
            double a = Math.toRadians(p.getYRot() + 90 + i * 120);
            double x = p.getX() + Math.cos(a) * 5.0;
            double z = p.getZ() + Math.sin(a) * 5.0;
            Vec3 at = new Vec3(x, Targeting.groundY(level, x, p.getY(), z), z);
            SkillTaskManager.add(level, new DelayedTask(i * 6, l -> SkillTaskManager.add(l, new SummonTask(p, at, size))));
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.2f, 0.6f);
        com.alastor.staff.registry.ModSounds.play(level, c, com.alastor.staff.registry.ModSounds.SHADOW_HUM, 1.5f, 1.2f);
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
        com.alastor.staff.registry.ModSounds.play(level, c, com.alastor.staff.registry.ModSounds.RADIO_TUNE, 2.0f, 1.0f);
        com.alastor.staff.registry.ModSounds.play(level, c, com.alastor.staff.registry.ModSounds.SHADOW_BOOM, 2.0f, 0.9f);
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
        com.alastor.staff.registry.ModSounds.play(level, c, com.alastor.staff.registry.ModSounds.RADIO_STATIC, 1.6f, 0.8f + wave * 0.15f);

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
            Double g = Targeting.findGround(level, x, c.y, z);
            if (g != null) strikes.add(new Vec3(x, g, z));
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
        com.alastor.staff.registry.ModSounds.play(level, c, com.alastor.staff.registry.ModSounds.SHADOW_HUM, 2.0f, 0.8f);
        com.alastor.staff.registry.ModSounds.play(level, c, com.alastor.staff.registry.ModSounds.SHADOW_BOOM, 1.5f, 1.1f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.0f, 0.5f);
        level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y + 0.3, c.z, 200, 10, 0.1, 10, 0.08);
        SkillTaskManager.add(level, new DomainTask(p, c));
        return true;
    }

    // ---- 7. Shadow Step: sink into your shadow and burst out of the ground up to 18 blocks ahead.
    private static boolean shadowStep(ServerLevel level, Player p) {
        Vec3 look = p.getLookAngle();
        Vec3 eye = p.getEyePosition();
        net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(look.scale(18)),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
        Vec3 end = hit.getLocation().subtract(look.scale(0.7));

        Vec3 dest = null;
        for (double back = 0; back <= 17; back += 0.75) {
            Vec3 feet = end.subtract(look.scale(back)).subtract(0, p.getEyeHeight(), 0);
            Double g = Targeting.findGround(level, feet.x, feet.y, feet.z, 2, 4);
            Vec3 cand = g != null ? new Vec3(feet.x, g, feet.z) : feet;
            if (level.noCollision(p, p.getBoundingBox().move(cand.subtract(p.position())))) { dest = cand; break; }
        }
        if (dest == null || dest.distanceToSqr(p.position()) < 4) return false;

        Vec3 from = p.position();
        Vec3 fromGround = groundAt(level, p);
        // departure: the caster sinks into a pool of shadow
        ShadowFxEntity.spawn(level, ShadowFxEntity.SIGIL, fromGround, 3f, 26, 3, 0f);
        level.sendParticles(ParticleTypes.SQUID_INK, from.x, from.y + 0.6, from.z, 40, 0.4, 0.8, 0.4, 0.08);
        level.sendParticles(ShadowFx.BLACK, from.x, from.y + 1, from.z, 40, 0.4, 0.9, 0.4, 0);
        // trail of shadow and lightning between the two points
        Vec3 d = dest.subtract(from);
        int n = (int) Math.max(2, d.length());
        for (int i = 0; i <= n; i++) {
            Vec3 q = from.add(d.scale((double) i / n)).add(0, 1, 0);
            level.sendParticles(ShadowFx.BLACK, q.x, q.y, q.z, 4, 0.25, 0.3, 0.25, 0);
            if (i % 3 == 0) level.sendParticles(ShadowFx.GREEN_SMALL, q.x, q.y, q.z, 2, 0.2, 0.2, 0.2, 0);
        }
        ShadowFx.zap(level, from.add(0, 1, 0), dest.add(0, 1, 0), 6);

        p.teleportTo(dest.x, dest.y, dest.z);
        p.fallDistance = 0;
        p.setDeltaMovement(Vec3.ZERO);
        p.hurtMarked = true;

        // arrival: erupt out of the ground, knocking enemies back
        ShadowFxEntity.spawn(level, ShadowFxEntity.SIGIL, dest, 4f, 30, 3, 0f);
        ShadowFxEntity.spawn(level, ShadowFxEntity.PILLAR, dest, 1.0f, 18, 3, 0f);
        ShadowFxEntity.spawn(level, ShadowFxEntity.RING, dest, 6f, 14, 8, 0f);
        for (int k = 0; k < 4; k++) {
            double a = k * Math.PI / 2 + 0.6;
            Vec3 root = dest.add(Math.cos(a) * 1.6, 0, Math.sin(a) * 1.6);
            TentacleEntity t = TentacleEntity.spawn(level, root, new Vec3(Math.cos(a) * 1.2, 4.5, Math.sin(a) * 1.2), 18, 3, 0.9f);
        }
        level.sendParticles(ParticleTypes.SQUID_INK, dest.x, dest.y + 0.5, dest.z, 40, 0.8, 0.6, 0.8, 0.1);
        ShadowFx.crackle(level, dest, 3, 3);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(3.5), x -> Targeting.isValid(p, x))) {
            Targeting.hurt(p, e, 6f);
            Vec3 away = e.position().subtract(dest);
            away = new Vec3(away.x, 0, away.z);
            if (away.lengthSqr() > 1.0E-4) Targeting.launch(e, away.normalize().scale(1.3).add(0, 0.6, 0));
        }
        level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 0.5f);
        com.alastor.staff.registry.ModSounds.play(level, dest, com.alastor.staff.registry.ModSounds.TENTACLE_BURST, 1.2f, 0.9f);
        com.alastor.staff.registry.ModSounds.play(level, dest, com.alastor.staff.registry.ModSounds.RADIO_STATIC, 0.6f, 1.4f);
        return true;
    }

    private static Vec3 groundAt(ServerLevel level, Player p) {
        return new Vec3(p.getX(), Targeting.groundY(level, p.getX(), p.getY(), p.getZ()), p.getZ());
    }

    private Skills() {}
}
