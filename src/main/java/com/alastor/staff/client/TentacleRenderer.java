package com.alastor.staff.client;

import com.alastor.staff.AlastorStaffMod;
import com.alastor.staff.entity.TentacleEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Glossy pure-black shadow tentacle. Built from many short segments, each an octagonal prism
 * (two cubes twisted 45°), following a curve with a wave travelling up the body and a curling tip.
 * Only the ground under it glows green while it bursts out.
 */
public class TentacleRenderer extends EntityRenderer<TentacleEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(AlastorStaffMod.MODID, "shadow_tentacle"), "main");
    private static final ResourceLocation TEX = new ResourceLocation(AlastorStaffMod.MODID, "textures/entity/tentacle.png");
    private static final ResourceLocation POOL = new ResourceLocation(AlastorStaffMod.MODID, "textures/fx/glow_pool.png");
    private static final int SEGMENTS = 30;

    private final ModelPart segment;
    private final ModelPart tipPart;

    public TentacleRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        ModelPart root = ctx.bakeLayer(LAYER);
        this.segment = root.getChild("segment");
        this.tipPart = root.getChild("tip");
        this.shadowRadius = 0.8f;
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("segment",
                CubeListBuilder.create().texOffs(0, 0).addBox(-8f, 0f, -8f, 16f, 16f, 16f), PartPose.ZERO);
        mesh.getRoot().addOrReplaceChild("tip",
                CubeListBuilder.create()
                        .texOffs(0, 32).addBox(-6f, 0f, -6f, 12f, 10f, 12f)
                        .texOffs(48, 32).addBox(-2f, 10f, -2f, 4f, 8f, 4f), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void render(TentacleEntity e, float entityYaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
        float age = e.tickCount + partialTick;
        float ext = e.extension(age);
        if (ext <= 0.01f) return;

        Vector3f tip = new Vector3f(e.lerpTipX(partialTick), e.lerpTipY(partialTick), e.lerpTipZ(partialTick));
        float len = tip.length();
        if (len < 0.05f) return;
        Vector3f dir = new Vector3f(tip).div(len);
        Vector3f side = new Vector3f(dir).cross(0, 1, 0);
        if (side.lengthSquared() < 1.0E-4f) side.set(1, 0, 0);
        side.normalize();
        Vector3f side2 = new Vector3f(dir).cross(side).normalize();

        float phase = e.phase();
        float thick = e.thickness();
        float amp = Math.min(1.8f, 0.13f * len);
        float curl = Math.min(2.2f, 0.16f * len);
        float reach = len * ext;

        // Centre line: a wave travels from root to tip, the last quarter curls over like a hook.
        Vector3f[] pts = new Vector3f[SEGMENTS + 1];
        for (int i = 0; i <= SEGMENTS; i++) {
            float s = (float) i / SEGMENTS;
            float env = (float) Math.pow(s, 1.3);
            float wave = (float) Math.sin(s * Math.PI * 2.0 - age * 0.22 + phase) * amp * env;
            float wave2 = (float) Math.cos(s * Math.PI * 1.4 - age * 0.15 + phase * 1.7) * amp * 0.6f * env;
            float hook = s > 0.7f ? (float) Math.pow((s - 0.7f) / 0.3f, 2) : 0f;
            float curlNow = curl * hook * (0.75f + 0.25f * (float) Math.sin(age * 0.12 + phase));
            pts[i] = new Vector3f(dir).mul(reach * s - curlNow * 0.35f * hook)
                    .add(new Vector3f(side).mul(wave + curlNow))
                    .add(new Vector3f(side2).mul(wave2));
        }

        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEX));
        for (int i = 0; i < SEGMENTS; i++) {
            Vector3f a = pts[i];
            Vector3f d = new Vector3f(pts[i + 1]).sub(a);
            float segLen = d.length();
            if (segLen < 1.0E-4f) continue;
            d.div(segLen);
            float s = (float) i / SEGMENTS;
            // thick muscular root, long elegant taper, a slow pulse running up the body
            float taper = 1.0f - 0.82f * (float) Math.pow(s, 0.85);
            float pulse = 1.0f + 0.08f * (float) Math.sin(i * 0.9f - age * 0.4f);
            float w = thick * taper * pulse;

            ps.pushPose();
            ps.translate(a.x, a.y, a.z);
            ps.mulPose(new Quaternionf().rotationTo(0f, 1f, 0f, d.x, d.y, d.z));
            if (i == SEGMENTS - 1) {
                ps.scale(w * 1.15f, segLen * 1.7f, w * 1.15f);
                tipPart.render(ps, vc, light, OverlayTexture.NO_OVERLAY);
                ps.mulPose(Axis.YP.rotationDegrees(45f));
                tipPart.render(ps, vc, light, OverlayTexture.NO_OVERLAY);
            } else {
                ps.scale(w, segLen * 1.15f, w);
                segment.render(ps, vc, light, OverlayTexture.NO_OVERLAY);
                ps.mulPose(Axis.YP.rotationDegrees(45f)); // second cube twisted 45° = rounded octagon
                segment.render(ps, vc, light, OverlayTexture.NO_OVERLAY);
            }
            ps.popPose();
        }

        // green light on the ground only while it bursts out
        float glow = e.glow(age);
        if (glow > 0.01f) {
            float r = 1.2f + thick * 1.8f;
            groundPool(ps, buffers.getBuffer(RenderType.entityTranslucent(POOL)), r, glow);
        }
        super.render(e, entityYaw, partialTick, ps, buffers, light);
    }

    private static void groundPool(PoseStack ps, VertexConsumer vc, float r, float alpha) {
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float y = 0.04f;
        float[][] corners = {{-r, -r, 0, 0}, {-r, r, 0, 1}, {r, r, 1, 1}, {r, -r, 1, 0}};
        for (float[] k : corners) {
            vc.vertex(m, k[0], y, k[1]).color(1f, 1f, 1f, alpha).uv(k[2], k[3])
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                    .normal(n, 0f, 1f, 0f).endVertex();
        }
    }

    @Override
    public ResourceLocation getTextureLocation(TentacleEntity entity) {
        return TEX;
    }
}
