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
 * Glossy pure-black shadow tentacle: a smooth round tube following a spring-simulated spine
 * (so it moves with weight and its tip whips), with a wave travelling up the body and a curling tip.
 * Only the ground under it glows green while it bursts out.
 */
public class TentacleRenderer extends EntityRenderer<TentacleEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(AlastorStaffMod.MODID, "shadow_tentacle"), "main");
    private static final ResourceLocation TEX = new ResourceLocation(AlastorStaffMod.MODID, "textures/entity/tentacle.png");
    private static final ResourceLocation POOL = new ResourceLocation(AlastorStaffMod.MODID, "textures/fx/glow_pool.png");
    private static final ResourceLocation SHADOW = new ResourceLocation(AlastorStaffMod.MODID, "textures/fx/shadow.png");
    private static final ResourceLocation WEAPON_TEX = new ResourceLocation(AlastorStaffMod.MODID, "textures/entity/weapon.png");
    private static final ResourceLocation WEAPON_GLOW = new ResourceLocation(AlastorStaffMod.MODID, "textures/entity/weapon_glow.png");
    private static final int SEGMENTS = TentacleEntity.SEGMENTS;
    private static final int SIDES = 12;
    private static final ResourceLocation TUBE_TEX = new ResourceLocation(AlastorStaffMod.MODID, "textures/entity/tentacle_tube.png");

    private final ModelPart segment;
    private final ModelPart tipPart;
    private final ModelPart sword, axe, spear;

    public TentacleRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        ModelPart root = ctx.bakeLayer(LAYER);
        this.segment = root.getChild("segment");
        this.tipPart = root.getChild("tip");
        this.sword = root.getChild("sword");
        this.axe = root.getChild("axe");
        this.spear = root.getChild("spear");
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
        // Weapons grown out of the tentacle tip (built small, scaled up when rendered).
        mesh.getRoot().addOrReplaceChild("sword", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-0.6f, -3f, -0.6f, 1.2f, 3f, 1.2f)          // grip
                .addBox(-3.2f, 0f, -0.8f, 6.4f, 1f, 1.6f)           // crossguard
                .addBox(-1.5f, 1f, -0.3f, 3f, 16f, 0.6f)            // blade
                .addBox(-1f, 17f, -0.3f, 2f, 2f, 0.6f)              // tapering point
                .addBox(-0.5f, 19f, -0.3f, 1f, 2f, 0.6f), PartPose.ZERO);
        mesh.getRoot().addOrReplaceChild("axe", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-0.6f, -3f, -0.6f, 1.2f, 21f, 1.2f)         // haft
                .addBox(0.6f, 11f, -0.35f, 3f, 7f, 0.7f)            // blade body
                .addBox(3.6f, 9.5f, -0.3f, 2.2f, 10f, 0.6f)         // wide curved edge
                .addBox(-3.2f, 14f, -0.3f, 2.6f, 1.6f, 0.6f)        // back spike
                .addBox(-0.4f, 18f, -0.4f, 0.8f, 3f, 0.8f), PartPose.ZERO);
        mesh.getRoot().addOrReplaceChild("spear", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-0.45f, -3f, -0.45f, 0.9f, 22f, 0.9f)       // shaft
                .addBox(-1.6f, 18f, -0.3f, 3.2f, 1f, 0.6f)          // barbs
                .addBox(-1.2f, 19f, -0.3f, 2.4f, 3f, 0.6f)          // head
                .addBox(-0.7f, 22f, -0.3f, 1.4f, 2f, 0.6f)
                .addBox(-0.3f, 24f, -0.3f, 0.6f, 2f, 0.6f), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void render(TentacleEntity e, float entityYaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
        float age = e.tickCount + partialTick;
        float ext = e.extension(age);
        if (ext <= 0.01f || !e.hasSpine()) return;
        float thick = e.thickness();

        // spine points from the spring simulation (weighted, whipping motion)
        Vector3f[] pts = new Vector3f[SEGMENTS + 1];
        for (int i = 0; i <= SEGMENTS; i++) {
            pts[i] = new Vector3f(e.spineX(i, partialTick), e.spineY(i, partialTick), e.spineZ(i, partialTick));
        }
        if (new Vector3f(pts[SEGMENTS]).lengthSquared() < 0.01f) return;

        tube(ps, buffers.getBuffer(RenderType.entityCutoutNoCull(TUBE_TEX)), pts, thick, age, light);

        // weapon carried at the tip, aligned with the last segment
        int weapon = e.weapon();
        if (weapon != TentacleEntity.PLAIN) {
            ModelPart part = weapon == TentacleEntity.SWORD ? sword : weapon == TentacleEntity.AXE ? axe : spear;
            Vector3f end = pts[SEGMENTS];
            Vector3f wd = new Vector3f(end).sub(pts[SEGMENTS - 2]);
            if (wd.lengthSquared() > 1.0E-6f) {
                wd.normalize();
                float k = Math.min(1f, ext) * thick * 2.3f; // giant: ~8 blocks long at thickness 2.2
                for (int pass = 0; pass < 2; pass++) {
                    VertexConsumer wv = buffers.getBuffer(pass == 0 ? RenderType.entityCutoutNoCull(WEAPON_TEX) : RenderType.eyes(WEAPON_GLOW));
                    ps.pushPose();
                    ps.translate(end.x, end.y, end.z);
                    ps.mulPose(new Quaternionf().rotationTo(0f, 1f, 0f, wd.x, wd.y, wd.z));
                    ps.mulPose(Axis.YP.rotation(e.phase()));
                    ps.scale(k, k, k);
                    part.render(ps, wv, pass == 0 ? light : LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                    ps.popPose();
                }
            }
        }

        // it rises out of a pool of darkness; a brief green flash only while bursting out
        float r = 1.0f + thick * 1.6f;
        groundPool(ps, buffers.getBuffer(RenderType.entityTranslucent(SHADOW)), r * (0.6f + 0.4f * Math.min(1f, ext)), Math.min(1f, ext * 1.5f) * 0.95f);
        float glow = e.glow(age);
        if (glow > 0.01f) {
            groundPool(ps, buffers.getBuffer(RenderType.entityTranslucent(POOL)), r * 0.8f, glow * 0.45f);
        }
        super.render(e, entityYaw, partialTick, ps, buffers, light);
    }

    /**
     * Smooth round tube along the spine: rings of vertices carried along with parallel-transport
     * frames (so it never twists), thick root, long taper to a fine point, a pulse running up it.
     */
    private static void tube(PoseStack ps, VertexConsumer vc, Vector3f[] pts, float thick, float age, int light) {
        int n = pts.length;
        Vector3f[] ringPos = new Vector3f[n];
        Vector3f[] nrm = new Vector3f[n];
        Vector3f[] bin = new Vector3f[n];
        float[] radius = new float[n];

        Vector3f t0 = new Vector3f(pts[1]).sub(pts[0]);
        if (t0.lengthSquared() < 1.0E-8f) t0.set(0, 1, 0);
        t0.normalize();
        Vector3f ref = Math.abs(t0.y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        Vector3f prevN = new Vector3f(ref).cross(t0).normalize();
        for (int i = 0; i < n; i++) {
            Vector3f t = new Vector3f(pts[Math.min(n - 1, i + 1)]).sub(pts[Math.max(0, i - 1)]);
            if (t.lengthSquared() < 1.0E-8f) t.set(t0);
            t.normalize();
            Vector3f nn = new Vector3f(prevN).sub(new Vector3f(t).mul(prevN.dot(t)));
            if (nn.lengthSquared() < 1.0E-8f) nn.set(new Vector3f(ref).cross(t));
            nn.normalize();
            prevN = nn;
            nrm[i] = nn;
            bin[i] = new Vector3f(t).cross(nn).normalize();
            float s = (float) i / (n - 1);
            float taper = 1.0f - 0.88f * (float) Math.pow(s, 0.8);   // thick root, long taper
            if (s > 0.9f) taper *= (1f - s) / 0.1f;                     // closing to a sharp point
            float pulse = 1.0f + 0.07f * (float) Math.sin(i * 0.8f - age * 0.4f);
            radius[i] = Math.max(0.01f, thick * 0.55f * taper * pulse);
            ringPos[i] = pts[i];
        }
        // sink the root a little into the shadow so it seems to come from below
        ringPos[0] = new Vector3f(pts[0]).sub(new Vector3f(pts[1]).sub(pts[0]).normalize().mul(0.4f));

        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f nm = pose.normal();
        for (int i = 0; i < n - 1; i++) {
            float v0 = (float) i / (n - 1), v1 = (float) (i + 1) / (n - 1);
            for (int j = 0; j < SIDES; j++) {
                float a0 = (float) (Math.PI * 2 * j / SIDES), a1 = (float) (Math.PI * 2 * (j + 1) / SIDES);
                float u0 = (float) j / SIDES, u1 = (float) (j + 1) / SIDES;
                tubeVertex(vc, m, nm, ringPos[i], nrm[i], bin[i], radius[i], a0, u0, v0, light);
                tubeVertex(vc, m, nm, ringPos[i + 1], nrm[i + 1], bin[i + 1], radius[i + 1], a0, u0, v1, light);
                tubeVertex(vc, m, nm, ringPos[i + 1], nrm[i + 1], bin[i + 1], radius[i + 1], a1, u1, v1, light);
                tubeVertex(vc, m, nm, ringPos[i], nrm[i], bin[i], radius[i], a1, u1, v0, light);
            }
        }
    }

    private static void tubeVertex(VertexConsumer vc, Matrix4f m, Matrix3f nm, Vector3f c, Vector3f n, Vector3f b,
                                   float r, float a, float u, float v, int light) {
        float cx = (float) Math.cos(a), sx = (float) Math.sin(a);
        float ox = n.x * cx + b.x * sx, oy = n.y * cx + b.y * sx, oz = n.z * cx + b.z * sx;
        vc.vertex(m, c.x + ox * r, c.y + oy * r, c.z + oz * r)
                .color(1f, 1f, 1f, 1f).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(nm, ox, oy, oz).endVertex();
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
