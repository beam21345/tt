package com.alastor.staff.client;

import com.alastor.staff.AlastorStaffMod;
import com.alastor.staff.entity.ShadowDemonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/** Shadow demon with glowing green eyes/grin, sized per demon, rising out of the ground when summoned. */
public class ShadowDemonRenderer extends MobRenderer<ShadowDemonEntity, ShadowDemonModel> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(AlastorStaffMod.MODID, "textures/entity/shadow_demon.png");
    private static final ResourceLocation GLOW =
            new ResourceLocation(AlastorStaffMod.MODID, "textures/entity/shadow_demon_glow.png");
    private static final int EMERGE_TICKS = 20;

    public ShadowDemonRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new ShadowDemonModel(ctx.bakeLayer(ShadowDemonModel.LAYER)), 0.6f);
        this.addLayer(new GlowLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(ShadowDemonEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(ShadowDemonEntity entity, PoseStack poseStack, float partialTick) {
        float size = entity.getDemonSize();
        float age = entity.tickCount + partialTick;
        if (age < EMERGE_TICKS) {
            // rise up out of the shadow (in this pose-stack space +Y points down)
            float t = age / EMERGE_TICKS;
            float sink = (1f - t) * (1f - t) * 2.6f;
            poseStack.translate(0, sink * size, 0);
        }
        poseStack.scale(size, size, size);
        this.shadowRadius = 0.6f * size;
    }

    static class GlowLayer extends EyesLayer<ShadowDemonEntity, ShadowDemonModel> {
        private static final RenderType TYPE = RenderType.eyes(GLOW);

        GlowLayer(RenderLayerParent<ShadowDemonEntity, ShadowDemonModel> parent) {
            super(parent);
        }

        @Override
        public RenderType renderType() {
            return TYPE;
        }
    }
}
