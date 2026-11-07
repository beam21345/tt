package com.alastor.staff.skill.task;

import com.alastor.staff.entity.TentacleEntity;
import com.alastor.staff.fx.ShadowFx;
import com.alastor.staff.util.Targeting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Radio Demon: a 3D tentacle grabs a creature, lifts it up, then flings it away from the caster. */
public class GrabThrowTask implements SkillTask {
    private static final int LIFT = 10;

    private final Player owner;
    private final LivingEntity target;
    private final Vec3 start;
    private final TentacleEntity tentacle;
    private int age;

    public GrabThrowTask(Player owner, LivingEntity target, ServerLevel level) {
        this.owner = owner;
        this.target = target;
        this.start = target.position();
        Vec3 toOwner = owner.position().subtract(start);
        Vec3 flat = new Vec3(toOwner.x, 0, toOwner.z);
        flat = flat.lengthSqr() > 1.0E-4 ? flat.normalize() : new Vec3(1, 0, 0);
        double rx = start.x + flat.x * 1.2, rz = start.z + flat.z * 1.2;
        Vec3 root = new Vec3(rx, Targeting.groundY(level, rx, start.y, rz), rz);
        this.tentacle = TentacleEntity.spawn(level, root, bodyOf(target).subtract(root), LIFT + 14, 3, 0.55f);
        level.playSound(null, start.x, start.y, start.z, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 0.6f, 1.6f);
    }

    private static Vec3 bodyOf(LivingEntity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    @Override
    public boolean tick(ServerLevel level) {
        if (!target.isAlive() || target.isRemoved() || owner.isRemoved()) {
            if (tentacle != null) tentacle.retract();
            return true;
        }
        age++;
        if (age <= LIFT) {
            Vec3 pos = start.add(0, age * 0.25, 0);
            target.teleportTo(pos.x, pos.y, pos.z);
            target.setDeltaMovement(Vec3.ZERO);
            target.fallDistance = 0;
            if (tentacle != null) tentacle.reachTo(bodyOf(target));
            return false;
        }
        Vec3 away = target.position().subtract(owner.position());
        away = new Vec3(away.x, 0, away.z);
        away = away.lengthSqr() > 1.0E-4 ? away.normalize() : new Vec3(1, 0, 0);
        Targeting.hurt(owner, target, 4f);
        Targeting.launch(target, away.scale(2.0).add(0, 0.7, 0));
        if (tentacle != null) {
            // whip the tentacle out in the throw direction, then let it sink
            tentacle.reachTo(bodyOf(target).add(away.scale(2.5)));
            tentacle.retract();
        }
        level.sendParticles(ShadowFx.GREEN, target.getX(), target.getY() + 1, target.getZ(), 12, 0.4, 0.4, 0.4, 0);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 1.0f, 0.8f);
        return true;
    }
}
