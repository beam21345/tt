package com.alastor.staff.entity;

import com.alastor.staff.fx.ShadowFx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.UUID;

/**
 * Shadow demon summoned by skill 2. Lives for 1 minute, follows its summoner and attacks
 * hostile mobs (anything that is an {@link Enemy}) – never animals, villagers or players.
 */
public class ShadowDemonEntity extends PathfinderMob {
    public static final int DEFAULT_LIFE = 1200; // 60 seconds
    private static final EntityDataAccessor<Float> DATA_SIZE = SynchedEntityData.defineId(ShadowDemonEntity.class, EntityDataSerializers.FLOAT);

    @Nullable private UUID ownerUUID;
    private int life = DEFAULT_LIFE;

    public ShadowDemonEntity(EntityType<? extends ShadowDemonEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_SIZE, 1.0f);
    }

    public float getDemonSize() { return this.entityData.get(DATA_SIZE); }

    /** Sets the demon's size; bigger demons are tougher and hit harder but move slower. */
    public void setDemonSize(float size) {
        this.entityData.set(DATA_SIZE, size);
        this.refreshDimensions();
        if (this.getAttribute(Attributes.MAX_HEALTH) != null) {
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(28.0 * size * size);
            this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(5.0 * size + 2.0);
            this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.42 - 0.06 * size);
            this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(Math.min(1.0, 0.35 * size));
            this.setHealth(this.getMaxHealth());
        }
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(getDemonSize());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_SIZE.equals(key)) this.refreshDimensions();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.33)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.ARMOR, 6.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.35, true));
        this.goalSelector.addGoal(3, new FollowSummonerGoal(this));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Mob.class, 5, false, false,
                e -> e instanceof Enemy && !(e instanceof ShadowDemonEntity)));
    }

    // ---------- owner ----------
    public void setOwner(Player owner) { this.ownerUUID = owner.getUUID(); }

    public void setLifeTicks(int ticks) { this.life = ticks; }

    public boolean isOwnedBy(Entity e) { return ownerUUID != null && ownerUUID.equals(e.getUUID()); }

    @Nullable
    public LivingEntity getOwner() {
        if (ownerUUID != null && this.level() instanceof ServerLevel sl) {
            Entity e = sl.getEntity(ownerUUID);
            if (e instanceof LivingEntity le) return le;
        }
        return null;
    }

    @Override
    public boolean isAlliedTo(Entity e) {
        if (isOwnedBy(e)) return true;
        if (e instanceof ShadowDemonEntity other && ownerUUID != null && ownerUUID.equals(other.ownerUUID)) return true;
        return super.isAlliedTo(e);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker != null && (isOwnedBy(attacker) || attacker instanceof ShadowDemonEntity)) return false;
        return super.hurt(source, amount);
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        // Never turn on the summoner, other players or peaceful creatures – monsters only.
        if (target != null && !(target instanceof Enemy)) return;
        super.setTarget(target);
    }

    // ---------- life & visuals ----------
    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel sl) {
            if (--life <= 0) {
                sl.sendParticles(ShadowFx.BLACK, getX(), getY() + 1, getZ(), 40, 0.4, 0.8, 0.4, 0);
                sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1, getZ(), 15, 0.3, 0.6, 0.3, 0.05);
                this.discard();
                return;
            }
            float sz = getDemonSize();
            if (tickCount % 3 == 0) sl.sendParticles(ShadowFx.BLACK, getX(), getY() + 0.3, getZ(), 3, 0.35 * sz, 0.2, 0.35 * sz, 0);
            if (tickCount % 6 == 0) sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 0.1, getZ(), 1, 0.3 * sz, 0.0, 0.3 * sz, 0.01);
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean ok = super.doHurtTarget(target);
        if (ok && this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ShadowFx.GREEN, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 6, 0.3, 0.3, 0.3, 0);
        }
        return ok;
    }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.SCULK_CATALYST_BLOOM; }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.SOUL_ESCAPE; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerUUID != null) tag.putUUID("Owner", ownerUUID);
        tag.putInt("Life", life);
        tag.putFloat("DemonSize", getDemonSize());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) ownerUUID = tag.getUUID("Owner");
        if (tag.contains("Life")) life = tag.getInt("Life");
        if (tag.contains("DemonSize")) this.entityData.set(DATA_SIZE, tag.getFloat("DemonSize"));
    }

    /** Walks back to the summoner when idle; teleports if left far behind. */
    static class FollowSummonerGoal extends Goal {
        private final ShadowDemonEntity demon;
        @Nullable private LivingEntity owner;
        private int recalc;

        FollowSummonerGoal(ShadowDemonEntity demon) {
            this.demon = demon;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity o = demon.getOwner();
            if (o == null || o.isSpectator()) return false;
            if (demon.getTarget() != null && demon.getTarget().isAlive()) return false;
            if (demon.distanceToSqr(o) < 36) return false;
            owner = o;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return owner != null && owner.isAlive() && demon.getTarget() == null && demon.distanceToSqr(owner) > 9;
        }

        @Override
        public void start() { recalc = 0; }

        @Override
        public void stop() {
            owner = null;
            demon.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (owner == null) return;
            demon.getLookControl().setLookAt(owner, 10.0f, demon.getMaxHeadXRot());
            if (--recalc <= 0) {
                recalc = 10;
                if (demon.distanceToSqr(owner) > 400) {
                    demon.teleportTo(owner.getX(), owner.getY(), owner.getZ());
                    demon.getNavigation().stop();
                } else {
                    demon.getNavigation().moveTo(owner, 1.25);
                }
            }
        }
    }
}
