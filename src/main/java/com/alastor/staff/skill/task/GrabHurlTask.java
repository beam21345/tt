package com.alastor.staff.skill.task;

import com.alastor.staff.entity.HurledBlockEntity;
import com.alastor.staff.entity.TentacleEntity;
import com.alastor.staff.fx.ShadowFx;
import com.alastor.staff.registry.ModSounds;
import com.alastor.staff.util.Targeting;
import com.alastor.staff.util.TerrainBreaker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Sneak + attack: a tentacle bursts out of the ground in front, tears up a chunk of earth (up to 3x3
 * blocks), hoists it over the caster's head and hurls it where they look.
 */
public class GrabHurlTask implements SkillTask {
    private static final int LIFT = 9, HOLD = 4;

    private final Player p;
    private TentacleEntity t;
    private HurledBlockEntity chunk;
    private Vec3 source;
    private int age;

    public GrabHurlTask(Player p) {
        this.p = p;
    }

    private Vec3 holdPoint() {
        return p.position().add(0, p.getBbHeight() + 2.6, 0).subtract(Targeting.horizontalLook(p).scale(0.8));
    }

    @Override
    public boolean tick(ServerLevel level) {
        if (p.isRemoved() || !p.isAlive()) {
            if (chunk != null) chunk.hurl(new Vec3(0, -0.5, 0));
            return true;
        }
        age++;
        if (age == 1) {
            Vec3 ahead = p.position().add(Targeting.horizontalLook(p).scale(3.5));
            Double g = Targeting.findGround(level, ahead.x, p.getY(), ahead.z);
            if (g == null) return true;
            source = new Vec3(ahead.x, g, ahead.z);
            BlockPos center = BlockPos.containing(source.x, g - 0.5, source.z);
            BlockState look = level.getBlockState(center);
            if (look.isAir()) look = Blocks.DIRT.defaultBlockState();

            // tear the ground out (only what the config / protection allows)
            int taken = 0;
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos bp = center.offset(dx, 0, dz);
                    BlockState st = level.getBlockState(bp);
                    if (TerrainBreaker.canBreak(level, p, bp, st)) {
                        level.removeBlock(bp, false);
                        taken++;
                    }
                }
            float size = 1.2f + 0.12f * taken;
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, look), source.x, source.y, source.z, 50, 1.2, 0.3, 1.2, 0.3);
            ShadowFx.spread(level, source, 3f, 40, 3);
            chunk = HurledBlockEntity.spawn(level, source.add(0, -0.3, 0), look, size, p);
            Vec3 root = source.add(Targeting.horizontalLook(p).scale(1.6));
            t = TentacleEntity.spawn(level, root, source.subtract(root), LIFT + HOLD + 16, 3, 1.3f);
            ModSounds.play(level, source, ModSounds.TENTACLE_BURST, 1.2f, 0.8f);
            level.playSound(null, source.x, source.y, source.z, net.minecraft.sounds.SoundEvents.ROOTED_DIRT_BREAK,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.5f, 0.6f);
        }
        if (chunk == null || t == null) return true;

        if (age <= LIFT + HOLD) {
            double f = Math.min(1.0, age / (double) LIFT);
            f = 1 - (1 - f) * (1 - f);
            Vec3 at = source.add(holdPoint().subtract(source).scale(f));
            chunk.holdAt(at);
            t.reachTo(at);
            return false;
        }
        if (age == LIFT + HOLD + 1) {
            Vec3 look = p.getLookAngle();
            chunk.hurl(look.scale(2.4).add(0, 0.12, 0));
            t.reachTo(chunk.position().add(look.scale(6)));
            t.retract();
            ModSounds.play(level, chunk.position(), ModSounds.ZAP, 0.8f, 0.7f);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.6f);
        }
        return age > LIFT + HOLD + 2;
    }
}
