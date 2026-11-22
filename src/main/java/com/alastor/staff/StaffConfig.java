package com.alastor.staff;

import net.minecraftforge.common.ForgeConfigSpec;

/** config/alastorstaff-common.toml */
public final class StaffConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue BREAK_BLOCKS;
    public static final ForgeConfigSpec.DoubleValue MAX_HARDNESS;
    public static final ForgeConfigSpec.BooleanValue DROP_BROKEN_BLOCKS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("terrain");
        BREAK_BLOCKS = b.comment("Tentacles smash and rip up blocks").define("tentaclesBreakBlocks", true);
        MAX_HARDNESS = b.comment("Hardest block tentacles can break (stone = 1.5, iron ore = 3, obsidian = 50)")
                .defineInRange("maxHardness", 3.0, 0.0, 50.0);
        DROP_BROKEN_BLOCKS = b.comment("Broken blocks drop as items").define("dropBrokenBlocks", true);
        b.pop();
        SPEC = b.build();
    }

    private StaffConfig() {}
}
