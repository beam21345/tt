package com.alastor.staff.network;

import com.alastor.staff.item.AlastorStaffItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: switch the held staff to the next (+1) or previous (-1) skill. */
public record CycleSkillPacket(int direction) {

    public static void encode(CycleSkillPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.direction);
    }

    public static CycleSkillPacket decode(FriendlyByteBuf buf) {
        return new CycleSkillPacket(buf.readByte());
    }

    public static void handle(CycleSkillPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof AlastorStaffItem)) stack = player.getOffhandItem();
            if (stack.getItem() instanceof AlastorStaffItem) {
                AlastorStaffItem.cycleSkill(player, stack, msg.direction >= 0 ? 1 : -1);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
