package com.alastor.staff.network;

import com.alastor.staff.AlastorStaffMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class ModNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(AlastorStaffMod.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        CHANNEL.registerMessage(0, CycleSkillPacket.class, CycleSkillPacket::encode, CycleSkillPacket::decode,
                CycleSkillPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    private ModNetwork() {}
}
