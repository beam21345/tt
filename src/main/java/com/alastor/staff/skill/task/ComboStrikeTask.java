package com.alastor.staff.skill.task;

import com.alastor.staff.entity.ShadowFxEntity;
import com.alastor.staff.entity.TentacleEntity;
import com.alastor.staff.fx.ShadowFx;
import com.alastor.staff.registry.ModSounds;
import com.alastor.staff.util.Targeting;
import com.alastor.staff.util.TerrainBreaker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** One hit of the five-hit tentacle combo. Tentacles rise from shadow pools around the caster. */
public class ComboStrikeTask implements SkillTask {
    private final Player p;
    private final int step;
    private final Vec3 fwd, right, origin;
    private final List<TentacleEntity> ts = new ArrayList<>();
    private final Set<UUID> hit = new HashSet<>();
    private int age;
    private double lastAngle;

    public ComboStrikeTask(Player p, int step) {
        this.p = p;
        this.step = step;
        this.fwd = Targeting.horizontalLook(p);
        this.right = new Vec3(-fwd.z, 0, fwd.x);     // caster's right-hand side
        this.origin = p.position();
    }

    private Vec3 ground(Vec3 at) {
        Double g = Targeting.findGround(p.level(), at.x, at.y + 1, at.z, 3, 8);
        return new Vec3(at.x, g != null ? g : at.y, at.z);
    }

    private TentacleEntity rise(ServerLevel level, Vec3 root, Vec3 tip, int life, float thick) {
        ShadowFx.spread(level, root, 1.6f + thick, life + 10, 3);
        TentacleEntity t = TentacleEntity.spawn(level, root, tip, life, 3, thick);
        if (t != null) ts.add(t);
        return t;
    }

    private void strike(ServerLevel level, LivingEntity e, float dmg, Vec3 push) {
        if (!hit.add(e.getUUID())) return;
        e.invulnerableTime = 0;
        Targeting.hurt(p, e, dmg);
        Targeting.launch(e, e.getDeltaMovement().add(push));
        level.sendParticles(ShadowFx.BLACK, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 10, 0.3, 0.4, 0.3, 0);
    }

    @Override
    public boolean tick(ServerLevel level) {
        if (p.isRemoved() || !p.isAlive()) return true;
        age++;
        return switch (step) {
            case 0 -> sweep(level, 1);
            case 1 -> sweep(level, -1);
            case 2 -> slam(level);
            case 3 -> thrust(level);
            default -> burst(level);
        };
    }

    // ---- hits 1 & 2: a tentacle comes from behind one shoulder and sweeps a half circle across the front
    private boolean sweep(ServerLevel level, int side) {
        Vec3 sideV = right.scale(side);
        double radius = 7.0;
        Vec3 center = origin.add(0, 1.1, 0);
        int windup = 3, swing = 6;
        if (age == 1) {
            Vec3 root = ground(origin.subtract(fwd.scale(1.4)).add(sideV.scale(1.6)));
            Vec3 start = center.add(sideV.scale(radius * 0.6)).subtract(fwd.scale(radius * 0.4)).subtract(root);
            rise(level, root, start, windup + swing + 10, 1.25f);
            lastAngle = 0;
            ModSounds.play(level, root, ModSounds.TENTACLE_BURST, 0.9f, 1.3f);
        }
        TentacleEntity t = ts.isEmpty() ? null : ts.get(0);
        if (t == null) return true;
        int s = age - windup;
        if (s >= 0 && s <= swing) {
            double a = Math.PI * s / swing;   // 0 = caster's side, PI/2 = straight ahead, PI = other side
            Vec3 dir = sideV.scale(Math.cos(a)).add(fwd.scale(Math.sin(a)));
            Vec3 tipW = center.add(dir.scale(radius)).add(0, 0.3, 0);
            t.reachTo(tipW);
            // everything inside the swept slice gets hit
            AABB box = new AABB(center, center).inflate(radius + 1, 2.5, radius + 1);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> Targeting.isValid(p, x))) {
                Vec3 rel = e.position().subtract(origin);
                Vec3 flat = new Vec3(rel.x, 0, rel.z);
                double dist = flat.length();
                if (dist > radius + 1 || dist < 0.5) continue;
                double ea = Math.atan2(flat.dot(fwd), flat.dot(sideV));
                if (ea >= lastAngle - 0.2 && ea <= a + 0.2) {
                    Vec3 tangent = fwd.scale(Math.cos(a)).subtract(sideV.scale(Math.sin(a)));
                    strike(level, e, 9f, tangent.scale(0.9).add(flat.normalize().scale(0.6)).add(0, 0.4, 0));
                }
            }
            TerrainBreaker.smash(level, p, tipW, 1.3, 4);
            lastAngle = a;
            if (s == swing / 2) ModSounds.play(level, tipW, ModSounds.ZAP, 0.6f, 1.4f);
        }
        if (s == swing + 2) t.retract();
        return s >= swing + 3;
    }

    // ---- hit 3: rears up behind the caster and slams down in front
    private boolean slam(ServerLevel level) {
        if (age == 1) {
            Vec3 root = ground(origin.subtract(fwd.scale(1.8)));
            rise(level, root, fwd.scale(-1.0).add(0, 8, 0), 26, 1.6f);
        }
        TentacleEntity t = ts.isEmpty() ? null : ts.get(0);
        if (t == null) return true;
        Vec3 impact = ground(origin.add(fwd.scale(7)));
        if (age > 4 && age <= 7) {
            double f = (age - 4) / 3.0;
            Vec3 up = t.position().add(fwd.scale(-1.0)).add(0, 8, 0);
            t.reachTo(up.add(impact.add(0, 0.4, 0).subtract(up).scale(f * f)));
        }
        if (age == 7) {
            ShadowFxEntity.spawn(level, ShadowFxEntity.RING, impact, 7f, 14, 8, 0f);
            ShadowFx.spread(level, impact, 3.5f, 40, 3);
            ShadowFx.crackle(level, impact, 3, 2);
            ModSounds.play(level, impact, ModSounds.SHADOW_BOOM, 1.4f, 1.2f);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(impact, impact).inflate(3.2, 2.5, 3.2),
                    x -> Targeting.isValid(p, x))) {
                strike(level, e, 14f, new Vec3(0, 0.9, 0));
            }
            // the shockwave runs along the line between the caster and the impact
            for (double d = 1; d < 7; d += 1) {
                Vec3 q = origin.add(fwd.scale(d));
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(q, q).inflate(1.2, 2, 1.2),
                        x -> Targeting.isValid(p, x))) strike(level, e, 8f, fwd.scale(0.6).add(0, 0.5, 0));
            }
            TerrainBreaker.smash(level, p, impact, 2.2, 14);
        }
        if (age == 12) t.retract();
        return age >= 13;
    }

    // ---- hit 4: two tentacles spear forward past the caster's shoulders
    private boolean thrust(ServerLevel level) {
        if (age == 1) {
            for (int side = -1; side <= 1; side += 2) {
                Vec3 root = ground(origin.subtract(fwd.scale(1.0)).add(right.scale(side * 1.3)));
                rise(level, root, fwd.scale(-0.5).add(0, 2.2, 0).add(right.scale(side * 0.4)), 20, 0.9f);
            }
        }
        if (age >= 4 && age <= 6) {
            double f = (age - 3) / 3.0;
            for (int i = 0; i < ts.size(); i++) {
                int side = i == 0 ? -1 : 1;
                Vec3 tipW = origin.add(0, 1.4, 0).add(right.scale(side * 0.5)).add(fwd.scale(11 * f));
                ts.get(i).reachTo(tipW);
                if (age == 6) TerrainBreaker.smash(level, p, tipW, 1.0, 3);
            }
            for (double d = 0.5; d <= 11 * f; d += 1) {
                Vec3 q = origin.add(0, 1, 0).add(fwd.scale(d));
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(q, q).inflate(1.3, 1.5, 1.3),
                        x -> Targeting.isValid(p, x))) strike(level, e, 10f, fwd.scale(1.3).add(0, 0.3, 0));
            }
            if (age == 4) ModSounds.play(level, origin, ModSounds.TENTACLE_BURST, 1.0f, 1.6f);
        }
        if (age == 10) ts.forEach(TentacleEntity::retract);
        return age >= 11;
    }

    // ---- hit 5: a ring of tentacles bursts out and lashes outward, with a crack of radio static
    private boolean burst(ServerLevel level) {
        if (age == 1) {
            for (int k = 0; k < 8; k++) {
                double a = k * Math.PI / 4;
                Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
                Vec3 root = ground(origin.add(out.scale(2.4)));
                rise(level, root, new Vec3(0, 4.5, 0).add(out.scale(-0.5)), 22, 1.1f);
            }
            ShadowFx.spread(level, origin, 4f, 30, 3);
            ModSounds.play(level, origin, ModSounds.RADIO_TUNE, 1.2f, 1.2f);
        }
        if (age >= 4 && age <= 8) {
            double f = (age - 3) / 5.0;
            for (TentacleEntity t : ts) {
                Vec3 out = t.position().subtract(origin);
                out = new Vec3(out.x, 0, out.z).normalize();
                t.setTip(new Vec3(0, 4.5 * (1 - f) + 0.6, 0).add(out.scale(6.0 * f)));
            }
        }
        if (age == 6) {
            ShadowFxEntity.spawn(level, ShadowFxEntity.RING, origin, 9f, 16, 8, 0f);
            for (int k = 0; k < 8; k++) {
                double a = k * Math.PI / 4 + 0.4;
                ShadowFxEntity.bolt(level, origin.add(0, 1.2, 0), origin.add(Math.cos(a) * 8, 0.2, Math.sin(a) * 8), k % 3 == 0, 7);
            }
            ModSounds.play(level, origin, ModSounds.SHADOW_BOOM, 1.4f, 1.0f);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(8.5, 3, 8.5),
                    x -> Targeting.isValid(p, x))) {
                Vec3 out = e.position().subtract(origin);
                out = new Vec3(out.x, 0, out.z);
                strike(level, e, 12f, (out.lengthSqr() > 1e-4 ? out.normalize() : fwd).scale(1.8).add(0, 0.8, 0));
            }
        }
        if (age == 12) ts.forEach(TentacleEntity::retract);
        return age >= 13;
    }
}
