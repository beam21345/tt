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
                    float w = 1.7f + 0.25f * (float) Math.sin(z * 1.3f + age * 0.3f);
                    float wobble = (float) Math.sin(z * 1.9f) * 0.25f;
                    quad(ps, vc, -w + wobble, z, w + wobble, z1, 0, 0, 1, z1 - z, alpha);
                }
                // glowing seam of light rising out of the crack
                VertexConsumer beam = buffers.getBuffer(RenderType.entityTranslucent(PILLAR));
                for (float z = 0; z < length; z += 1f) {
                    float z1 = Math.min(length, z + 1f);
                    float h = 1.2f + 0.6f * (float) Math.sin(z * 2.1f + age * 0.4f);
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
                float h = 18f * ease;
                float r = size * (1.2f - 0.4f * growth);
                VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(PILLAR));
                cylinder(ps, vc, r, h, alpha, age * 0.08f);
                cylinder(ps, vc, r * 0.45f, h * 1.1f, alpha, -age * 0.12f);
                flat(ps, buffers.getBuffer(RenderType.entityTranslucent(POOL)), r * 3f, alpha * pulse);
            }
            default -> {}
        }
        ps.popPose();
        super.render(e, entityYaw, partialTick, ps, buffers, light);
    }

    // ---------------- geometry helpers ----------------

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
