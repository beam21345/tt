package com.alastor.staff.skill.task;

import com.alastor.staff.entity.ShadowDemonEntity;
import com.alastor.staff.entity.ShadowFxEntity;
import com.alastor.staff.entity.TentacleEntity;
import com.alastor.staff.fx.ShadowFx;
import com.alastor.staff.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Shadow Summoning ritual for one demon: sigil + pillar of green light, tentacles spiral up around
 * the spot, they burst apart and the demon steps out of the shadow.
 */
public class SummonTask implements SkillTask {
    private static final int EMERGE = 16;

    private final Player owner;
    private final Vec3 at;
    private final List<TentacleEntity> coil = new ArrayList<>();
    private int age;

    public SummonTask(Player owner, Vec3 at) {
        this.owner = owner;
        this.at = at;
    }

    @Override
    public boolean tick(ServerLevel level) {
        age++;
        if (age == 1) {
            ShadowFxEntity.spawn(level, ShadowFxEntity.SIGIL, at, 5.0f, 50, 8, 0f);
            ShadowFxEntity.spawn(level, ShadowFxEntity.PILLAR, at, 0.9f, 32, 10, 0f);
            for (int k = 0; k < 4; k++) {
                double a = k * Math.PI / 2;
                Vec3 root = at.add(Math.cos(a) * 1.4, 0, Math.sin(a) * 1.4);
                TentacleEntity t = TentacleEntity.spawn(level, root, new Vec3(0, 3, 0), EMERGE + 10, 6, 0.8f);
                if (t != null) coil.add(t);
            }
            level.playSound(null, at.x, at.y, at.z, SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 1.2f, 0.6f);
        }
        // tentacles twist upward around the spot, closing over it
        for (int k = 0; k < coil.size(); k++) {
            double a = k * Math.PI / 2 + age * 0.35;
            double h = 1.5 + age * 0.22;
            Vec3 root = coil.get(k).position();
            coil.get(k).setTip(at.add(Math.cos(a) * 0.4, h, Math.sin(a) * 0.4).subtract(root));
        }
        level.sendParticles(ShadowFx.BLACK, at.x, at.y + 1, at.z, 6, 0.5, 1.0, 0.5, 0);
        if (age % 2 == 0) level.sendParticles(ParticleTypes.SCULK_SOUL, at.x, at.y + 0.3, at.z, 2, 0.8, 0.1, 0.8, 0.05);
        if (age % 4 == 0) ShadowFx.zap(level, at.add(0, 6 + owner.getRandom().nextDouble() * 4, 0),
                at.add((owner.getRandom().nextDouble() - 0.5) * 6, 0, (owner.getRandom().nextDouble() - 0.5) * 6), 6);

        if (age == EMERGE) {
            for (TentacleEntity t : coil) {
                Vec3 out = t.position().subtract(at).normalize().scale(3.5).add(0, 2.5, 0);
                t.setTip(out);
                t.retract();
            }
            ShadowDemonEntity demon = ModEntities.SHADOW_DEMON.get().create(level);
            if (demon != null) {
                demon.moveTo(at.x, at.y, at.z, owner.getYRot(), 0);
                demon.setOwner(owner);
                demon.setLifeTicks(ShadowDemonEntity.DEFAULT_LIFE);
                level.addFreshEntity(demon);
            }
            ShadowFxEntity.spawn(level, ShadowFxEntity.RING, at, 8f, 14, 8, 0f);
            ShadowFx.crackle(level, at, 4, 4);
            level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 1, at.z, 40, 0.6, 1.0, 0.6, 0.12);
            level.sendParticles(ShadowFx.GREEN, at.x, at.y + 1.6, at.z, 30, 0.6, 0.8, 0.6, 0);
            level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 0.8f, 1.3f);
            return true;
        }
        return false;
    }
}
