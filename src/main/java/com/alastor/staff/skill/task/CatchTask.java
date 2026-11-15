package com.alastor.staff.skill.task;

import com.alastor.staff.entity.ShadowFxEntity;
import com.alastor.staff.entity.TentacleEntity;
import com.alastor.staff.fx.ShadowFx;
import com.alastor.staff.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Passive "Shadow Cradle": tentacles burst up and catch the staff holder before a hard landing. */
public class CatchTask implements SkillTask {
    private static final int HOLD = 10;

    private final Player player;
    private final Vec3 ground;
    private final List<TentacleEntity> arms = new ArrayList<>();
    private int age;

    public CatchTask(Player player, Vec3 ground) {
        this.player = player;
        this.ground = ground;
    }

    private Vec3 body() {
        return player.position().add(0, player.getBbHeight() * 0.45, 0);
    }

    @Override
    public boolean tick(ServerLevel level) {
        if (player.isRemoved() || !player.isAlive()) return true;
        age++;
        if (age == 1) {
            for (int k = 0; k < 4; k++) {
                double a = k * Math.PI / 2 + player.getYRot() * 0.0174;
                Vec3 root = ground.add(Math.cos(a) * 1.8, 0, Math.sin(a) * 1.8);
                TentacleEntity t = TentacleEntity.spawn(level, root, body().subtract(root), HOLD + 10, 2, 0.9f);
                if (t != null) arms.add(t);
            }
            ShadowFxEntity.spawn(level, ShadowFxEntity.SIGIL, ground, 2.6f, 30, 4, 0f);
            ModSounds.play(level, ground, ModSounds.TENTACLE_BURST, 1.0f, 1.1f);
        }
        // cushion the fall: tentacles hold the player and lower them softly
        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(v.x * 0.5, Math.max(v.y * 0.3, -0.08), v.z * 0.5);
        player.hurtMarked = true;
        player.fallDistance = 0;
        for (TentacleEntity t : arms) t.reachTo(body());
        level.sendParticles(ShadowFx.BLACK, player.getX(), player.getY() + 0.5, player.getZ(), 4, 0.4, 0.4, 0.4, 0);

        if (age >= HOLD || player.onGround()) {
            arms.forEach(TentacleEntity::retract);
            level.sendParticles(ParticleTypes.SQUID_INK, ground.x, ground.y + 0.2, ground.z, 20, 1.0, 0.1, 1.0, 0.05);
            ShadowFxEntity.spawn(level, ShadowFxEntity.RING, ground, 3.5f, 12, 8, 0f);
            return true;
        }
        return false;
    }
}
