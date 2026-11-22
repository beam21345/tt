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
 * SIGIL (voodoo circle), RING (expanding wave), RIFT (shadow crack), DOMAIN (shadow circle), PILLAR (light beam).
 */
public class ShadowFxEntity extends Entity {
    /** Client-only bookkeeping: whether this effect already shook the camera. */
    public boolean clientShakeDone;

    public static final int SIGIL = 0, RING = 1, RIFT = 2, DOMAIN = 3, PILLAR = 4, BOLT = 5, SHADOW = 6;

    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(ShadowFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(ShadowFxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(ShadowFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> GROW = SynchedEntityData.defineId(ShadowFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> YAW = SynchedEntityData.defineId(ShadowFxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> EX = SynchedEntityData.defineId(ShadowFxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> EY = SynchedEntityData.defineId(ShadowFxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> EZ = SynchedEntityData.defineId(ShadowFxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> RED = SynchedEntityData.defineId(ShadowFxEntity.class, EntityDataSerializers.BOOLEAN);

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

    /** A crackling lightning bolt from {@code from} to {@code to}, green or red. */
    public static ShadowFxEntity bolt(ServerLevel level, Vec3 from, Vec3 to, boolean red, int life) {
        ShadowFxEntity e = ModEntities.SHADOW_FX.get().create(level);
        if (e == null) return null;
        e.setPos(from.x, from.y, from.z);
        e.entityData.set(VARIANT, BOLT);
        e.entityData.set(LIFE, life);
        e.entityData.set(GROW, 2);
        e.entityData.set(EX, (float) (to.x - from.x));
        e.entityData.set(EY, (float) (to.y - from.y));
        e.entityData.set(EZ, (float) (to.z - from.z));
        e.entityData.set(RED, red);
        level.addFreshEntity(e);
        if (level.getRandom().nextFloat() < 0.4f) com.alastor.staff.registry.ModSounds.play(level, from, com.alastor.staff.registry.ModSounds.ZAP, 0.5f, 0.8f + level.getRandom().nextFloat() * 0.6f);
        return e;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(EX, 0f);
        this.entityData.define(EY, 0f);
        this.entityData.define(EZ, 0f);
        this.entityData.define(RED, false);
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
    public float ex() { return entityData.get(EX); }
    public float ey() { return entityData.get(EY); }
    public float ez() { return entityData.get(EZ); }
    public boolean red() { return entityData.get(RED); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) clientFx();
        else if (this.tickCount >= life()) this.discard();
    }

    /** Lodestone glow for each kind of effect (client only). */
    private void clientFx() {
        Level level = level();
        net.minecraft.util.RandomSource r = random;
        float age = tickCount;
        float g = Math.min(1f, age / grow());
        float size = size();
        if (life() - age < 4) return;
        switch (variant()) {
            case PILLAR -> {
                float h = (12f + size * 7f) * g;
                for (int i = 0; i < 2 + (int) size; i++) {
                    double a = r.nextDouble() * Math.PI * 2, rr = size * (0.3 + r.nextDouble() * 0.9);
                    com.alastor.staff.fx.LodestoneFx.glow(level, getX() + Math.cos(a) * rr, getY() + r.nextDouble() * h, getZ() + Math.sin(a) * rr,
                            0.4f + size * 0.25f, 18, 0.1, 0.12 + size * 0.03);
                }
                if (tickCount % 3 == 0) com.alastor.staff.fx.LodestoneFx.twinkle(level, getX(), getY() + r.nextDouble() * h, getZ(), 0.3f + size * 0.1f, size);
            }
            case RING -> {
                if (g < 1f) {
                    float rr = size * (1f - (1f - g) * (1f - g) * (1f - g));
                    int n = (int) Math.min(18, 4 + rr * 0.8f);
                    for (int i = 0; i < n; i++) {
                        double a = r.nextDouble() * Math.PI * 2;
                        com.alastor.staff.fx.LodestoneFx.glow(level, getX() + Math.cos(a) * rr, getY() + 0.3, getZ() + Math.sin(a) * rr, 0.45f, 10, 0.15, 0.04);
                    }
                }
            }
            case BOLT -> {
                if (tickCount == 1) {
                    com.alastor.staff.fx.LodestoneFx.sparks(level, getX() + ex(), getY() + ey(), getZ() + ez(), 6, 0.2f, red());
                    com.alastor.staff.fx.LodestoneFx.glow(level, getX() + ex(), getY() + ey(), getZ() + ez(), 0.9f, 8, 0.1, 0);
                }
            }
            case SHADOW -> {
                if (tickCount % 4 == 0) {
                    double a = r.nextDouble() * Math.PI * 2, rr = Math.sqrt(r.nextDouble()) * size * g * 0.8;
                    com.alastor.staff.fx.LodestoneFx.shadowSmoke(level, getX() + Math.cos(a) * rr, getY() + 0.1, getZ() + Math.sin(a) * rr, 0.6f + size * 0.05f, 0.2);
                }
            }
            case SIGIL -> {
                if (tickCount % 2 == 0) {
                    double a = r.nextDouble() * Math.PI * 2, rr = size * g * (0.4 + r.nextDouble() * 0.6);
                    com.alastor.staff.fx.LodestoneFx.twinkle(level, getX() + Math.cos(a) * rr, getY() + 0.15, getZ() + Math.sin(a) * rr, 0.25f, 0.05);
                }
            }
            default -> {}
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(size() + 2).expandTowards(0, 48, 0).expandTowards(ex(), ey(), ez());
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
