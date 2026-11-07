package com.alastor.staff.skill.task;

import net.minecraft.server.level.ServerLevel;

/** A skill effect that plays out over several ticks. */
public interface SkillTask {
    /** @return true when the task is finished and should be removed. */
    boolean tick(ServerLevel level);

    default void onEnd(ServerLevel level) {}
}
