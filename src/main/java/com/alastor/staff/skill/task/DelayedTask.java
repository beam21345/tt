package com.alastor.staff.skill.task;

import net.minecraft.server.level.ServerLevel;

import java.util.function.Consumer;

/** Runs an action after a delay (used to choreograph waves of effects). */
public class DelayedTask implements SkillTask {
    private int delay;
    private final Consumer<ServerLevel> action;

    public DelayedTask(int delay, Consumer<ServerLevel> action) {
        this.delay = delay;
        this.action = action;
    }

    @Override
    public boolean tick(ServerLevel level) {
        if (delay-- > 0) return false;
        action.accept(level);
        return true;
    }
}
