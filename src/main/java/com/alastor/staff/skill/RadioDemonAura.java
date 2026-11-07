package com.alastor.staff.skill;

import com.alastor.staff.entity.TentacleEntity;
import com.alastor.staff.fx.ShadowFx;
import com.alastor.staff.skill.task.GrabThrowTask;
import com.alastor.staff.skill.task.SkillTaskManager;
import com.alastor.staff.util.Targeting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Skill 6 – Radio Demon (toggle). While active, a 7x7 zone around the caster: creatures that enter
 * are grabbed, lifted and thrown away; incoming projectiles are caught and hurled back.
 */
public final class RadioDemonAura {
    public static final double HALF = 3.5; // 7x7 blocks
    private static final int REGRAB_DELAY = 40;

    private static final Map<UUID, Long> GRAB_COOLDOWN = new HashMap<>();
    private static final Map<UUID, Long> LAST_TICK = new HashMap<>();

    public static void tick(ServerLevel level, Player p) {
        long now = level.getGameTime();
        Long last = LAST_TICK.put(p.getUUID(), now);
        if (last != null && last == now) return; // several staffs in one inventory: run once per tick

        Vec3 c = p.position();
        if (now % 4 == 0) {
            double rot = now * 0.05;
            ShadowFx.ring(level, c.add(0, 0.1, 0), HALF, 28, ShadowFx.GREEN_SMALL, rot);
            ShadowFx.ring(level, c.add(0, 0.15, 0), HALF - 0.4, 18, ShadowFx.BLACK_SMALL, -rot);
            for (int k = 0; k < 2; k++) {
                double a = rot * 2 + k * Math.PI;
                Vec3 base = c.add(Math.cos(a) * HALF, 0, Math.sin(a) * HALF);
                ShadowFx.tentacle(level, base, base.add(0, 1.4, 0), a);
            }
        }

        // Projectiles: catch anything flying toward us that isn't ours and throw it back.
        AABB projBox = p.getBoundingBox().inflate(HALF + 2.0);
        for (Projectile proj : level.getEntitiesOfClass(Projectile.class, projBox, pr -> pr.getOwner() != p)) {
            Vec3 v = proj.getDeltaMovement();
            if (v.lengthSqr() < 0.01) continue;
            Vec3 toPlayer = p.getEyePosition().subtract(proj.position());
            if (v.dot(toPlayer) <= 0) continue;

            Entity shooter = proj.getOwner();
            Vec3 newDir = (shooter != null && shooter.isAlive() && shooter.level() == level)
                    ? shooter.getEyePosition().subtract(proj.position()).normalize()
                    : v.normalize().scale(-1);
            Vec3 newVel = newDir.scale(Math.max(v.length(), 1.2) * 1.1);

            proj.setOwner(p);
            proj.setDeltaMovement(newVel);
            if (proj instanceof AbstractHurtingProjectile fireball) {
                fireball.xPower = newDir.x * 0.1;
                fireball.yPower = newDir.y * 0.1;
                fireball.zPower = newDir.z * 0.1;
            }
            proj.hurtMarked = true;

            Vec3 root = new Vec3(proj.getX(), c.y, proj.getZ());
            TentacleEntity.spawn(level, root, proj.position().subtract(root), 10, 2, 0.35f);
            level.sendParticles(ShadowFx.GREEN, proj.getX(), proj.getY(), proj.getZ(), 6, 0.2, 0.2, 0.2, 0);
            level.playSound(null, proj.getX(), proj.getY(), proj.getZ(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 0.7f, 1.5f);
        }

        // Creatures: grab, lift, throw.
        AABB box = new AABB(c.x - HALF, c.y - 1.5, c.z - HALF, c.x + HALF, c.y + 3.5, c.z + HALF);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> Targeting.isValid(p, x))) {
            Long until = GRAB_COOLDOWN.get(e.getUUID());
            if (until != null && until > now) continue;
            GRAB_COOLDOWN.put(e.getUUID(), now + REGRAB_DELAY);
            SkillTaskManager.add(level, new GrabThrowTask(p, e, level));
        }

        if (now % 200 == 0) {
            GRAB_COOLDOWN.values().removeIf(t -> t < now);
            LAST_TICK.values().removeIf(t -> t < now - 100);
        }
    }

    private RadioDemonAura() {}
}
