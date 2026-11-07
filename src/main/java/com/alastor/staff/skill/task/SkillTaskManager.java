package com.alastor.staff.skill.task;

import com.alastor.staff.AlastorStaffMod;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Runs all active skill tasks, per dimension, once per server tick. */
public final class SkillTaskManager {
    private static final Map<ResourceKey<Level>, List<SkillTask>> ACTIVE = new HashMap<>();
    private static final Map<ResourceKey<Level>, List<SkillTask>> PENDING = new HashMap<>();

    public static void add(ServerLevel level, SkillTask task) {
        PENDING.computeIfAbsent(level.dimension(), k -> new ArrayList<>()).add(task);
    }

    public static void tick(ServerLevel level) {
        List<SkillTask> list = ACTIVE.computeIfAbsent(level.dimension(), k -> new ArrayList<>());
        List<SkillTask> pending = PENDING.remove(level.dimension());
        if (pending != null) list.addAll(pending);

        Iterator<SkillTask> it = list.iterator();
        while (it.hasNext()) {
            SkillTask task = it.next();
            boolean done;
            try {
                done = task.tick(level);
            } catch (Exception ex) {
                AlastorStaffMod.LOGGER.error("Alastor Staff skill task failed", ex);
                done = true;
            }
            if (done) {
                try {
                    task.onEnd(level);
                } catch (Exception ex) {
                    AlastorStaffMod.LOGGER.error("Alastor Staff skill task end failed", ex);
                }
                it.remove();
            }
        }
    }

    public static void clear() {
        ACTIVE.clear();
        PENDING.clear();
    }

    private SkillTaskManager() {}
}
