package com.alastor.staff.client;

import com.alastor.staff.AlastorStaffMod;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-only screen shake. Big skill effects add "trauma" based on how close they are to the
 * camera; the camera then jitters with smooth noise that fades out.
 */
@Mod.EventBusSubscriber(modid = AlastorStaffMod.MODID, value = Dist.CLIENT)
public final class CameraShake {
    private static float trauma;
    private static float time;

    /** @param strength 0..1 at the source; fades with distance up to {@code range} blocks. */
    public static void add(Vec3 source, float strength, float range) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double dist = mc.player.position().distanceTo(source);
        float falloff = (float) Math.max(0.0, 1.0 - dist / range);
        trauma = Math.min(1f, trauma + strength * falloff);
        RadioStaticOverlay.trigger(strength * falloff * 0.9f);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        time += 1f;
        trauma = Math.max(0f, trauma - 0.035f);
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (trauma <= 0f) return;
        float s = trauma * trauma;
        float t = time + (float) event.getPartialTick();
        float yaw = (float) (Math.sin(t * 1.7) + Math.sin(t * 3.1 + 1.3) * 0.5) * 2.2f * s;
        float pitch = (float) (Math.sin(t * 2.3 + 0.7) + Math.sin(t * 4.1) * 0.5) * 2.2f * s;
        float roll = (float) Math.sin(t * 1.3 + 2.1) * 3.0f * s;
        event.setYaw(event.getYaw() + yaw);
        event.setPitch(event.getPitch() + pitch);
        event.setRoll(event.getRoll() + roll);
    }

    private CameraShake() {}
}
