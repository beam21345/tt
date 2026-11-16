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

    // ---- 1. Shadow Tentacles: darkness streams from the caster toward the target, tentacles burst out of it
    //         along the way, then the finale: one giant sigil, giant tentacles seize, lift and crush everything.
    private static boolean shadowTentacles(ServerLevel level, Player p) {
        RandomSource rnd = p.getRandom();
        Vec3 flat = Targeting.horizontalLook(p);
        HitResult hr = p.pick(48.0, 1.0f, false);
        Vec3 aim = hr.getLocation();

        // aim at the nearest enemy in front if there is one, otherwise at the looked-at spot
        Vec3 look = p.getLookAngle();
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(40),
                e -> Targeting.isValid(p, e) && p.hasLineOfSight(e)
                        && e.position().subtract(p.position()).normalize().dot(look) > 0.75);
        targets.sort(Comparator.comparingDouble((LivingEntity e) -> e.distanceToSqr(p)));
        if (!targets.isEmpty() && hr.getType() != HitResult.Type.BLOCK) aim = targets.get(0).position();

        Double cg = Targeting.findGround(level, aim.x, aim.y, aim.z);
        if (cg == null) {
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.alastorstaff.no_ground"), true);
            return false;
        }
        Vec3 center = new Vec3(aim.x, cg, aim.z);
        Vec3 from = groundAt(level, p);

        ShadowFx.spread(level, from, 3.5f, 60, 4);
        int arrive = ShadowFx.stream(level, from, center, 50, 2.5);

        // tentacles lash up out of the stream as it flows (rooted on real ground, reaching up to our level)
        double dist = Math.sqrt(from.distanceToSqr(center));
        for (int i = 3; i < dist - 4; i += 4) {
            Vec3 pt = from.add(center.subtract(from).scale(i / dist)).add(new Vec3(-flat.z, 0, flat.x).scale((i / 4) % 2 == 0 ? 1.5 : -1.5));
            double refY = from.y + (center.y - from.y) * (i / dist);
            Double g = Targeting.findGround(level, pt.x, refY, pt.z);
            if (g == null) continue;
            double h = Math.max(6.0 + rnd.nextDouble() * 2, p.getY() - g + 3.0);
            SkillTaskManager.add(level, new TentacleTask(p, new Vec3(pt.x, g, pt.z), (int) (i / 2.5) + 2, h, 7f, 2.0));
        }
        SkillTaskManager.add(level, new DelayedTask(arrive + 2, l -> SkillTaskManager.add(l, new FinaleTask(p, center))));

        level.sendParticles(ParticleTypes.SQUID_INK, p.getX(), p.getY() + 0.3, p.getZ(), 40, 1.2, 0.1, 1.2, 0.05);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 1.2f, 0.5f);
        com.alastor.staff.registry.ModSounds.play(level, p.position(), com.alastor.staff.registry.ModSounds.RADIO_STATIC, 1.0f, 1.3f);
        return true;
    }

    // ---- 2. Shadow Summoning: three summoning rituals, one after another.
    private static boolean shadowSummoning(ServerLevel level, Player p) {
        Vec3 c = groundAt(level, p);
        ShadowFx.spread(level, c, 9f, 70, 10);
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

        ShadowFx.spread(level, c, 10.0f, 70, 6);
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
            ShadowFx.spread(level, s, 1.6f, 18, 3);
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
        ShadowFx.spread(level, new Vec3(p.getX(), gy, p.getZ()), 5.6f, 40, 4);
        ShadowFx.crackle(level, new Vec3(p.getX(), gy, p.getZ()), 4, 4);
        level.sendParticles(ParticleTypes.SQUID_INK, origin.x, gy + 0.2, origin.z, 30, 0.6, 0.1, 0.6, 0.1);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_DIG, SoundSource.PLAYERS, 1.5f, 0.6f);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.0f, 1.2f);
        SkillTaskManager.add(level, new FractureTask(p, origin, dir));
        return true;
    }

    // ---- 5. Shadow Domain: darkness pours out over a 75-block circle; weapon-tentacles rise from it.
    private static boolean shadowDomain(ServerLevel level, Player p) {
        Vec3 c = groundAt(level, p);
        ShadowFx.spread(level, c, 6f, 240, 6);
        ShadowFxEntity.spawn(level, ShadowFxEntity.RING, c, (float) com.alastor.staff.skill.task.ArmoryTask.RADIUS, 34, 30, 0f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 2.0f, 0.7f);
        com.alastor.staff.registry.ModSounds.play(level, c, com.alastor.staff.registry.ModSounds.SHADOW_BOOM, 2.0f, 0.8f);
        level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y + 0.3, c.z, 120, 3, 0.1, 3, 0.15);
        SkillTaskManager.add(level, new com.alastor.staff.skill.task.ArmoryTask(p, c));
        return true;
    }

    // ---- 7. Shadow Step: melt into the ground and race as a streak of darkness to any ground in sight
    //         (up to 200 blocks). There must be ground to land on – no stepping into thin air.
    private static boolean shadowStep(ServerLevel level, Player p) {
        Vec3 look = p.getLookAngle();
        Vec3 eye = p.getEyePosition();
        net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(look.scale(200)),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
        if (hit.getType() != HitResult.Type.BLOCK) {
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.alastorstaff.no_ground"), true);
            return false;
        }
        Vec3 dest = null;
        Vec3 hitPos = hit.getLocation();
        for (double back = 0.4; back <= 8 && dest == null; back += 0.8) {
            Vec3 q = hitPos.subtract(look.scale(back));
            Double g = Targeting.findGround(level, q.x, q.y, q.z, 2, 6);
            if (g == null) continue;
            Vec3 cand = new Vec3(q.x, g, q.z);
            if (level.noCollision(p, p.getBoundingBox().move(cand.subtract(p.position())))) dest = cand;
        }
        if (dest == null || dest.distanceToSqr(p.position()) < 4) {
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.alastorstaff.no_ground"), true);
            return false;
        }
        SkillTaskManager.add(level, new com.alastor.staff.skill.task.ShadowStepTask(p, dest));
        return true;
    }

    private static Vec3 groundAt(ServerLevel level, Player p) {
        return new Vec3(p.getX(), Targeting.groundY(level, p.getX(), p.getY(), p.getZ()), p.getZ());
    }

    private Skills() {}
}
