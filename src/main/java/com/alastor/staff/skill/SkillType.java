package com.alastor.staff.skill;

import net.minecraft.network.chat.Component;

/** The six skills of the Alastor Staff. Cooldowns are in ticks (20 ticks = 1 second). */
public enum SkillType {
    SHADOW_TENTACLES("shadow_tentacles", 80),
    SHADOW_SUMMONING("shadow_summoning", 1400),
    RADIO_SOUND("radio_sound", 300),
    SHADOW_FRACTURE("shadow_fracture", 400),
    SHADOW_DOMAIN("shadow_domain", 900),
    RADIO_DEMON("radio_demon", 20);

    public final String id;
    public final int cooldown;

    SkillType(String id, int cooldown) {
        this.id = id;
        this.cooldown = cooldown;
    }

    public Component displayName() {
        return Component.translatable("skill.alastorstaff." + id);
    }

    public static SkillType byIndex(int i) {
        SkillType[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}
