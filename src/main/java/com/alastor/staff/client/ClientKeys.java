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

    public static final KeyMapping SUMMON_STAFF = new KeyMapping("key.alastorstaff.summon_staff",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.KEY_V, "key.categories.alastorstaff");

    private static long lastCombo;

    /** Left-click with the staff = tentacle combo (sneak: rip up and hurl the ground) instead of a plain hit. */
    @SubscribeEvent
    public static void onAttackKey(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isAttack() || mc.player == null) return;
        if (!(mc.player.getMainHandItem().getItem() instanceof AlastorStaffItem)) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        long now = mc.level != null ? mc.level.getGameTime() : 0;
        if (now - lastCombo < 4) return;
        lastCombo = now;
        ModNetwork.CHANNEL.sendToServer(new com.alastor.staff.network.ComboPacket(mc.player.isShiftKeyDown()));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (SUMMON_STAFF.consumeClick()) {
            if (mc.player != null) ModNetwork.CHANNEL.sendToServer(new com.alastor.staff.network.SummonStaffPacket());
        }
        while (CYCLE_SKILL.consumeClick()) {
            if (mc.player == null) continue;
            boolean holding = mc.player.getMainHandItem().getItem() instanceof AlastorStaffItem
                    || mc.player.getOffhandItem().getItem() instanceof AlastorStaffItem;
            if (holding) ModNetwork.CHANNEL.sendToServer(new CycleSkillPacket(mc.player.isShiftKeyDown() ? -1 : 1));
        }
    }

    private ClientKeys() {}
}
