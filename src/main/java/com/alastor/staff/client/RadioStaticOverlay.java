package com.alastor.staff.client;

import com.alastor.staff.AlastorStaffMod;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.Random;

/**
 * When a big skill goes off nearby, the screen briefly crackles like a badly tuned old radio
 * broadcast: grainy static, rolling scanlines and a green tint.
 */
public final class RadioStaticOverlay {
    private static final ResourceLocation NOISE = new ResourceLocation(AlastorStaffMod.MODID, "textures/gui/static.png");
    private static final Random RND = new Random();
    private static float strength;
    private static long lastTick;

    public static void trigger(float s) {
        strength = Math.min(1f, Math.max(strength, s));
    }

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        if (now != lastTick) {
            strength = Math.max(0f, strength - 0.07f * (now - lastTick > 5 ? 10 : (now - lastTick)));
            lastTick = now;
        }
        if (strength <= 0.01f || mc.options.hideGui) return;
        float a = strength * strength;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // grain: the noise texture tiled with a fresh random offset every frame
        RenderSystem.setShaderColor(0.75f, 1f, 0.8f, 0.55f * a);
        int ox = RND.nextInt(128), oy = RND.nextInt(128);
        g.blit(NOISE, 0, 0, ox, oy, width, height, 128, 128);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        // rolling scanlines and a couple of torn horizontal bands
        int alpha = (int) (60 * a);
        for (int y = (int) (now % 4); y < height; y += 4) g.fill(0, y, width, y + 1, (alpha << 24) | 0x002810);
        for (int k = 0; k < 2; k++) {
            int y = RND.nextInt(Math.max(1, height));
            g.fill(0, y, width, y + 2 + RND.nextInt(4), ((int) (90 * a) << 24) | 0x3CFF78);
        }
        RenderSystem.disableBlend();
    }

    private RadioStaticOverlay() {}
}
