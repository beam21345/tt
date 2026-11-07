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

/** Draws the flat, glowing ground models: voodoo sigil, expanding wave ring, shadow rift and domain. */
public class ShadowFxRenderer extends EntityRenderer<ShadowFxEntity> {
    private static final ResourceLocation SIGIL = tex("sigil");
    private static final ResourceLocation RING = tex("ring");
    private static final ResourceLocation RIFT = tex("rift");
    private static final ResourceLocation DOMAIN = tex("domain");
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
        float size = e.size();

        ps.pushPose();
        ps.translate(0, 0.03, 0);
        switch (e.variant()) {
            case ShadowFxEntity.SIGIL -> {
                ps.mulPose(Axis.YP.rotation(age * 0.04f));
                float r = size * (0.3f + 0.7f * growth);
                quad(ps, buffers.getBuffer(RenderType.entityTranslucent(SIGIL)), -r, -r, r, r, 0, 0, 1, 1, alpha);
            }
            case ShadowFxEntity.RING -> {
                float r = Math.max(0.2f, size * growth);
                float a = alpha * (1f - 0.6f * growth);
                quad(ps, buffers.getBuffer(RenderType.entityTranslucent(RING)), -r, -r, r, r, 0, 0, 1, 1, a);
                ps.translate(0, 0.02, 0);
                float r2 = r * 0.7f;
                quad(ps, buffers.getBuffer(RenderType.entityTranslucent(RING)), -r2, -r2, r2, r2, 0, 0, 1, 1, a * 0.7f);
            }
            case ShadowFxEntity.RIFT -> {
                ps.mulPose(Axis.YP.rotationDegrees(-e.yawDeg()));
                float length = size * growth;
                VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(RIFT));
                float w = 1.1f;
                for (float z = 0; z < length; z += 1f) {
                    float z1 = Math.min(length, z + 1f);
                    float wobble = (float) Math.sin(z * 1.9f) * 0.15f;
                    quad(ps, vc, -w + wobble, z, w + wobble, z1, 0, 0, 1, z1 - z, alpha);
                }
            }
            case ShadowFxEntity.DOMAIN -> {
                float h = size * (0.2f + 0.8f * growth);
                quad(ps, buffers.getBuffer(RenderType.entityTranslucent(DOMAIN)), -h, -h, h, h, 0, 0, 1, 1, alpha);
                ps.translate(0, 0.02, 0);
                ps.mulPose(Axis.YP.rotation(age * 0.02f));
                float r = h * 0.8f;
                quad(ps, buffers.getBuffer(RenderType.entityTranslucent(SIGIL)), -r, -r, r, r, 0, 0, 1, 1, alpha * 0.9f);
            }
            default -> {}
        }
        ps.popPose();
        super.render(e, entityYaw, partialTick, ps, buffers, light);
    }

    /** Horizontal textured quad from (x0,z0) to (x1,z1), drawn full-bright so the green glows. */
    private static void quad(PoseStack ps, VertexConsumer vc, float x0, float z0, float x1, float z1,
                             float u0, float v0, float u1, float v1, float alpha) {
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        vertex(vc, m, n, x0, z0, u0, v0, alpha);
        vertex(vc, m, n, x0, z1, u0, v1, alpha);
        vertex(vc, m, n, x1, z1, u1, v1, alpha);
        vertex(vc, m, n, x1, z0, u1, v0, alpha);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float z, float u, float v, float alpha) {
        vc.vertex(m, x, 0f, z)
                .color(1f, 1f, 1f, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(FULL_BRIGHT)
                .normal(n, 0f, 1f, 0f)
                .endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(ShadowFxEntity entity) {
        return SIGIL;
    }
}
