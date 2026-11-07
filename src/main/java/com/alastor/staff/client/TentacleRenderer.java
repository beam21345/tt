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
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws a shadow tentacle as a chain of 3D segments. Each segment is a textured cube stretched
 * between two points of a swaying curve, thick at the root and thin at the tip, with glowing
 * green voodoo runes.
 */
public class TentacleRenderer extends EntityRenderer<TentacleEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(AlastorStaffMod.MODID, "shadow_tentacle"), "main");
    private static final ResourceLocation TEX = new ResourceLocation(AlastorStaffMod.MODID, "textures/entity/tentacle.png");
    private static final ResourceLocation GLOW = new ResourceLocation(AlastorStaffMod.MODID, "textures/entity/tentacle_glow.png");
    private static final int SEGMENTS = 16;

    private final ModelPart segment;
    private final ModelPart tipPart;

    public TentacleRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        ModelPart root = ctx.bakeLayer(LAYER);
        this.segment = root.getChild("segment");
        this.tipPart = root.getChild("tip");
        this.shadowRadius = 0.4f;
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        // A 1x1x1 block cube standing on its base; scaled per segment at render time.
        mesh.getRoot().addOrReplaceChild("segment",
                CubeListBuilder.create().texOffs(0, 0).addBox(-8f, 0f, -8f, 16f, 16f, 16f), PartPose.ZERO);
        // Pointed tip: a smaller cube on top of a thin spike.
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

        Vector3f tip = new Vector3f(e.tipX(), e.tipY(), e.tipZ());
        float len = tip.length();
        if (len < 0.05f) return;
        Vector3f dir = new Vector3f(tip).div(len);
        Vector3f side = new Vector3f(dir).cross(0, 1, 0);
        if (side.lengthSquared() < 1.0E-4f) side.set(1, 0, 0);
        side.normalize();
        Vector3f side2 = new Vector3f(dir).cross(side).normalize();

        float phase = e.phase();
        float thick = e.thickness();
        float amp = Math.min(0.9f, 0.12f * len);

        Vector3f[] pts = new Vector3f[SEGMENTS + 1];
        for (int i = 0; i <= SEGMENTS; i++) {
            float s = (float) i / SEGMENTS;
            float sway = (float) Math.sin(s * Math.PI * 1.8 + age * 0.13 + phase) * amp * s;
            float sway2 = (float) Math.cos(s * Math.PI * 1.3 + age * 0.09 + phase * 1.7) * amp * 0.7f * s;
            pts[i] = new Vector3f(dir).mul(len * s * ext)
                    .add(new Vector3f(side).mul(sway))
                    .add(new Vector3f(side2).mul(sway2));
        }

        drawPass(ps, buffers.getBuffer(RenderType.entityCutoutNoCull(TEX)), pts, thick, age, light);
        drawPass(ps, buffers.getBuffer(RenderType.eyes(GLOW)), pts, thick, age, light);
        super.render(e, entityYaw, partialTick, ps, buffers, light);
    }

    private void drawPass(PoseStack ps, VertexConsumer vc, Vector3f[] pts, float thick, float age, int light) {
        for (int i = 0; i < SEGMENTS; i++) {
            Vector3f a = pts[i];
            Vector3f d = new Vector3f(pts[i + 1]).sub(a);
            float segLen = d.length();
            if (segLen < 1.0E-4f) continue;
            d.div(segLen);
            float s = (float) i / SEGMENTS;
            float w = thick * (1.0f - 0.7f * s);

            ps.pushPose();
            ps.translate(a.x, a.y, a.z);
            ps.mulPose(new Quaternionf().rotationTo(0f, 1f, 0f, d.x, d.y, d.z));
            ps.mulPose(Axis.YP.rotation(age * 0.04f + i * 0.5f));
            if (i == SEGMENTS - 1) {
                ps.scale(w * 1.3f, segLen * 1.6f, w * 1.3f);
                tipPart.render(ps, vc, light, OverlayTexture.NO_OVERLAY);
            } else {
                ps.scale(w, segLen * 1.12f, w);
                segment.render(ps, vc, light, OverlayTexture.NO_OVERLAY);
            }
            ps.popPose();
        }
    }

    @Override
    public ResourceLocation getTextureLocation(TentacleEntity entity) {
        return TEX;
    }
}
