package com.alastor.staff.entity;

import com.alastor.staff.registry.ModEntities;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A chunk of a smashed block flying off, tumbling, bouncing and then crumbling away.
 * Purely visual: it never places a block or hurts anything.
 */
public class DebrisEntity extends Entity {
    private static final EntityDataAccessor<BlockState> STATE = SynchedEntityData.defineId(DebrisEntity.class, EntityDataSerializers.BLOCK_STATE);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(DebrisEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(DebrisEntity.class, EntityDataSerializers.INT);

    /** Global cap so a huge smash can't flood the world with debris. */
    private static int alive;
    private static final int MAX_ALIVE = 160;

    /** Tick it came to rest on the ground (both sides work it out), or -1 while still flying. */
    public int landedAt = -1;
    public static final int VANISH_TICKS = 6;

    public DebrisEntity(EntityType<? extends DebrisEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static void spawn(ServerLevel level, Vec3 at, BlockState state, Vec3 velocity) {
        if (alive >= MAX_ALIVE) return;
        DebrisEntity e = ModEntities.DEBRIS.get().create(level);
        if (e == null) return;
        e.setPos(at.x, at.y, at.z);
        e.entityData.set(STATE, state);
        e.entityData.set(SIZE, 0.3f + level.getRandom().nextFloat() * 0.35f);
        e.entityData.set(LIFE, 30 + level.getRandom().nextInt(25));
        e.setDeltaMovement(velocity);
        level.addFreshEntity(e);
        alive++;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(STATE, Blocks.STONE.defaultBlockState());
        this.entityData.define(SIZE, 0.4f);
        this.entityData.define(LIFE, 40);
    }

    public BlockState blockState() { return entityData.get(STATE); }
    public float size() { return entityData.get(SIZE); }
    public int life() { return entityData.get(LIFE); }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = getDeltaMovement().add(0, -0.06, 0);
        Vec3 next = position().add(v);
        BlockPos bp = BlockPos.containing(next);
        if (!level().getBlockState(bp).getCollisionShape(level(), bp).isEmpty()) {
            // bounce off the ground and skid
            v = new Vec3(v.x * 0.55, Math.abs(v.y) > 0.15 ? -v.y * 0.3 : 0, v.z * 0.55);
            next = position().add(v.x, Math.max(0, v.y), v.z);
            if (!level().getBlockState(BlockPos.containing(next)).getCollisionShape(level(), BlockPos.containing(next)).isEmpty()) {
                next = position();
                v = Vec3.ZERO;
            }
        }
        setDeltaMovement(v.scale(0.98));
        setPos(next.x, next.y, next.z);
        // once it has come to rest it crumbles away almost at once – nothing is left lying around
        if (landedAt < 0 && tickCount > 3 && v.lengthSqr() < 0.004) landedAt = tickCount;
        boolean gone = tickCount >= life() || (landedAt >= 0 && tickCount >= landedAt + VANISH_TICKS);
        if (!level().isClientSide && gone) discard();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && !isRemoved()) alive = Math.max(0, alive - 1);
        super.remove(reason);
    }

    @Override
    public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(1); }

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
