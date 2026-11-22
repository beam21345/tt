package com.alastor.staff.client;

import com.alastor.staff.entity.DebrisEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/** A tumbling chunk of a smashed block that shrinks away at the end of its life. */
public class DebrisRenderer extends EntityRenderer<DebrisEntity> {
    public DebrisRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.15f;
    }

    @Override
    public void render(DebrisEntity e, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
        float age = e.tickCount + partialTick;
        float left = e.life() - age;
        float s = e.size() * Math.min(1f, Math.max(0f, left / 8f));
        if (s <= 0.01f) return;
        boolean moving = e.getDeltaMovement().lengthSqr() > 1.0E-3;
        float spin = moving ? age : Math.min(age, e.life() * 0.4f);
        ps.pushPose();
        ps.translate(0, s * 0.5, 0);
        ps.mulPose(Axis.XP.rotationDegrees(spin * 23f + e.getId() * 37));
        ps.mulPose(Axis.ZP.rotationDegrees(spin * 17f + e.getId() * 53));
        ps.scale(s, s, s);
        ps.translate(-0.5, -0.5, -0.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(e.blockState(), ps, buffers, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
        super.render(e, yaw, partialTick, ps, buffers, light);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureLocation(DebrisEntity e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
