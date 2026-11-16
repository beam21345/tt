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

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A giant tentacle that erupts from a pool of shadow carrying a weapon:
 * SWORD rises and slashes down, AXE winds back further and chops, SPEAR thrusts straight up
 * through its target, PLAIN grabs, lifts and slams.
 */
public class WeaponStrikeTask implements SkillTask {
    private final Player owner;
    @Nullable private final LivingEntity target;
    private final Vec3 aimPoint;
    private final int weapon;
    private final double length;
    private Vec3 root;
    private int delay;
    private int age;
    private TentacleEntity t;
    private final Set<UUID> hit = new HashSet<>();

    public WeaponStrikeTask(Player owner, @Nullable LivingEntity target, Vec3 aimPoint, int weapon, int delay, double length) {
        this.owner = owner;
        this.target = target;
        this.aimPoint = aimPoint;
        this.weapon = weapon;
        this.delay = delay;
        this.length = length;
    }

    private Vec3 targetPos() {
        return target != null && target.isAlive() ? target.position() : aimPoint;
    }

    @Override
    public boolean tick(ServerLevel level) {
        if (root == null) {
            Vec3 tp = targetPos();
            double off = weapon == TentacleEntity.SPEAR ? 0.6 : 5.0 + owner.getRandom().nextDouble() * 2.5;
            double a = owner.getRandom().nextDouble() * Math.PI * 2;
            double x = tp.x + Math.cos(a) * off, z = tp.z + Math.sin(a) * off;
            Double g = Targeting.findGround(level, x, tp.y, z);
            if (g == null) return true;
            root = new Vec3(x, g, z);
            ShadowFx.spread(level, root, 3.2f, delay + 60, Math.max(3, delay));
        }
        if (delay-- > 0) {
            if (delay % 3 == 0) level.sendParticles(ParticleTypes.SQUID_INK, root.x, root.y + 0.1, root.z, 4, 1.0, 0.02, 1.0, 0.02);
            return false;
        }
        age++;
        return switch (weapon) {
            case TentacleEntity.SPEAR -> spear(level);
            case TentacleEntity.PLAIN -> grabSlam(level);
            default -> slash(level, weapon == TentacleEntity.AXE);
        };
    }

    private void erupt(ServerLevel level, Vec3 tip, int life, int rise, float thick) {
        t = TentacleEntity.spawn(level, root, tip, life, rise, thick);
        if (t != null) t.setWeapon(weapon);
        debris(level, root, 30);
        ModSounds.play(level, root, ModSounds.TENTACLE_BURST, 1.6f, 0.75f);
    }

    // ---- sword / axe: rise, rear back, crash down on the target ----
    private boolean slash(ServerLevel level, boolean axe) {
        int rise = 6, hold = axe ? 8 : 5, swing = axe ? 5 : 4;
        Vec3 up = new Vec3(0, length, 0);
        if (age == 1) erupt(level, up, rise + hold + swing + 14, rise, axe ? 2.3f : 2.0f);
        if (t == null) return true;
        Vec3 tp = targetPos();
        Vec3 flat = new Vec3(tp.x - root.x, 0, tp.z - root.z);
        Vec3 dir = flat.lengthSqr() > 1.0E-4 ? flat.normalize() : new Vec3(1, 0, 0);
        int s = age - rise;
        if (s > 0 && s <= hold) {
            // rear back away from the target
            double f = (double) s / hold;
            Vec3 back = dir.scale(-length * (axe ? 0.55 : 0.35)).add(0, length * 0.85, 0);
            t.setTip(lerp(up, back, f));
        } else if (s > hold && s <= hold + swing) {
            double f = (double) (s - hold) / swing;
            Vec3 back = dir.scale(-length * (axe ? 0.55 : 0.35)).add(0, length * 0.85, 0);
            Vec3 down = dir.scale(length * 0.95).add(0, 0.6, 0);
            Vec3 tip = arc(back, down, f * f);
            t.setTip(tip);
            if (s == hold + swing) impact(level, root.add(down), axe ? 26f : 20f, axe ? 5.0 : 3.5, axe);
        } else if (s == hold + swing + 6) {
            t.retract();
        }
        return s >= hold + swing + 8;
    }

    // ---- spear: shoots straight up through the target ----
    private boolean spear(ServerLevel level) {
        if (age == 1) {
            Vec3 tp = targetPos();
            Vec3 tipDir = new Vec3(tp.x - root.x, 0, tp.z - root.z).scale(0.3).add(0, length, 0);
            erupt(level, tipDir, 26, 2, 1.7f);
            AABB box = new AABB(root.x - 2, root.y - 1, root.z - 2, root.x + 2, root.y + length, root.z + 2);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> Targeting.isValid(owner, x))) {
                e.invulnerableTime = 0;
                Targeting.hurt(owner, e, 22f);
                Targeting.launch(e, new Vec3(0, 1.4, 0));
                level.sendParticles(ShadowFx.BLACK, e.getX(), e.getY() + 1, e.getZ(), 30, 0.4, 0.8, 0.4, 0);
            }
            ModSounds.play(level, root, ModSounds.SHADOW_BOOM, 1.0f, 1.4f);
            ShadowFx.crackle(level, root, 2, 2);
        }
        return age >= 3;
    }

    // ---- plain giant tentacle: seize, hoist, slam ----
    private boolean grabSlam(ServerLevel level) {
        if (age == 1) erupt(level, targetPos().add(0, 1, 0).subtract(root), 34, 4, 2.1f);
        if (t == null) return true;
        LivingEntity v = target != null && target.isAlive() ? target : null;
        if (age <= 12) {
            if (v != null) {
                Vec3 lift = v.position().add(0, 0.35, 0);
                v.teleportTo(lift.x, lift.y, lift.z);
                v.setDeltaMovement(Vec3.ZERO);
                v.fallDistance = 0;
                t.reachTo(v.position().add(0, v.getBbHeight() * 0.5, 0));
            }
        } else if (age <= 16) {
            Vec3 slamAt = root.add(new Vec3(targetPos().x - root.x, 0, targetPos().z - root.z).normalize().scale(length * 0.6));
            t.setTip(lerp(new Vec3(0, length, 0), slamAt.subtract(root).add(0, 0.5, 0), (age - 12) / 4.0));
            if (v != null) {
                Vec3 b = t.position().add(t.tipX(), t.tipY(), t.tipZ());
                v.teleportTo(b.x, b.y, b.z);
            }
            if (age == 16) impact(level, slamAt, 22f, 3.5, false);
        } else if (age == 20) {
            t.retract();
        }
        return age >= 22;
    }

    private void impact(ServerLevel level, Vec3 at, float dmg, double radius, boolean heavy) {
        Double g = Targeting.findGround(level, at.x, at.y + 1, at.z);
        Vec3 p = new Vec3(at.x, g != null ? g : at.y, at.z);
        ShadowFxEntity.spawn(level, ShadowFxEntity.RING, p, (float) radius * 2.5f, 16, 10, 0f);
        ShadowFx.spread(level, p, (float) radius * 1.4f, 40, 4);
        debris(level, p, heavy ? 90 : 55);
        level.sendParticles(ParticleTypes.SQUID_INK, p.x, p.y + 0.3, p.z, 40, radius * 0.6, 0.2, radius * 0.6, 0.15);
        level.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y + 0.5, p.z, heavy ? 3 : 1, radius * 0.4, 0.2, radius * 0.4, 0);
        if (heavy) ShadowFxEntity.spawn(level, ShadowFxEntity.PILLAR, p, 2.2f, 20, 4, 0f);
        ModSounds.play(level, p, ModSounds.SHADOW_BOOM, heavy ? 2.0f : 1.4f, heavy ? 0.8f : 1.1f);
        ShadowFx.crackle(level, p, (float) radius, heavy ? 3 : 2);
        AABB box = new AABB(p.x - radius, p.y - 1, p.z - radius, p.x + radius, p.y + 4, p.z + radius);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> Targeting.isValid(owner, x))) {
            if (!hit.add(e.getUUID())) continue;
            e.invulnerableTime = 0;
            Targeting.hurt(owner, e, dmg);
            Vec3 away = e.position().subtract(p);
            away = new Vec3(away.x, 0, away.z);
            Targeting.launch(e, (away.lengthSqr() > 1.0E-4 ? away.normalize().scale(0.8) : Vec3.ZERO).add(0, heavy ? 0.9 : 0.6, 0));
        }
    }

    private static void debris(ServerLevel level, Vec3 at, int count) {
        BlockState gs = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
        if (!gs.isAir()) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, gs), at.x, at.y + 0.3, at.z, count, 1.5, 0.3, 1.5, 0.4);
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double f) {
        return a.add(b.subtract(a).scale(f));
    }

    /** Swings along an arc (keeps the length roughly constant) instead of a straight line. */
    private Vec3 arc(Vec3 a, Vec3 b, double f) {
        Vec3 m = lerp(a, b, f);
        double len = a.length() + (b.length() - a.length()) * f;
        return m.lengthSqr() > 1.0E-6 ? m.normalize().scale(len) : m;
    }
}
