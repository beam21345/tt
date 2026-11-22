package com.alastor.staff.skill.task;

import com.alastor.staff.entity.ShadowFxEntity;
import com.alastor.staff.entity.TentacleEntity;
import com.alastor.staff.fx.ShadowFx;
import com.alastor.staff.registry.ModSounds;
import com.alastor.staff.util.Targeting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Skill 1 finale: one giant sigil opens under the target area, giant tentacles seize every creature,
 * hoist them into the air, then all the tentacles constrict together on one point until it bursts.
 */
public class FinaleTask implements SkillTask {
    private static final double AREA = 18;
    private static final int GRAB = 8, LIFT_END = 32, SQUEEZE_END = 52;

    private final Player owner;
    private final Vec3 center;
    private final Vec3 core;
    private final List<LivingEntity> victims = new ArrayList<>();
    private final List<TentacleEntity> grabbers = new ArrayList<>();
    private final List<TentacleEntity> ring = new ArrayList<>();
    private final List<Vec3> ringStart = new ArrayList<>();
    private int age;

    public FinaleTask(Player owner, Vec3 center) {
        this.owner = owner;
        this.center = center;
        this.core = center.add(0, 11, 0);
    }

    private Vec3 slot(int i) {
        double a = i * 2.4;
        double r = victims.size() <= 1 ? 0 : 1.6;
        return core.add(Math.cos(a) * r, (i % 3) * 0.6 - 0.6, Math.sin(a) * r);
    }

    @Override
    public boolean tick(ServerLevel level) {
        age++;
        if (age == 1) {
            ShadowFxEntity.spawn(level, ShadowFxEntity.SIGIL, center, 22f, SQUEEZE_END + 40, 10, 0f);   // the one giant sigil
            ShadowFx.spread(level, center, 24f, SQUEEZE_END + 40, 14);
            ModSounds.play(level, center, ModSounds.RADIO_TUNE, 3.0f, 0.6f);
            ModSounds.play(level, center, ModSounds.SHADOW_HUM, 2.5f, 0.9f);
            AABB box = new AABB(center.x - AREA, center.y - 4, center.z - AREA, center.x + AREA, center.y + 10, center.z + AREA);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> Targeting.isValid(owner, x))) {
                if (victims.size() >= 16) break;
                victims.add(e);
            }
        }

        if (age == GRAB) {
            // one giant tentacle per victim, erupting from the shadow beside it
            for (LivingEntity v : victims) {
                Vec3 out = new Vec3(v.getX() - center.x, 0, v.getZ() - center.z);
                out = out.lengthSqr() > 1.0E-3 ? out.normalize() : new Vec3(1, 0, 0);
                Vec3 p = v.position().add(out.scale(3.0));
                Double g = Targeting.findGround(level, p.x, v.getY(), p.z);
                Vec3 root = new Vec3(p.x, g != null ? g : center.y, p.z);
                TentacleEntity t = TentacleEntity.spawn(level, root, v.position().add(0, v.getBbHeight() * 0.5, 0).subtract(root),
                        SQUEEZE_END - GRAB + 12, 4, 2.4f);
                if (t != null) grabbers.add(t);
                debris(level, root, 30);
            }
            // a ring of colossal tentacles around the sigil
            for (int k = 0; k < 12; k++) {
                double a = k * Math.PI * 2 / 12;
                Vec3 p = center.add(Math.cos(a) * 16, 0, Math.sin(a) * 16);
                Double g = Targeting.findGround(level, p.x, center.y, p.z);
                if (g == null) continue;
                Vec3 root = new Vec3(p.x, g, p.z);
                Vec3 tip = new Vec3(-Math.cos(a) * 3, 22 + (k % 3) * 2, -Math.sin(a) * 3);
                TentacleEntity t = TentacleEntity.spawn(level, root, tip, SQUEEZE_END - GRAB + 14, 8, 3.3f);
                if (t != null) { ring.add(t); ringStart.add(tip); }
                debris(level, root, 40);
            }
            ModSounds.play(level, center, ModSounds.TENTACLE_BURST, 3.0f, 0.6f);
            ModSounds.play(level, center, ModSounds.SHADOW_BOOM, 1.5f, 1.2f);
        }

        if (age > GRAB && age <= SQUEEZE_END) {
            boolean squeezing = age > LIFT_END;
            double f = squeezing ? 1.0 : (age - GRAB) / (double) (LIFT_END - GRAB);
            for (int i = 0; i < victims.size(); i++) {
                LivingEntity v = victims.get(i);
                if (!v.isAlive()) continue;
                Vec3 goal = squeezing ? core.add(slot(i).subtract(core).scale(0.3)) : slot(i);
                Vec3 next = v.position().add(goal.subtract(v.position()).scale(squeezing ? 0.4 : 0.18));
                v.teleportTo(next.x, next.y, next.z);
                v.setDeltaMovement(Vec3.ZERO);
                v.fallDistance = 0;
                if (i < grabbers.size()) grabbers.get(i).reachTo(v.position().add(0, v.getBbHeight() * 0.5, 0));
            }
            // the ring tentacles bend inward and coil tighter and tighter around the core
            double coil = squeezing ? 1.0 : f * 0.6;
            for (int k = 0; k < ring.size(); k++) {
                TentacleEntity t = ring.get(k);
                Vec3 target = core.add(Math.cos(age * 0.3 + k) * (1 - coil) * 2.5, (k % 2) * 0.8, Math.sin(age * 0.3 + k) * (1 - coil) * 2.5);
                Vec3 want = target.subtract(t.position());
                Vec3 from = ringStart.get(k);
                t.setTip(from.add(want.subtract(from).scale(Math.min(1.0, coil + 0.2))));
            }
            if (age % 3 == 0) {
                // a storm gathers: lightning from the sky into the ring
                double a = level.random.nextDouble() * Math.PI * 2, rr = level.random.nextDouble() * AREA;
                ShadowFxEntity.bolt(level, center.add(Math.cos(a) * rr * 0.4, 32, Math.sin(a) * rr * 0.4),
                        center.add(Math.cos(a) * rr, 0.2, Math.sin(a) * rr), level.random.nextFloat() < 0.25f, 8);
            }
            if (age == LIFT_END + 1) ShadowFxEntity.spawn(level, ShadowFxEntity.ORB, core, 3.2f, SQUEEZE_END - LIFT_END + 4, SQUEEZE_END - LIFT_END, 0f);
            if (squeezing) {
                level.sendParticles(ShadowFx.BLACK, core.x, core.y, core.z, 25, 1.6, 1.6, 1.6, 0);
                if (age % 2 == 0) ShadowFx.zap(level, core.add((level.random.nextDouble() - 0.5) * 6, -3, (level.random.nextDouble() - 0.5) * 6), core, 4);
                if (age == LIFT_END + 1) ModSounds.play(level, core, ModSounds.RADIO_TUNE, 2.0f, 0.7f);
            }
        }

        if (age == SQUEEZE_END) {
            explode(level);
            return true;
        }
        return false;
    }

    private void explode(ServerLevel level) {
        for (TentacleEntity t : grabbers) t.retract();
        for (TentacleEntity t : ring) {
            Vec3 out = t.position().subtract(center);
            t.setTip(new Vec3(out.x, 0, out.z).normalize().scale(6).add(0, 12, 0));
            t.retract();
        }
        ShadowFxEntity.spawn(level, ShadowFxEntity.PILLAR, center, 6.0f, 60, 5, 0f);
        ShadowFxEntity.spawn(level, ShadowFxEntity.PILLAR, center, 2.5f, 70, 3, 0f);
        ShadowFxEntity.spawn(level, ShadowFxEntity.RING, center, 32f, 28, 20, 0f);
        ShadowFxEntity.spawn(level, ShadowFxEntity.RING, center, 22f, 22, 14, 0f);
        ShadowFxEntity.spawn(level, ShadowFxEntity.RING, center, 12f, 16, 8, 0f);
        com.alastor.staff.util.TerrainBreaker.smash(level, owner, center, 4.0, 60);
        ShadowFx.crackle(level, center, (float) AREA, 10);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, core.x, core.y, core.z, 3, 1.2, 1.2, 1.2, 0);
        level.sendParticles(ParticleTypes.SONIC_BOOM, core.x, core.y, core.z, 4, 1.5, 1.5, 1.5, 0);
        level.sendParticles(ShadowFx.BLACK, core.x, core.y, core.z, 250, 4, 4, 4, 0);
        level.sendParticles(ShadowFx.GREEN, core.x, core.y, core.z, 160, 3, 3, 3, 0);
        for (int k = 0; k < 16; k++) {
            double a = k * Math.PI * 2 / 16;
            ShadowFxEntity.bolt(level, core, center.add(Math.cos(a) * 16, 0.1, Math.sin(a) * 16), k % 4 == 0, 10);
        }
        ModSounds.play(level, core, ModSounds.SHADOW_BOOM, 3.0f, 0.7f);
        level.playSound(null, core.x, core.y, core.z, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,
                net.minecraft.sounds.SoundSource.PLAYERS, 2.5f, 0.6f);

        for (LivingEntity v : victims) {
            if (!v.isAlive()) continue;
            v.invulnerableTime = 0;
            Targeting.hurt(owner, v, 40f);
            Vec3 out = v.position().subtract(core);
            Targeting.launch(v, (out.lengthSqr() > 1.0E-3 ? out.normalize() : new Vec3(0, 1, 0)).scale(1.4).add(0, 0.6, 0));
        }
        AABB box = new AABB(core.x - 14, core.y - 14, core.z - 14, core.x + 14, core.y + 8, core.z + 14);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> Targeting.isValid(owner, x) && !victims.contains(x))) {
            Targeting.hurt(owner, e, 12f);
        }
    }

    private static void debris(ServerLevel level, Vec3 at, int count) {
        BlockState gs = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
        if (!gs.isAir()) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, gs), at.x, at.y + 0.3, at.z, count, 1.5, 0.3, 1.5, 0.4);
    }
}
