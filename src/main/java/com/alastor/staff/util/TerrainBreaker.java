package com.alastor.staff.util;

import com.alastor.staff.StaffConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;

import java.util.ArrayList;
import java.util.List;

/** Lets tentacles smash through terrain, respecting the config, game mode and protection mods. */
public final class TerrainBreaker {

    /** Whether the player may have this block broken by a tentacle right now. */
    public static boolean canBreak(ServerLevel level, Player owner, BlockPos pos, BlockState state) {
        if (!StaffConfig.BREAK_BLOCKS.get() || !owner.mayBuild()) return false;
        if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty()) return false;
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0 || hardness > StaffConfig.MAX_HARDNESS.get()) return false;
        return !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, owner));
    }

    /** Smashes up to {@code max} breakable blocks within {@code radius} of {@code at}. */
    public static int smash(ServerLevel level, Player owner, Vec3 at, double radius, int max) {
        if (!StaffConfig.BREAK_BLOCKS.get()) return 0;
        int r = (int) Math.ceil(radius), broken = 0;
        BlockPos c = BlockPos.containing(at);
        List<BlockPos> list = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++)
            for (int dy = -r; dy <= r; dy++)
                for (int dz = -r; dz <= r; dz++)
                    if (dx * dx + dy * dy + dz * dz <= radius * radius) list.add(c.offset(dx, dy, dz));
        list.sort((a, b) -> Double.compare(a.distToCenterSqr(at), b.distToCenterSqr(at)));
        boolean drop = StaffConfig.DROP_BROKEN_BLOCKS.get();
        for (BlockPos pos : list) {
            if (broken >= max) break;
            BlockState st = level.getBlockState(pos);
            if (!canBreak(level, owner, pos, st)) continue;
            level.destroyBlock(pos, drop, owner);   // vanilla break sound + crumble particles
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    14, 0.35, 0.35, 0.35, 0.25);
            // chunks of the block fly off away from the blow
            Vec3 bc = Vec3.atCenterOf(pos);
            Vec3 out = bc.subtract(at);
            out = out.lengthSqr() > 1.0E-4 ? out.normalize() : new Vec3(0, 1, 0);
            int chunks = 1 + level.getRandom().nextInt(2);
            for (int k = 0; k < chunks; k++) {
                Vec3 vel = out.scale(0.25 + level.getRandom().nextDouble() * 0.35)
                        .add((level.getRandom().nextDouble() - 0.5) * 0.3, 0.25 + level.getRandom().nextDouble() * 0.35,
                                (level.getRandom().nextDouble() - 0.5) * 0.3);
                com.alastor.staff.entity.DebrisEntity.spawn(level, bc, st, vel);
            }
            broken++;
        }
        return broken;
    }

    private TerrainBreaker() {}
}
