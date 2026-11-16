package com.alastor.staff.skill.task;

import com.alastor.staff.entity.TentacleEntity;
import com.alastor.staff.fx.ShadowFx;
import com.alastor.staff.registry.ModSounds;
import com.alastor.staff.util.Targeting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Skill 5 – Shadow Domain: darkness pours out from the caster across a 75-block circle. Every hostile
 * caught in it is held fast, and giant tentacles burst out of the shadow around it – some bare,
 * some ending in huge black swords, axes or spears – to strike it again and again.
 */
public class ArmoryTask implements SkillTask {
    public static final double RADIUS = 37.5;     // 75 blocks across
    private static final int SPREAD = 30;         // ticks for the shadow to reach the edge
    private static final int DURATION = 220;
    private static final int MAX_TARGETS = 40;
    private static final int[] WEAPONS = {TentacleEntity.SWORD, TentacleEntity.AXE, TentacleEntity.SPEAR, TentacleEntity.PLAIN};

    private final Player owner;
    private final Vec3 center;
    private final Map<UUID, LivingEntity> held = new HashMap<>();
    private final Map<UUID, Vec3> pins = new HashMap<>();
    private final Map<UUID, Integer> nextStrike = new HashMap<>();
    private final Map<UUID, Integer> strikes = new HashMap<>();
    private final Map<UUID, Integer> freeUntil = new HashMap<>();
    private int age;

    public ArmoryTask(Player owner, Vec3 center) {
        this.owner = owner;
        this.center = center;
    }

    @Override
    public boolean tick(ServerLevel level) {
        age++;
        RandomSource rnd = level.getRandom();
        double r = RADIUS * Math.min(1.0, age / (double) SPREAD);

        // the creeping edge of the darkness
        if (age <= SPREAD) {
            int n = (int) Math.min(70, 8 + r * 2);
            for (int i = 0; i < n; i++) {
                double a = rnd.nextDouble() * Math.PI * 2;
                double x = center.x + Math.cos(a) * r, z = center.z + Math.sin(a) * r;
                level.sendParticles(ParticleTypes.SQUID_INK, x, center.y + 0.2, z, 1, 0.3, 0.05, 0.3, 0.02);
                if (i % 5 == 0) level.sendParticles(ShadowFx.BLACK, x, center.y + 0.3, z, 1, 0.3, 0.1, 0.3, 0);
            }
            if (age % 3 == 0) {
                double a = rnd.nextDouble() * Math.PI * 2;
                Vec3 p = center.add(Math.cos(a) * r * 0.9, 0, Math.sin(a) * r * 0.9);
                Double g = Targeting.findGround(level, p.x, center.y, p.z);
                if (g != null) ShadowFx.spread(level, new Vec3(p.x, g, p.z), 4f + rnd.nextFloat() * 3f, DURATION - age + 20, 6);
            }
        }
        if (age % 10 == 0) ShadowFx.crackle(level, center, r, 2);
        if (age % 60 == 1) ModSounds.play(level, center, ModSounds.SHADOW_HUM, 3.0f, 0.7f);

        // catch hostiles as the darkness reaches them
        if (age % 2 == 0 && held.size() < MAX_TARGETS) {
            AABB box = new AABB(center.x - r, center.y - 8, center.z - r, center.x + r, center.y + 12, center.z + r);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> Targeting.isValid(owner, x)
                    && (x instanceof Enemy || x instanceof Player)
                    && Math.hypot(x.getX() - center.x, x.getZ() - center.z) <= r)) {
                if (held.size() >= MAX_TARGETS) break;
                if (held.containsKey(e.getUUID())) continue;
                held.put(e.getUUID(), e);
                pins.put(e.getUUID(), e.position());
                nextStrike.put(e.getUUID(), age + 4 + rnd.nextInt(14));
                strikes.put(e.getUUID(), 0);
                Double g = Targeting.findGround(level, e.getX(), e.getY(), e.getZ());
                if (g != null) {
                    Vec3 foot = new Vec3(e.getX(), g, e.getZ());
                    ShadowFx.spread(level, foot, 2.0f, DURATION - age + 10, 3);
                    // two tentacles bind it in place
                    for (int k = 0; k < 2; k++) {
                        double a = k * Math.PI + rnd.nextDouble();
                        Vec3 rt = foot.add(Math.cos(a) * 1.2, 0, Math.sin(a) * 1.2);
                        TentacleEntity.spawn(level, rt, e.position().add(0, e.getBbHeight() * 0.6, 0).subtract(rt),
                                DURATION - age + TentacleEntity.FALL_TICKS, 5, 0.8f);
                    }
                }
            }
        }

        // keep them pinned and order up the next weapon strike
        for (Map.Entry<UUID, LivingEntity> en : held.entrySet()) {
            LivingEntity e = en.getValue();
            if (!e.isAlive() || e.isRemoved()) continue;
            int free = freeUntil.getOrDefault(en.getKey(), 0);
            if (age == free) pins.put(en.getKey(), e.position());   // re-pin wherever the last blow left it
            Vec3 pin = pins.get(en.getKey());
            boolean beingStruck = age < free;
            if (!beingStruck && e.position().distanceToSqr(pin) > 0.04) e.teleportTo(pin.x, pin.y, pin.z);
            if (!beingStruck) {
                e.setDeltaMovement(Vec3.ZERO);
                e.hurtMarked = true;
            }
            e.fallDistance = 0;
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 10, false, false));
            if (e instanceof Mob mob) mob.getNavigation().stop();

            int due = nextStrike.get(en.getKey());
            int done = strikes.get(en.getKey());
            if (age >= due && done < 3 && age < DURATION - 30) {
                int weapon = WEAPONS[rnd.nextInt(WEAPONS.length)];
                SkillTaskManager.add(level, new WeaponStrikeTask(owner, e, e.position(), weapon, 6, 13 + rnd.nextDouble() * 5));
                strikes.put(en.getKey(), done + 1);
                nextStrike.put(en.getKey(), age + 35 + rnd.nextInt(20));
                freeUntil.put(en.getKey(), age + 30);   // let the blow knock it around before re-pinning
            }
        }

        // the battlefield erupts on its own too
        if (age > 10 && age < DURATION - 40 && age % 7 == 0) {
            double a = rnd.nextDouble() * Math.PI * 2, rr = Math.sqrt(rnd.nextDouble()) * r;
            Vec3 p = center.add(Math.cos(a) * rr, 0, Math.sin(a) * rr);
            SkillTaskManager.add(level, new WeaponStrikeTask(owner, null, p, WEAPONS[rnd.nextInt(3)], 8, 12 + rnd.nextDouble() * 6));
        }
        return age >= DURATION;
    }
}
