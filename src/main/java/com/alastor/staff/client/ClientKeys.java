package com.alastor.staff.client;

import com.alastor.staff.AlastorStaffMod;
import com.alastor.staff.item.AlastorStaffItem;
import com.alastor.staff.network.CycleSkillPacket;
import com.alastor.staff.network.ModNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** R = next skill, Shift + R = previous skill (rebindable in Controls). */
@Mod.EventBusSubscriber(modid = AlastorStaffMod.MODID, value = Dist.CLIENT)
public final class ClientKeys {
    public static final KeyMapping CYCLE_SKILL = new KeyMapping("key.alastorstaff.cycle_skill",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.KEY_R, "key.categories.alastorstaff");

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (CYCLE_SKILL.consumeClick()) {
            if (mc.player == null) continue;
            boolean holding = mc.player.getMainHandItem().getItem() instanceof AlastorStaffItem
                    || mc.player.getOffhandItem().getItem() instanceof AlastorStaffItem;
            if (holding) ModNetwork.CHANNEL.sendToServer(new CycleSkillPacket(mc.player.isShiftKeyDown() ? -1 : 1));
        }
    }

    private ClientKeys() {}
}
