package com.alastor.staff.skill.task;

import com.alastor.staff.entity.ShadowFxEntity;
import com.alastor.staff.entity.TentacleEntity;
import com.alastor.staff.fx.ShadowFx;
import com.alastor.staff.registry.ModSounds;
import com.alastor.staff.util.Targeting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Shadow Step: the caster melts into the ground and races along it as a streak of darkness
 * (about 8 blocks per tick) to the destination, then erupts out of the shadow there.
 */
public class ShadowStepTask implements SkillTask {
    /** Players currently travelling as a shadow (they can't be hurt). */
    public static final Set<UUID> TRAVELLING = new HashSet<>();
    private static final double SPEED = 8.0;

    private final Player p;
    private final Vec3 start;
    private final Vec3 dest;
    private final int steps;
    private Vec3 prev;
    private int age;

    public ShadowStepTask(Player p, Vec3 dest) {
        this.p = p;
        this.start = p.position();
        this.dest = dest;
        this.steps = (int) Math.max(3, Math.ceil(start.distanceTo(dest) / SPEED));
        this.prev = start;
    }

    @Override
    public boolean tick(ServerLevel level) {
        if (p.isRemoved() || !p.isAlive() || p.level() != level) {
            TRAVELLING.remove(p.getUUID());
            return true;
        }
        age++;
        if (age == 1) {
            TRAVELLING.add(p.getUUID());
            p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, steps + 2, 0, false, false));
            ShadowFx.spread(level, start, 3.5f, 40, 3);
            level.sendParticles(ParticleTypes.SQUID_INK, start.x, start.y + 0.6, start.z, 50, 0.4, 0.8, 0.4, 0.08);
            level.sendParticles(ShadowFx.BLACK, start.x, start.y + 1, start.z, 50, 0.4, 0.9, 0.4, 0);
            ModSounds.play(level, start, ModSounds.RADIO_STATIC, 0.8f, 1.4f);
            ModSounds.play(level, start, ModSounds.TENTACLE_BURST, 0.9f, 1.3f);
        }

        // glide along the ground: horizontal interpolation, height follows the terrain
        double f = Math.min(1.0, age / (double) steps);
        Vec3 flat = start.add(dest.subtract(start).scale(f));
        Double g = Targeting.findGround(level, flat.x, flat.y, flat.z, 8, 24);
        Vec3 pos = f >= 1.0 ? dest : new Vec3(flat.x, g != null ? g : flat.y, flat.z);

        // the streak of darkness rushing behind
        Vec3 d = pos.subtract(prev);
        int n = (int) Math.max(2, d.length() / 0.8);
        for (int i = 0; i <= n; i++) {
            Vec3 q = prev.add(d.scale((double) i / n));
            level.sendParticles(ShadowFx.BLACK, q.x, q.y + 0.5, q.z, 3, 0.3, 0.4, 0.3, 0);
            if (i % 2 == 0) level.sendParticles(ParticleTypes.SQUID_INK, q.x, q.y + 0.15, q.z, 1, 0.3, 0.02, 0.3, 0.01);
            if (i % 5 == 0) ShadowFx.spread(level, new Vec3(q.x, q.y, q.z), 1.6f, 30, 2);
        }
        level.sendParticles(ShadowFx.BLACK, pos.x, pos.y + 0.8, pos.z, 20, 0.5, 0.6, 0.5, 0);
        if (age % 2 == 0) ShadowFx.zap(level, prev.add(0, 0.6, 0), pos.add(0, 0.6, 0), 4);

        p.teleportTo(pos.x, pos.y, pos.z);
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
        p.hurtMarked = true;
        prev = pos;

        if (f >= 1.0) {
            arrive(level);
            TRAVELLING.remove(p.getUUID());
            p.removeEffect(MobEffects.INVISIBILITY);
            return true;
        }
        return false;
    }

    private void arrive(ServerLevel level) {
        ShadowFx.spread(level, dest, 5f, 40, 3);
        ShadowFxEntity.spawn(level, ShadowFxEntity.PILLAR, dest, 1.0f, 18, 3, 0f);
        ShadowFxEntity.spawn(level, ShadowFxEntity.RING, dest, 7f, 14, 8, 0f);
        for (int k = 0; k < 4; k++) {
            double a = k * Math.PI / 2 + 0.6;
            Vec3 root = dest.add(Math.cos(a) * 1.8, 0, Math.sin(a) * 1.8);
            TentacleEntity.spawn(level, root, new Vec3(Math.cos(a) * 1.4, 5, Math.sin(a) * 1.4), 18, 3, 1.0f);
        }
        level.sendParticles(ParticleTypes.SQUID_INK, dest.x, dest.y + 0.5, dest.z, 50, 0.8, 0.6, 0.8, 0.12);
        ShadowFx.crackle(level, dest, 3, 3);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(4), x -> Targeting.isValid(p, x))) {
            Targeting.hurt(p, e, 8f);
            Vec3 away = e.position().subtract(dest);
            away = new Vec3(away.x, 0, away.z);
            if (away.lengthSqr() > 1.0E-4) Targeting.launch(e, away.normalize().scale(1.4).add(0, 0.6, 0));
        }
        level.playSound(null, dest.x, dest.y, dest.z, net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.5f);
        ModSounds.play(level, dest, ModSounds.SHADOW_BOOM, 1.2f, 1.3f);
    }
}
