package com.alastor.staff.client;

import com.alastor.staff.AlastorStaffMod;
import com.alastor.staff.entity.ShadowFxEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Random;

/** Glowing effect models: voodoo sigil, expanding wave, shadow rift, circular domain and light pillar. */
public class ShadowFxRenderer extends EntityRenderer<ShadowFxEntity> {
    private static final ResourceLocation SIGIL = tex("sigil");
    private static final ResourceLocation RING = tex("ring");
    private static final ResourceLocation RIFT = tex("rift");
    private static final ResourceLocation DOMAIN = tex("domain");
    private static final ResourceLocation PILLAR = tex("pillar");
    private static final ResourceLocation POOL = tex("glow_pool");
    private static final int FULL_BRIGHT = LightTexture.FULL_BRIGHT;

    public ShadowFxRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(AlastorStaffMod.MODID, "textures/fx/" + name + ".png");
    }

    @Override
    public void render(ShadowFxEntity e, float entityYaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
        float age = e.tickCount + partialTick;
        int life = e.life();
        float alpha = Math.min(1f, age / 4f) * Math.min(1f, (life - age) / 8f);
        if (alpha <= 0.01f) return;
        float growth = Math.min(1f, age / e.grow());
        float ease = 1f - (1f - growth) * (1f - growth) * (1f - growth); // ease-out
        float size = e.size();
        float pulse = 0.85f + 0.15f * (float) Math.sin(age * 0.5f);

        ps.pushPose();
        ps.translate(0, 0.03, 0);
        switch (e.variant()) {
            case ShadowFxEntity.SIGIL -> {
                float r = size * (0.2f + 0.8f * ease) * (1f + 0.25f * (1f - growth));
                ps.pushPose();
                ps.mulPose(Axis.YP.rotation(age * 0.05f));
                flat(ps, buffers.getBuffer(RenderType.entityTranslucent(POOL)), r * 1.25f, alpha * 0.55f * pulse);
                ps.translate(0, 0.01, 0);
                flat(ps, buffers.getBuffer(RenderType.entityTranslucent(SIGIL)), r, alpha);
                ps.popPose();
                ps.translate(0, 0.03, 0);
                ps.mulPose(Axis.YP.rotation(-age * 0.09f));
                flat(ps, buffers.getBuffer(RenderType.entityTranslucent(SIGIL)), r * 0.55f, alpha * 0.9f);
                // a second, floating sigil that rises a little while it is active
                ps.translate(0, 0.25f + 0.35f * ease, 0);
                ps.mulPose(Axis.YP.rotation(age * 0.14f));
                flat(ps, buffers.getBuffer(RenderType.entityTranslucent(SIGIL)), r * 0.35f, alpha * 0.6f * pulse);
            }
            case ShadowFxEntity.RING -> {
                float r = Math.max(0.2f, size * ease);
                float a = alpha * (1f - 0.55f * growth);
                VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(RING));
                flat(ps, vc, r, a);
                ps.translate(0, 0.02, 0);
                flat(ps, vc, r * 0.82f, a * 0.6f);
                // standing wall of light on the wave front
                cylinder(ps, buffers.getBuffer(RenderType.entityTranslucent(PILLAR)), r, 1.6f * (1f - growth) + 0.3f, a * 0.8f, 0f);
            }
            case ShadowFxEntity.RIFT -> {
                ps.mulPose(Axis.YP.rotationDegrees(-e.yawDeg()));
                float length = size * ease;
                VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(RIFT));
                for (float z = 0; z < length; z += 1f) {
                    float z1 = Math.min(length, z + 1f);
                    float w = 3.2f + 0.4f * (float) Math.sin(z * 1.3f + age * 0.3f);
                    float wobble = (float) Math.sin(z * 1.9f) * 0.25f;
                    quad(ps, vc, -w + wobble, z, w + wobble, z1, 0, 0, 1, z1 - z, alpha);
                }
                // glowing seam of light rising out of the crack
                VertexConsumer beam = buffers.getBuffer(RenderType.entityTranslucent(PILLAR));
                for (float z = 0; z < length; z += 1f) {
                    float z1 = Math.min(length, z + 1f);
                    float h = 2.0f + 1.0f * (float) Math.sin(z * 2.1f + age * 0.4f);
                    wall(ps, beam, (float) Math.sin(z * 1.9f) * 0.25f, z, z1, h, alpha * 0.8f);
                }
            }
            case ShadowFxEntity.DOMAIN -> {
                float r = size * (0.15f + 0.85f * ease);
                flat(ps, buffers.getBuffer(RenderType.entityTranslucent(DOMAIN)), r, alpha);
                ps.translate(0, 0.02, 0);
                ps.pushPose();
                ps.mulPose(Axis.YP.rotation(age * 0.02f));
                flat(ps, buffers.getBuffer(RenderType.entityTranslucent(SIGIL)), r * 0.85f, alpha * 0.9f);
                ps.popPose();
                ps.translate(0, 0.02, 0);
                ps.mulPose(Axis.YP.rotation(-age * 0.04f));
                flat(ps, buffers.getBuffer(RenderType.entityTranslucent(SIGIL)), r * 0.4f, alpha * pulse);
                // curtain of green light around the edge
                cylinder(ps, buffers.getBuffer(RenderType.entityTranslucent(PILLAR)), r, 2.5f * ease, alpha * 0.55f * pulse, age * 0.01f);
            }
            case ShadowFxEntity.PILLAR -> {
                float h = (12f + size * 7f) * ease;
                float r = size * (1.25f - 0.35f * growth) * pulse;
                VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(PILLAR));
                cylinder(ps, vc, r * 1.6f, h * 0.7f, alpha * 0.45f, age * 0.05f);
                cylinder(ps, vc, r, h, alpha, age * 0.08f);
                cylinder(ps, vc, r * 0.45f, h * 1.1f, alpha, -age * 0.12f);
                flat(ps, buffers.getBuffer(RenderType.entityTranslucent(POOL)), r * 3f, alpha * pulse);
            }
            case ShadowFxEntity.BOLT -> {
                Random rnd = new Random(e.getId() * 341873128712L + (long) (age / 2));
                float flicker = alpha * (0.65f + 0.35f * rnd.nextFloat());
                VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
                bolt(ps.last().pose(), vc, new Vector3f(), new Vector3f(e.ex(), e.ey(), e.ez()), rnd, 0, e.red(), flicker);
            }
            default -> {}
        }
        ps.popPose();
        super.render(e, entityYaw, partialTick, ps, buffers, light);
    }

    // ---------------- geometry helpers ----------------

    /** Jagged lightning: glowing outer band + bright core, with a few side branches. */
    private static void bolt(Matrix4f m, VertexConsumer vc, Vector3f a, Vector3f b, Random rnd, int depth, boolean red, float alpha) {
        Vector3f d = new Vector3f(b).sub(a);
        float len = d.length();
        if (len < 0.05f) return;
        Vector3f dir = new Vector3f(d).div(len);
        Vector3f p1 = new Vector3f(dir).cross(0, 1, 0);
        if (p1.lengthSquared() < 1.0E-4f) p1.set(1, 0, 0);
        p1.normalize();
        Vector3f p2 = new Vector3f(dir).cross(p1).normalize();
        int n = Math.max(3, (int) (len / 0.7f));
        float jag = Math.min(1.2f, len * 0.09f);
        Vector3f prev = new Vector3f(a);
        float wGlow = (depth == 0 ? 0.32f : 0.18f), wCore = (depth == 0 ? 0.08f : 0.05f);
        for (int i = 1; i <= n; i++) {
            Vector3f next = new Vector3f(dir).mul(len * i / n).add(a);
            if (i < n) {
                next.add(new Vector3f(p1).mul((rnd.nextFloat() - 0.5f) * 2 * jag))
                    .add(new Vector3f(p2).mul((rnd.nextFloat() - 0.5f) * 2 * jag));
            }
            if (red) {
                segment(m, vc, prev, next, wGlow, 1.0f, 0.15f, 0.1f, alpha * 0.55f);
                segment(m, vc, prev, next, wCore, 1.0f, 0.75f, 0.7f, alpha);
            } else {
                segment(m, vc, prev, next, wGlow, 0.2f, 1.0f, 0.4f, alpha * 0.55f);
                segment(m, vc, prev, next, wCore, 0.8f, 1.0f, 0.85f, alpha);
            }
            if (depth == 0 && i < n && rnd.nextFloat() < 0.28f) {
                Vector3f branchEnd = new Vector3f(next)
                        .add(new Vector3f(dir).mul(len * 0.15f))
                        .add(new Vector3f(p1).mul((rnd.nextFloat() - 0.5f) * len * 0.5f))
                        .add(new Vector3f(p2).mul((rnd.nextFloat() - 0.5f) * len * 0.5f));
                bolt(m, vc, next, branchEnd, rnd, 1, red, alpha * 0.8f);
            }
            prev = next;
        }
    }

    /** One bolt segment as two crossed, double-sided quads. */
    private static void segment(Matrix4f m, VertexConsumer vc, Vector3f a, Vector3f b, float w,
                                float r, float g, float bl, float alpha) {
        Vector3f d = new Vector3f(b).sub(a);
        if (d.lengthSquared() < 1.0E-6f) return;
        d.normalize();
        Vector3f q1 = new Vector3f(d).cross(0, 1, 0);
        if (q1.lengthSquared() < 1.0E-4f) q1.set(1, 0, 0);
        q1.normalize().mul(w);
        Vector3f q2 = new Vector3f(d).cross(q1).normalize().mul(w);
        for (Vector3f q : new Vector3f[]{q1, q2}) {
            Vector3f a0 = new Vector3f(a).add(q), a1 = new Vector3f(a).sub(q);
            Vector3f b0 = new Vector3f(b).add(q), b1 = new Vector3f(b).sub(q);
            Vector3f[] quad = {a0, a1, b1, b0, b0, b1, a1, a0}; // front + back
            for (Vector3f v : quad) vc.vertex(m, v.x, v.y, v.z).color(r, g, bl, alpha).endVertex();
        }
    }

    private static void flat(PoseStack ps, VertexConsumer vc, float r, float alpha) {
        quad(ps, vc, -r, -r, r, r, 0, 0, 1, 1, alpha);
    }

    /** Horizontal textured quad from (x0,z0) to (x1,z1). */
    private static void quad(PoseStack ps, VertexConsumer vc, float x0, float z0, float x1, float z1,
                             float u0, float v0, float u1, float v1, float alpha) {
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        vertex(vc, m, n, x0, 0, z0, u0, v0, alpha, 0, 1, 0);
        vertex(vc, m, n, x0, 0, z1, u0, v1, alpha, 0, 1, 0);
        vertex(vc, m, n, x1, 0, z1, u1, v1, alpha, 0, 1, 0);
        vertex(vc, m, n, x1, 0, z0, u1, v0, alpha, 0, 1, 0);
    }

    /** Vertical strip along z (x fixed) – used for the light seam of the rift. */
    private static void wall(PoseStack ps, VertexConsumer vc, float x, float z0, float z1, float h, float alpha) {
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        vertex(vc, m, n, x, 0, z0, 0, 1, alpha, 1, 0, 0);
        vertex(vc, m, n, x, h, z0, 0, 0, alpha, 1, 0, 0);
        vertex(vc, m, n, x, h, z1, 1, 0, alpha, 1, 0, 0);
        vertex(vc, m, n, x, 0, z1, 1, 1, alpha, 1, 0, 0);
    }

    /** Open cylinder of light (no caps), texture v runs bottom (1) to top (0). */
    private static void cylinder(PoseStack ps, VertexConsumer vc, float r, float h, float alpha, float spin) {
        if (h <= 0.01f || r <= 0.01f) return;
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        int sides = 24;
        for (int i = 0; i < sides; i++) {
            double a0 = spin + Math.PI * 2 * i / sides, a1 = spin + Math.PI * 2 * (i + 1) / sides;
            float x0 = (float) Math.cos(a0) * r, z0 = (float) Math.sin(a0) * r;
            float x1 = (float) Math.cos(a1) * r, z1 = (float) Math.sin(a1) * r;
            float u0 = (float) i / sides, u1 = (float) (i + 1) / sides;
            float nx = (float) Math.cos((a0 + a1) / 2), nz = (float) Math.sin((a0 + a1) / 2);
            vertex(vc, m, n, x0, 0, z0, u0, 1, alpha, nx, 0, nz);
            vertex(vc, m, n, x0, h, z0, u0, 0, alpha, nx, 0, nz);
            vertex(vc, m, n, x1, h, z1, u1, 0, alpha, nx, 0, nz);
            vertex(vc, m, n, x1, 0, z1, u1, 1, alpha, nx, 0, nz);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z,
                               float u, float v, float alpha, float nx, float ny, float nz) {
        vc.vertex(m, x, y, z)
                .color(1f, 1f, 1f, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(FULL_BRIGHT)
                .normal(n, nx, ny, nz)
                .endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(ShadowFxEntity entity) {
        return SIGIL;
    }
}
