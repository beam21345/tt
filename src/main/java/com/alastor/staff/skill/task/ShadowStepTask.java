package com.alastor.staff.skill.task;

import com.alastor.staff.entity.ShadowFxEntity;
import com.alastor.staff.fx.ShadowFx;
import com.alastor.staff.registry.ModSounds;
import com.alastor.staff.util.Targeting;
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
 * Shadow Step: a pool of darkness opens under the caster and swallows them; they glide along the
 * ground as a living shadow (about 8 blocks per tick), leaving a fading trail of darkness, and rise
 * out of a pool at the destination.
 */
public class ShadowStepTask implements SkillTask {
    /** Players currently travelling as a shadow (they can't be hurt). */
    public static final Set<UUID> TRAVELLING = new HashSet<>();
    private static final double SPEED = 8.0;
    private static final int SINK = 6, RISE = 6;

    private final Player p;
    private final Vec3 start;
    private final Vec3 dest;
    private final int steps;
    private Vec3 prev;
    private double sinceTrail;
    private int age;

    public ShadowStepTask(Player p, Vec3 dest) {
        this.p = p;
        this.start = p.position();
        this.dest = dest;
        this.steps = (int) Math.max(3, Math.ceil(start.distanceTo(dest) / SPEED));
        this.prev = start;
    }

    private void hold(Vec3 at) {
        p.teleportTo(at.x, at.y, at.z);
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
        p.hurtMarked = true;
    }

    @Override
    public boolean tick(ServerLevel level) {
        if (p.isRemoved() || !p.isAlive() || p.level() != level) {
            TRAVELLING.remove(p.getUUID());
            return true;
        }
        age++;

        // ---- sinking into the shadow
        if (age <= SINK) {
            if (age == 1) {
                TRAVELLING.add(p.getUUID());
                ShadowFx.spread(level, start, 2.6f, SINK + 30, SINK);
                ModSounds.play(level, start, ModSounds.RADIO_STATIC, 0.7f, 0.6f);
            }
            if (age == SINK - 2) {
                p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, steps + SINK + RISE, 0, false, false));
                for (int k = 0; k < 3; k++) ShadowFx.spread(level, start, 1.2f + k * 0.9f, 22, 2 + k * 2);
                ModSounds.play(level, start, ModSounds.TENTACLE_BURST, 0.6f, 0.5f);
            }
            hold(start);
            return false;
        }

        // ---- gliding along the ground as a shadow
        int g = age - SINK;
        if (g <= steps) {
            double f = Math.min(1.0, g / (double) steps);
            Vec3 flat = start.add(dest.subtract(start).scale(f));
            Double gy = Targeting.findGround(level, flat.x, flat.y, flat.z, 8, 24);
            Vec3 pos = f >= 1.0 ? dest : new Vec3(flat.x, gy != null ? gy : flat.y, flat.z);

            // a trail of darkness that lingers and fades behind
            Vec3 d = pos.subtract(prev);
            double len = d.length();
            for (double t = 1.3 - sinceTrail; t <= len; t += 1.3) {
                Vec3 q = prev.add(d.scale(t / Math.max(1e-6, len)));
                Double qg = Targeting.findGround(level, q.x, q.y + 1, q.z, 3, 6);
                ShadowFx.spread(level, new Vec3(q.x, qg != null ? qg : q.y, q.z), 1.5f, 26, 2);
                sinceTrail = len - t;
            }
            ShadowFx.spread(level, pos, 2.3f, 5, 1);   // the shadow itself, racing ahead
            hold(pos);
            prev = pos;
            if (g == steps) {
                ShadowFx.spread(level, dest, 3.2f, RISE + 40, RISE);
                ModSounds.play(level, dest, ModSounds.RADIO_TUNE, 0.6f, 1.6f);
            }
            return false;
        }

        // ---- rising out at the destination
        hold(dest);
        if (age >= SINK + steps + RISE) {
            p.removeEffect(MobEffects.INVISIBILITY);
            TRAVELLING.remove(p.getUUID());
            arrive(level);
            return true;
        }
        return false;
    }

    private void arrive(ServerLevel level) {
        for (int k = 0; k < 3; k++) ShadowFx.spread(level, dest, 1.5f + k * 1.6f, 24, 2 + k * 2);
        ShadowFxEntity.spawn(level, ShadowFxEntity.RING, dest, 6f, 12, 8, 0f);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(4), x -> Targeting.isValid(p, x))) {
            Targeting.hurt(p, e, 8f);
            Vec3 away = e.position().subtract(dest);
            away = new Vec3(away.x, 0, away.z);
            if (away.lengthSqr() > 1.0E-4) Targeting.launch(e, away.normalize().scale(1.4).add(0, 0.6, 0));
        }
        ModSounds.play(level, dest, ModSounds.SHADOW_BOOM, 1.0f, 1.4f);
    }
}
