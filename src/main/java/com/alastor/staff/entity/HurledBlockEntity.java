package com.alastor.staff.entity;

import com.alastor.staff.entity.ShadowFxEntity;
import com.alastor.staff.fx.ShadowFx;
import com.alastor.staff.registry.ModEntities;
import com.alastor.staff.registry.ModSounds;
import com.alastor.staff.util.Targeting;
import com.alastor.staff.util.TerrainBreaker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/** A chunk of ground ripped up by a tentacle; held aloft, then hurled to burst on impact. */
public class HurledBlockEntity extends Entity {
    private static final EntityDataAccessor<BlockState> STATE = SynchedEntityData.defineId(HurledBlockEntity.class, EntityDataSerializers.BLOCK_STATE);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(HurledBlockEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> THROWN = SynchedEntityData.defineId(HurledBlockEntity.class, EntityDataSerializers.BOOLEAN);

    @Nullable private UUID owner;

    public HurledBlockEntity(EntityType<? extends HurledBlockEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static HurledBlockEntity spawn(ServerLevel level, Vec3 at, BlockState state, float size, Player owner) {
        HurledBlockEntity e = ModEntities.HURLED_BLOCK.get().create(level);
        if (e == null) return null;
        e.setPos(at.x, at.y, at.z);
        e.entityData.set(STATE, state);
        e.entityData.set(SIZE, size);
        e.owner = owner.getUUID();
        level.addFreshEntity(e);
        return e;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(STATE, Blocks.DIRT.defaultBlockState());
        this.entityData.define(SIZE, 1.5f);
        this.entityData.define(THROWN, false);
    }

    public BlockState blockState() { return entityData.get(STATE); }
    public float size() { return entityData.get(SIZE); }
    public boolean thrown() { return entityData.get(THROWN); }

    /** Moves the held chunk (before it is thrown). */
    public void holdAt(Vec3 at) {
        setPos(at.x, at.y, at.z);
        setDeltaMovement(Vec3.ZERO);
    }

    public void hurl(Vec3 velocity) {
        entityData.set(THROWN, true);
        setDeltaMovement(velocity);
        hurtMarked = true;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (tickCount > 200) { discard(); return; }
        if (!thrown()) return;

        Vec3 v = getDeltaMovement().add(0, -0.045, 0);
        setDeltaMovement(v);
        Vec3 next = position().add(v);
        Player p = owner != null && level.getEntity(owner) instanceof Player pl ? pl : null;

        // hit something?
        boolean blocked = !level.getBlockState(BlockPos.containing(next)).getCollisionShape(level, BlockPos.containing(next)).isEmpty();
        boolean hitMob = p != null && !level.getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().move(v).inflate(size() * 0.5), x -> Targeting.isValid(p, x)).isEmpty();
        if (blocked || hitMob || next.y < level.getMinBuildHeight()) {
            impact(level, p);
            return;
        }
        setPos(next.x, next.y, next.z);
        level.sendParticles(ShadowFx.BLACK, getX(), getY(), getZ(), 3, 0.3, 0.3, 0.3, 0);
    }

    private void impact(ServerLevel level, @Nullable Player p) {
        Vec3 c = position();
        float sz = size();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, blockState()), c.x, c.y, c.z, (int) (60 * sz), sz, sz * 0.6, sz, 0.4);
        level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 2, sz * 0.5, 0.3, sz * 0.5, 0);
        ShadowFxEntity.spawn(level, ShadowFxEntity.RING, c, 3.5f + sz * 2f, 14, 8, 0f);
        ShadowFx.spread(level, c, 2f + sz, 30, 3);
        ModSounds.play(level, c, ModSounds.SHADOW_BOOM, 1.4f, 1.1f);
        if (p != null) {
            double r = 2.5 + sz;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r), x -> Targeting.isValid(p, x))) {
                e.invulnerableTime = 0;
                Targeting.hurt(p, e, 10f + 5f * sz);
                Vec3 out = e.position().subtract(c);
                Targeting.launch(e, (out.lengthSqr() > 1e-4 ? out.normalize() : new Vec3(0, 1, 0)).scale(1.2).add(0, 0.6, 0));
            }
            TerrainBreaker.smash(level, p, c, 1.6 + sz * 0.5, 10);
        }
        discard();
    }

    @Override
    public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(size()); }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) { return d < 128 * 128; }

    @Override
    public boolean isPickable() { return false; }

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
