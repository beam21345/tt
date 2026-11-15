package com.alastor.staff.client;

import com.alastor.staff.AlastorStaffMod;
import com.alastor.staff.item.AlastorStaffItem;
import com.alastor.staff.skill.SkillType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * Skill bar shown on the right edge of the screen while the staff is held: six skill icons,
 * the selected one framed in green with its name, cooldowns shaded with seconds left,
 * and a pulsing frame while Radio Demon is active.
 */
public final class SkillHud {
    private static final int SLOT = 22;
    private static final int GREEN = 0xFF3CFF78;
    private static final int RED = 0xFFFF4A3C;

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || mc.options.hideGui || mc.level == null) return;
        ItemStack stack = p.getMainHandItem();
        if (!(stack.getItem() instanceof AlastorStaffItem)) {
            stack = p.getOffhandItem();
            if (!(stack.getItem() instanceof AlastorStaffItem)) return;
        }

        Font font = mc.font;
        long time = mc.level.getGameTime();
        float pulse = 0.5f + 0.5f * (float) Math.sin((time + partialTick) * 0.25);
        SkillType selected = AlastorStaffItem.getSkill(stack);
        boolean radioOn = AlastorStaffItem.isRadioOn(stack);

        SkillType[] skills = SkillType.values();
        int x = width - SLOT - 6;
        int y0 = height / 2 - (skills.length * SLOT) / 2;

        RenderSystem.enableBlend();
        for (int i = 0; i < skills.length; i++) {
            SkillType s = skills[i];
            int y = y0 + i * SLOT;
            boolean sel = s == selected;

            g.fill(x, y, x + SLOT - 2, y + SLOT - 2, sel ? 0xCC0C1A10 : 0x99000000);
            int frame = sel ? GREEN : 0x66306040;
            if (s == SkillType.RADIO_DEMON && radioOn) {
                int a = (int) (120 + 135 * pulse);
                frame = (a << 24) | 0x3CFF78;
            }
            border(g, x, y, SLOT - 2, frame);
            if (sel) border(g, x - 1, y - 1, SLOT, 0x553CFF78);

            ResourceLocation icon = new ResourceLocation(AlastorStaffMod.MODID, "textures/gui/" + s.id + ".png");
            g.blit(icon, x + 2, y + 2, 0, 0, 16, 16, 16, 16);

            long left = AlastorStaffItem.cooldownRemaining(stack, s, time);
            if (left > 0 && s != SkillType.RADIO_DEMON) {
                float f = Math.min(1f, left / (float) s.cooldown);
                int shade = (int) (16 * f);
                g.fill(x + 2, y + 2 + (16 - shade), x + 18, y + 18, 0xB0000000);
                String secs = String.valueOf((left + 19) / 20);
                g.drawString(font, secs, x + 19 - font.width(secs), y + 11, RED, true);
            }
            g.drawString(font, String.valueOf(i + 1), x + 2, y + 1, sel ? GREEN : 0xFF8A8A8A, true);

            if (sel) {
                Component name = s.displayName();
                int w = font.width(name);
                g.fill(x - w - 10, y + 4, x - 3, y + 16, 0xAA000000);
                g.drawString(font, name, x - w - 6, y + 6, GREEN, true);
            }
        }
        RenderSystem.disableBlend();
    }

    private static void border(GuiGraphics g, int x, int y, int size, int color) {
        g.fill(x, y, x + size, y + 1, color);
        g.fill(x, y + size - 1, x + size, y + size, color);
        g.fill(x, y, x + 1, y + size, color);
        g.fill(x + size - 1, y, x + size, y + size, color);
    }

    private SkillHud() {}
}
