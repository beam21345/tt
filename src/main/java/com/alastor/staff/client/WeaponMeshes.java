package com.alastor.staff.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Organic shadow weapons grown out of a tentacle tip, built from smooth lofted cross-sections so they
 * share the tentacle's glossy black skin. Local frame: +Y runs along the weapon from the grip, X is the
 * blade's width, Z its thickness. Units are 1/16 block before the caller's scale.
 */
final class WeaponMeshes {
    /** One cross-section: height, half width, half thickness, sideways offset (for curved blades). */
    private record Ring(float y, float hw, float ht, float x) {}

    private static final int SIDES = 12;

    // ------------------------------------------------------------------ weapons

    /** Curved demonic sabre with a swept crossguard. */
    static void sword(VertexConsumer vc, boolean glowPass, Matrix4f m, Matrix3f n, int light) {
        VertexConsumer skin = glowPass ? null : vc, glow = glowPass ? vc : null;
        if (!glowPass) loft(skin, m, n, light, false, new Ring(-3.5f, 0.75f, 0.75f, 0), new Ring(-0.5f, 0.65f, 0.65f, 0), new Ring(0, 0.9f, 0.9f, 0));
        // crossguard, swept up at the ends
        if (!glowPass) loftX(skin, m, n, light, -4.2f, 4.2f, 0.3f, 0.55f, 0.8f);
        Ring[] blade = new Ring[12];
        for (int i = 0; i < blade.length; i++) {
            float t = i / (float) (blade.length - 1);
            float y = 1.0f + t * 21f;
            float hw = t < 0.75f ? 1.75f + 0.25f * (float) Math.sin(t * Math.PI * 1.3) : 1.85f * (1f - t) / 0.25f;
            blade[i] = new Ring(y, Math.max(0.02f, hw), Math.max(0.02f, 0.38f * (1f - t * 0.85f)), 0.0065f * (y - 1) * (y - 1));
        }
        if (glowPass) edges(glow, m, n, blade);
        else loft(skin, m, n, light, true, blade);
    }

    /** Long-hafted war axe with a crescent blade and a spiked crown. */
    static void axe(VertexConsumer vc, boolean glowPass, Matrix4f m, Matrix3f n, int light) {
        VertexConsumer skin = glowPass ? null : vc, glow = glowPass ? vc : null;
        if (!glowPass) loft(skin, m, n, light, false, new Ring(-3.5f, 0.7f, 0.7f, 0), new Ring(10f, 0.6f, 0.6f, 0), new Ring(19f, 0.6f, 0.6f, 0),
                new Ring(21f, 0.45f, 0.45f, 0), new Ring(24.5f, 0.02f, 0.02f, 0));
        crescent(skin, glow, m, n, light, 1f);
        crescent(skin, glow, m, n, light, -0.45f);   // smaller back blade (only one of skin/glow is set)
    }

    /** Barbed spear with a long diamond head. */
    static void spear(VertexConsumer vc, boolean glowPass, Matrix4f m, Matrix3f n, int light) {
        VertexConsumer skin = glowPass ? null : vc, glow = glowPass ? vc : null;
        if (!glowPass) loft(skin, m, n, light, false, new Ring(-3.5f, 0.55f, 0.55f, 0), new Ring(16.5f, 0.45f, 0.45f, 0),
                new Ring(17f, 0.85f, 0.85f, 0), new Ring(19f, 0.85f, 0.85f, 0), new Ring(19.4f, 0.5f, 0.5f, 0));
        Ring[] head = {
                new Ring(19.4f, 0.5f, 0.32f, 0), new Ring(20.4f, 2.1f, 0.3f, 0), new Ring(21.0f, 1.1f, 0.3f, 0),
                new Ring(22.5f, 1.45f, 0.3f, 0), new Ring(26f, 0.75f, 0.2f, 0), new Ring(29f, 0.02f, 0.02f, 0)};
        if (glowPass) edges(glow, m, n, head);
        else loft(skin, m, n, light, true, head);
    }

    // ------------------------------------------------------------------ geometry

    /** Lofts rings along Y. diamond = sharp-edged blade cross-section, otherwise round. */
    private static void loft(VertexConsumer vc, Matrix4f m, Matrix3f n, int light, boolean diamond, Ring... rings) {
        for (int i = 0; i < rings.length - 1; i++) {
            Ring a = rings[i], b = rings[i + 1];
            float v0 = i / (float) (rings.length - 1), v1 = (i + 1) / (float) (rings.length - 1);
            for (int j = 0; j < SIDES; j++) {
                float t0 = (float) (Math.PI * 2 * j / SIDES), t1 = (float) (Math.PI * 2 * (j + 1) / SIDES);
                float u0 = j / (float) SIDES, u1 = (j + 1) / (float) SIDES;
                ringVertex(vc, m, n, a, t0, diamond, u0, v0, light);
                ringVertex(vc, m, n, b, t0, diamond, u0, v1, light);
                ringVertex(vc, m, n, b, t1, diamond, u1, v1, light);
                ringVertex(vc, m, n, a, t1, diamond, u1, v0, light);
            }
        }
    }

    private static void ringVertex(VertexConsumer vc, Matrix4f m, Matrix3f n, Ring r, float t, boolean diamond,
                                   float u, float v, int light) {
        float c = (float) Math.cos(t), s = (float) Math.sin(t);
        float px, pz;
        if (diamond) {
            float k = Math.abs(c) + Math.abs(s);
            px = r.hw * c / k;
            pz = r.ht * s / k;
        } else {
            px = r.hw * c;
            pz = r.ht * s;
        }
        float nx = c / Math.max(0.05f, r.hw), nz = s / Math.max(0.05f, r.ht);
        float nl = (float) Math.sqrt(nx * nx + nz * nz);
        vertex(vc, m, n, r.x + px, r.y, pz, u, v, nx / nl, 0, nz / nl, light);
    }

    /** A short bar along X (crossguard), its ends swept upward. */
    private static void loftX(VertexConsumer vc, Matrix4f m, Matrix3f n, int light, float x0, float x1, float y, float hh, float ht) {
        int segs = 8;
        for (int i = 0; i < segs; i++) {
            float fa = i / (float) segs, fb = (i + 1) / (float) segs;
            float xa = x0 + (x1 - x0) * fa, xb = x0 + (x1 - x0) * fb;
            float ya = y + 0.12f * xa * xa, yb = y + 0.12f * xb * xb;
            float ka = 1f - 0.5f * Math.abs(fa * 2 - 1), kb = 1f - 0.5f * Math.abs(fb * 2 - 1);
            for (int j = 0; j < SIDES; j++) {
                float t0 = (float) (Math.PI * 2 * j / SIDES), t1 = (float) (Math.PI * 2 * (j + 1) / SIDES);
                barVertex(vc, m, n, xa, ya, hh * ka, ht * ka, t0, fa, j / (float) SIDES, light);
                barVertex(vc, m, n, xb, yb, hh * kb, ht * kb, t0, fb, j / (float) SIDES, light);
                barVertex(vc, m, n, xb, yb, hh * kb, ht * kb, t1, fb, (j + 1) / (float) SIDES, light);
                barVertex(vc, m, n, xa, ya, hh * ka, ht * ka, t1, fa, (j + 1) / (float) SIDES, light);
            }
        }
    }

    private static void barVertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float hh, float ht,
                                  float t, float v, float u, int light) {
        float c = (float) Math.cos(t), s = (float) Math.sin(t);
        vertex(vc, m, n, x, y + hh * c, ht * s, u, v, 0, c, s, light);
    }

    /** Crescent axe blade on the +X side (dir = 1) or a smaller one on -X (dir < 0, scaled). */
    private static void crescent(VertexConsumer skin, VertexConsumer glow, Matrix4f m, Matrix3f n, int light, float dir) {
        int segs = 14;
        float scale = Math.abs(dir), sign = Math.signum(dir);
        float cx = 3.0f * scale, cy = 14.5f, rad = 5.5f * scale;
        for (int i = 0; i < segs; i++) {
            float ta = i / (float) segs, tb = (i + 1) / (float) segs;
            float[] ia = {0.55f * sign, 12f + 5f * ta}, ib = {0.55f * sign, 12f + 5f * tb};
            float aa = (float) Math.toRadians(-72 + 144 * ta), ab = (float) Math.toRadians(-72 + 144 * tb);
            float[] oa = {sign * (cx + rad * (float) Math.cos(aa)), cy + rad * (float) Math.sin(aa)};
            float[] ob = {sign * (cx + rad * (float) Math.cos(ab)), cy + rad * (float) Math.sin(ab)};
            float th = 0.55f * scale + 0.1f;
            for (int side = -1; side <= 1 && skin != null; side += 2) {
                float z = side * th, zEdge = side * 0.03f;
                // inner half (thick) and outer half (tapering to the sharp edge)
                float[] ma = {(ia[0] + oa[0]) * 0.5f, (ia[1] + oa[1]) * 0.5f}, mb = {(ib[0] + ob[0]) * 0.5f, (ib[1] + ob[1]) * 0.5f};
                quad(skin, m, n, ia, z, ib, z, mb, z * 0.8f, ma, z * 0.8f, side, light, ta, tb);
                quad(skin, m, n, ma, z * 0.8f, mb, z * 0.8f, ob, zEdge, oa, zEdge, side, light, ta, tb);
            }
            // glowing cutting edge
            if (glow != null) edgeStrip(glow, m, n, oa, ob, sign);
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, float[] a, float za, float[] b, float zb,
                             float[] c, float zc, float[] d, float zd, int side, int light, float v0, float v1) {
        vertex(vc, m, n, a[0], a[1], za, 0f, v0, 0, 0, side, light);
        vertex(vc, m, n, b[0], b[1], zb, 0f, v1, 0, 0, side, light);
        vertex(vc, m, n, c[0], c[1], zc, 1f, v1, 0, 0, side, light);
        vertex(vc, m, n, d[0], d[1], zd, 1f, v0, 0, 0, side, light);
    }

    /** Thin glowing strips along both cutting edges of a lofted blade. */
    private static void edges(VertexConsumer vc, Matrix4f m, Matrix3f n, Ring[] rings) {
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < rings.length - 1; i++) {
                Ring a = rings[i], b = rings[i + 1];
                float xa = a.x + side * a.hw, xb = b.x + side * b.hw;
                float w = 0.22f;
                for (int face = -1; face <= 1; face += 2) {
                    vertex(vc, m, n, xa, a.y, 0, 0, 0, 0, 0, face, FULL);
                    vertex(vc, m, n, xb, b.y, 0, 0, 1, 0, 0, face, FULL);
                    vertex(vc, m, n, xb - side * w, b.y, face * 0.05f, 1, 1, 0, 0, face, FULL);
                    vertex(vc, m, n, xa - side * w, a.y, face * 0.05f, 1, 0, 0, 0, face, FULL);
                }
            }
        }
    }

    private static void edgeStrip(VertexConsumer vc, Matrix4f m, Matrix3f n, float[] a, float[] b, float sign) {
        float w = 0.3f * sign;
        for (int face = -1; face <= 1; face += 2) {
            vertex(vc, m, n, a[0], a[1], 0, 0, 0, 0, 0, face, FULL);
            vertex(vc, m, n, b[0], b[1], 0, 0, 1, 0, 0, face, FULL);
            vertex(vc, m, n, b[0] - w, b[1], face * 0.05f, 1, 1, 0, 0, face, FULL);
            vertex(vc, m, n, a[0] - w, a[1], face * 0.05f, 1, 0, 0, 0, face, FULL);
        }
    }

    private static final int FULL = 0xF000F0;

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z,
                               float u, float v, float nx, float ny, float nz, int light) {
        vc.vertex(m, x, y, z).color(1f, 1f, 1f, 1f).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(n, nx, ny, nz).endVertex();
    }

    private WeaponMeshes() {}
}
