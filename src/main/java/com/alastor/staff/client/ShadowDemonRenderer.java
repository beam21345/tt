package com.alastor.staff.client;

import com.alastor.staff.AlastorStaffMod;
import com.alastor.staff.entity.ShadowDemonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/** Tall black humanoid shadow with glowing green eyes and grin. */
public class ShadowDemonRenderer extends HumanoidMobRenderer<ShadowDemonEntity, HumanoidModel<ShadowDemonEntity>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(AlastorStaffMod.MODID, "textures/entity/shadow_demon.png");
    private static final ResourceLocation GLOW =
            new ResourceLocation(AlastorStaffMod.MODID, "textures/entity/shadow_demon_glow.png");

    public ShadowDemonRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
        this.addLayer(new GlowLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(ShadowDemonEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(ShadowDemonEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(1.1f, 1.15f, 1.1f);
    }

    static class GlowLayer extends EyesLayer<ShadowDemonEntity, HumanoidModel<ShadowDemonEntity>> {
        private static final RenderType TYPE = RenderType.eyes(GLOW);

        GlowLayer(RenderLayerParent<ShadowDemonEntity, HumanoidModel<ShadowDemonEntity>> parent) {
            super(parent);
        }

        @Override
        public RenderType renderType() {
            return TYPE;
        }
    }
}
