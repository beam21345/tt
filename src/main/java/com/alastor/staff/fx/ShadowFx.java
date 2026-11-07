package com.alastor.staff.fx;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Black shadow + green aura particle drawing, all done server-side so every player sees it. */
public final class ShadowFx {
    public static final DustParticleOptions BLACK = new DustParticleOptions(new Vector3f(0.03f, 0.02f, 0.04f), 1.8f);
    public static final DustParticleOptions BLACK_SMALL = new DustParticleOptions(new Vector3f(0.03f, 0.02f, 0.04f), 1.0f);
    public static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.15f, 1.0f, 0.35f), 1.4f);
    public static final DustParticleOptions GREEN_SMALL = new DustParticleOptions(new Vector3f(0.2f, 1.0f, 0.4f), 0.8f);

    /** A swaying shadow tentacle from base to tip, thick at the root and thin at the tip. */
    public static void tentacle(ServerLevel level, Vec3 base, Vec3 tip, double phase) {
        Vec3 axis = tip.subtract(base);
        double len = axis.length();
        if (len < 0.05) return;
        Vec3 dir = axis.scale(1.0 / len);
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0E-4) side = new Vec3(1, 0, 0);
        side = side.normalize();
        Vec3 side2 = dir.cross(side).normalize();

        int steps = Math.max(4, (int) (len / 0.3));
        for (int i = 0; i <= steps; i++) {
            double f = (double) i / steps;
            double sway = Math.sin(f * Math.PI * 2.2 + phase) * 0.55 * f;
            double sway2 = Math.cos(f * Math.PI * 1.7 + phase * 1.3) * 0.35 * f;
            Vec3 pt = base.add(axis.scale(f)).add(side.scale(sway)).add(side2.scale(sway2));
            double thick = 0.28 * (1 - f) + 0.05;
            level.sendParticles(BLACK, pt.x, pt.y, pt.z, 2, thick, thick, thick, 0);
            if (i % 4 == 0) level.sendParticles(GREEN_SMALL, pt.x, pt.y, pt.z, 1, thick, thick, thick, 0);
        }
        level.sendParticles(GREEN, tip.x, tip.y, tip.z, 2, 0.1, 0.1, 0.1, 0);
    }

    public static void ring(ServerLevel level, Vec3 c, double r, int points, ParticleOptions o, double rotation) {
        for (int i = 0; i < points; i++) {
            double a = rotation + Math.PI * 2 * i / points;
            level.sendParticles(o, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    public static void line(ServerLevel level, Vec3 a, Vec3 b, ParticleOptions o, double spacing) {
        Vec3 d = b.subtract(a);
        int n = Math.max(1, (int) (d.length() / spacing));
        for (int i = 0; i <= n; i++) {
            Vec3 p = a.add(d.scale((double) i / n));
            level.sendParticles(o, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    /** Voodoo-style ground sigil: double ring, a rotated cross and small orbs on the ends. */
    public static void sigil(ServerLevel level, Vec3 c, double r, double rot) {
        Vec3 g = c.add(0, 0.05, 0);
        int pts = (int) (r * 14) + 8;
        ring(level, g, r, pts, GREEN_SMALL, rot);
        ring(level, g, r * 0.62, pts / 2 + 4, BLACK_SMALL, -rot);
        for (int k = 0; k < 4; k++) {
            double a = rot + Math.PI / 2 * k + Math.PI / 4;
            Vec3 end = g.add(Math.cos(a) * r, 0, Math.sin(a) * r);
            line(level, g, end, GREEN_SMALL, 0.35);
            ring(level, end, 0.3, 6, GREEN_SMALL, 0);
        }
    }

    private ShadowFx() {}
}
