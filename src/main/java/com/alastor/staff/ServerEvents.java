package com.alastor.staff;

import com.alastor.staff.skill.task.SkillTaskManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge-bus events: drives every running skill effect once per server tick. */
@Mod.EventBusSubscriber(modid = AlastorStaffMod.MODID)
public final class ServerEvents {

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel serverLevel) {
            SkillTaskManager.tick(serverLevel);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SkillTaskManager.clear();
    }

    private ServerEvents() {}
}
