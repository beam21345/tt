package com.alastor.staff.client;

import com.alastor.staff.entity.HurledBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/** Draws the torn-up chunk of ground as a big tumbling block. */
public class HurledBlockRenderer extends EntityRenderer<HurledBlockEntity> {
    public HurledBlockRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.8f;
    }

    @Override
    public void render(HurledBlockEntity e, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
        float age = e.tickCount + partialTick;
        float s = e.size();
        ps.pushPose();
        if (e.thrown()) {
            ps.mulPose(Axis.XP.rotationDegrees(age * 18f));
            ps.mulPose(Axis.ZP.rotationDegrees(age * 11f));
        } else {
            ps.mulPose(Axis.YP.rotationDegrees(age * 4f));
            ps.mulPose(Axis.XP.rotationDegrees((float) Math.sin(age * 0.3) * 8f));
        }
        ps.scale(s, s, s);
        ps.translate(-0.5, -0.5, -0.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(e.blockState(), ps, buffers, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
        super.render(e, yaw, partialTick, ps, buffers, light);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureLocation(HurledBlockEntity e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
