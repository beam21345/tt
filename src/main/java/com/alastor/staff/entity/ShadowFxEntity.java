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
 * Purely visual ground effect drawn as a textured model:
 * SIGIL (voodoo circle), RING (expanding wave), RIFT (shadow crack), DOMAIN (shadow square).
 */
public class ShadowFxEntity extends Entity {
    public static final int SIGIL = 0, RING = 1, RIFT = 2, DOMAIN = 3;

    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(ShadowFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(ShadowFxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(ShadowFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> GROW = SynchedEntityData.defineId(ShadowFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> YAW = SynchedEntityData.defineId(ShadowFxEntity.class, EntityDataSerializers.FLOAT);

    public ShadowFxEntity(EntityType<? extends ShadowFxEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /**
     * @param size  radius (SIGIL/RING), half-width (DOMAIN) or length (RIFT) in blocks
     * @param grow  ticks to reach full size
     * @param yaw   direction in degrees (RIFT only)
     */
    public static ShadowFxEntity spawn(ServerLevel level, int variant, Vec3 pos, float size, int life, int grow, float yaw) {
        ShadowFxEntity e = ModEntities.SHADOW_FX.get().create(level);
        if (e == null) return null;
        e.setPos(pos.x, pos.y, pos.z);
        e.entityData.set(VARIANT, variant);
        e.entityData.set(SIZE, size);
        e.entityData.set(LIFE, life);
        e.entityData.set(GROW, Math.max(1, grow));
        e.entityData.set(YAW, yaw);
        level.addFreshEntity(e);
        return e;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(VARIANT, SIGIL);
        this.entityData.define(SIZE, 2f);
        this.entityData.define(LIFE, 20);
        this.entityData.define(GROW, 5);
        this.entityData.define(YAW, 0f);
    }

    public int variant() { return entityData.get(VARIANT); }
    public float size() { return entityData.get(SIZE); }
    public int life() { return entityData.get(LIFE); }
    public int grow() { return entityData.get(GROW); }
    public float yawDeg() { return entityData.get(YAW); }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && this.tickCount >= life()) this.discard();
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(size() + 2);
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
