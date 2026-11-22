package com.alastor.staff.skill;

import net.minecraft.network.chat.Component;

/** The six skills of the Alastor Staff. Cooldowns are in ticks (20 ticks = 1 second). */
public enum SkillType {
    SHADOW_TENTACLES("shadow_tentacles", 80, CastAnim.POINT),
    SHADOW_SUMMONING("shadow_summoning", 1400, CastAnim.RAISE),
    RADIO_SOUND("radio_sound", 300, CastAnim.PLANT),
    SHADOW_FRACTURE("shadow_fracture", 400, CastAnim.PLANT),
    SHADOW_DOMAIN("shadow_domain", 900, CastAnim.PLANT),
    RADIO_DEMON("radio_demon", 20, CastAnim.RAISE),
    SHADOW_STEP("shadow_step", 160, CastAnim.POINT);

    public final String id;
    public final int cooldown;
    public final int anim;

    SkillType(String id, int cooldown, int anim) {
        this.id = id;
        this.cooldown = cooldown;
        this.anim = anim;
    }

    /** Caster animations (shared with the client renderer). */
    public static final class CastAnim {
        public static final int POINT = 0, PLANT = 1, RAISE = 2;
        /** Melee combo motions. */
        public static final int SWEEP_R = 3, SWEEP_L = 4, SLAM = 5, THRUST = 6, BURST = 7, HURL = 8;
        private CastAnim() {}
    }

    public Component displayName() {
        return Component.translatable("skill.alastorstaff." + id);
    }

    public static SkillType byIndex(int i) {
        SkillType[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}
