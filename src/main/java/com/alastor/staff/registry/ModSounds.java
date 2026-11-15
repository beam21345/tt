package com.alastor.staff.registry;

import com.alastor.staff.AlastorStaffMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Original sound effects for the staff (synthesized for this mod). */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, AlastorStaffMod.MODID);

    public static final RegistryObject<SoundEvent> RADIO_STATIC = reg("radio_static");
    public static final RegistryObject<SoundEvent> RADIO_TUNE = reg("radio_tune");
    public static final RegistryObject<SoundEvent> SHADOW_BOOM = reg("shadow_boom");
    public static final RegistryObject<SoundEvent> SHADOW_HUM = reg("shadow_hum");
    public static final RegistryObject<SoundEvent> TENTACLE_BURST = reg("tentacle_burst");
    public static final RegistryObject<SoundEvent> ZAP = reg("zap");

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(AlastorStaffMod.MODID, name)));
    }

    public static void play(ServerLevel level, Vec3 at, RegistryObject<SoundEvent> sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound.get(), SoundSource.PLAYERS, volume, pitch);
    }

    private ModSounds() {}
}
