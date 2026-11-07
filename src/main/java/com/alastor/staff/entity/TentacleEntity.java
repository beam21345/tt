package com.alastor.staff.entity;

import com.alastor.staff.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A purely visual 3D shadow tentacle. It is rooted at its position and reaches toward a tip offset.
 * The server controls where the tip goes (it can follow a grabbed mob); the client draws the model.
 */
public class TentacleEntity extends Entity {
    private static final EntityDataAccessor<Float> TIP_X = SynchedEntityData.defineId(TentacleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TIP_Y = SynchedEntityData.defineId(TentacleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TIP_Z = SynchedEntityData.defineId(TentacleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> THICK = SynchedEntityData.defineId(TentacleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PHASE = SynchedEntityData.defineId(TentacleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(TentacleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RISE = SynchedEntityData.defineId(TentacleEntity.class, EntityDataSerializers.INT);

    public static final int FALL_TICKS = 6;

    public TentacleEntity(EntityType<? extends TentacleEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Spawns a tentacle at {@code base} reaching to {@code base + tip}. */
    public static TentacleEntity spawn(ServerLevel level, Vec3 base, Vec3 tip, int life, int rise, float thickness) {
        TentacleEntity t = ModEntities.TENTACLE.get().create(level);
        if (t == null) return null;
        t.setPos(base.x, base.y, base.z);
        t.setTip(tip);
        t.entityData.set(LIFE, life);
        t.entityData.set(RISE, Math.max(1, rise));
        t.entityData.set(THICK, thickness);
        t.entityData.set(PHASE, (float) (level.getRandom().nextDouble() * Math.PI * 2));
        level.addFreshEntity(t);
        return t;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(TIP_X, 0f);
        this.entityData.define(TIP_Y, 5f);
        this.entityData.define(TIP_Z, 0f);
        this.entityData.define(THICK, 0.6f);
        this.entityData.define(PHASE, 0f);
        this.entityData.define(LIFE, 40);
        this.entityData.define(RISE, 6);
    }

    public void setTip(Vec3 tip) {
        this.entityData.set(TIP_X, (float) tip.x);
        this.entityData.set(TIP_Y, (float) tip.y);
        this.entityData.set(TIP_Z, (float) tip.z);
    }

    /** Points the tip at a world position. */
    public void reachTo(Vec3 worldPos) {
        setTip(worldPos.subtract(position()));
    }

    /** Ends the tentacle early: it sinks back into the ground. */
    public void retract() {
        int end = this.tickCount + FALL_TICKS;
        if (end < getLife()) this.entityData.set(LIFE, end);
    }

    public float tipX() { return entityData.get(TIP_X); }
    public float tipY() { return entityData.get(TIP_Y); }
    public float tipZ() { return entityData.get(TIP_Z); }
    public float thickness() { return entityData.get(THICK); }
    public float phase() { return entityData.get(PHASE); }
    public int getLife() { return entityData.get(LIFE); }

    /** 0..1 how far the tentacle is out of the ground at the given (partial) age. */
    public float extension(float age) {
        int rise = entityData.get(RISE);
        if (age < rise) return age / rise;
        float remaining = getLife() - age;
        if (remaining < FALL_TICKS) return Math.max(0f, remaining / FALL_TICKS);
        return 1f;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && this.tickCount >= getLife()) this.discard();
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        double r = Math.max(Math.abs(tipX()), Math.max(Math.abs(tipY()), Math.abs(tipZ()))) + 2;
        return getBoundingBox().inflate(r);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distSqr) {
        return distSqr < 96 * 96;
    }

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
