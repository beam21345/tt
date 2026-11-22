package com.alastor.staff.network;

import com.alastor.staff.skill.StaffCombo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: left-click with the staff (sneak = rip up and hurl the ground). */
public record ComboPacket(boolean sneak) {

    public static void encode(ComboPacket msg, FriendlyByteBuf buf) { buf.writeBoolean(msg.sneak); }

    public static ComboPacket decode(FriendlyByteBuf buf) { return new ComboPacket(buf.readBoolean()); }

    public static void handle(ComboPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p != null && !p.isSpectator()) StaffCombo.attack(p, msg.sneak);
        });
        ctx.get().setPacketHandled(true);
    }
}
