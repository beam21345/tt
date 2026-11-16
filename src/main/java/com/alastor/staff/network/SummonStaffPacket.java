package com.alastor.staff.network;

import com.alastor.staff.skill.StaffBinding;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: summon the staff from the shadow, or send it back. */
public record SummonStaffPacket() {

    public static void encode(SummonStaffPacket msg, FriendlyByteBuf buf) {}

    public static SummonStaffPacket decode(FriendlyByteBuf buf) {
        return new SummonStaffPacket();
    }

    public static void handle(SummonStaffPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && !player.isSpectator()) StaffBinding.toggle(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
