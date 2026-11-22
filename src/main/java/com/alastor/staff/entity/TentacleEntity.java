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
    private static final EntityDataAccessor<Integer> WEAPON = SynchedEntityData.defineId(TentacleEntity.class, EntityDataSerializers.INT);

    /** What the tentacle carries at its tip. */
    public static final int PLAIN = 0, SWORD = 1, AXE = 2, SPEAR = 3;

    public static final int FALL_TICKS = 7;

    /** Server only: whose tentacle this is, if it should tear through blocks it touches. */
    @javax.annotation.Nullable private java.util.UUID destroyer;
    private float[] serverSpine;

    /** Lets this tentacle smash blocks along its body (subject to the terrain config). */
    public void setDestroyer(net.minecraft.world.entity.player.Player owner) {
        this.destroyer = owner.getUUID();
    }

    /** Number of segments along the tentacle's spine. */
    public static final int SEGMENTS = 30;

    // client-side smoothing of the tip between server updates
    private float prevTipX, prevTipY, prevTipZ, curTipX, curTipY, curTipZ;
    private boolean tipInit;
    // client-side spring chain: each spine point chases its target shape with some lag, so the
    // tentacle moves with weight – the root leads, the tip follows through and whips
    private float[] simCur, simPrev, simVel, target;

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
        this.entityData.define(WEAPON, PLAIN);
    }

    public void setWeapon(int weapon) { this.entityData.set(WEAPON, weapon); }

    public int weapon() { return this.entityData.get(WEAPON); }

    public void setTip(Vec3 tip) {
        this.entityData.set(TIP_X, (float) tip.x);
        this.entityData.set(TIP_Y, (float) tip.y);
        this.entityData.set(TIP_Z, (float) tip.z);
    }

    /** Points the tip at a world position. */
    public void reachTo(Vec3 worldPos) {
        setTip(worldPos.subtract(position()));
    }

    /** Keeps the tentacle out for at least another {@code ticks} ticks (used by followers). */
    public void keepAlive(int ticks) {
        int end = this.tickCount + ticks + FALL_TICKS;
        if (end > getLife()) this.entityData.set(LIFE, end);
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

    /** 0..1+ how far the tentacle is out of the ground: springs up with a slight overshoot, eases back down. */
    public float extension(float age) {
        int rise = entityData.get(RISE);
        if (age < rise) {
            float t = age / rise - 1f;
            return 1f + 2.70158f * t * t * t + 1.70158f * t * t; // ease-out-back
        }
        float remaining = getLife() - age;
        if (remaining < FALL_TICKS) {
            float t = Math.max(0f, remaining / FALL_TICKS);
            return t * t * (3f - 2f * t); // smoothstep
        }
        return 1f;
    }

    /** Tip position smoothly interpolated between server ticks (client side). */
    public float lerpTipX(float pt) { return tipInit ? prevTipX + (curTipX - prevTipX) * pt : tipX(); }
    public float lerpTipY(float pt) { return tipInit ? prevTipY + (curTipY - prevTipY) * pt : tipY(); }
    public float lerpTipZ(float pt) { return tipInit ? prevTipZ + (curTipZ - prevTipZ) * pt : tipZ(); }

    /** 0..1 strength of the green summoning light: full while emerging, then fades to pure black. */
    public float glow(float age) {
        int rise = entityData.get(RISE);
        if (age < rise) return 1f;
        return Math.max(0f, 1f - (age - rise) / 12f);
    }

    /**
     * The shape the tentacle wants to have at {@code age}: a wave travelling from root to tip and a
     * hooked, curling tip. Writes SEGMENTS+1 points (x,y,z relative to the root) into {@code out}.
     */
    private void targetCurve(float age, float tx, float ty, float tz, float[] out) {
        float len = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
        float ext = extension(age);
        if (len < 0.05f) { java.util.Arrays.fill(out, 0f); return; }
        float dx = tx / len, dy = ty / len, dz = tz / len;
        // side = dir x up, side2 = dir x side
        float sx = -dz, sy = 0, sz = dx;
        float sl = (float) Math.sqrt(sx * sx + sz * sz);
        if (sl < 1.0E-3f) { sx = 1; sz = 0; sl = 1; }
        sx /= sl; sz /= sl;
        float s2x = dy * sz - dz * sy, s2y = dz * sx - dx * sz, s2z = dx * sy - dy * sx;
        float phase = phase();
        float amp = Math.min(1.8f, 0.13f * len);
        float curl = Math.min(2.2f, 0.16f * len);
        float reach = len * ext;
        for (int i = 0; i <= SEGMENTS; i++) {
            float s = (float) i / SEGMENTS;
            float env = (float) Math.pow(s, 1.3);
            float wave = (float) Math.sin(s * Math.PI * 2.0 - age * 0.22 + phase) * amp * env;
            float wave2 = (float) Math.cos(s * Math.PI * 1.4 - age * 0.15 + phase * 1.7) * amp * 0.6f * env;
            float hook = s > 0.7f ? (float) Math.pow((s - 0.7f) / 0.3f, 2) : 0f;
            float curlNow = curl * hook * (0.75f + 0.25f * (float) Math.sin(age * 0.12 + phase));
            float along = reach * s - curlNow * 0.35f * hook;
            float side = wave + curlNow;
            out[i * 3] = dx * along + sx * side + s2x * wave2;
            out[i * 3 + 1] = dy * along + sy * side + s2y * wave2;
            out[i * 3 + 2] = dz * along + sz * side + s2z * wave2;
        }
    }

    private void clientSim() {
        int n = (SEGMENTS + 1) * 3;
        if (simCur == null) {
            simCur = new float[n]; simPrev = new float[n]; simVel = new float[n]; target = new float[n];
            targetCurve(tickCount, curTipX, curTipY, curTipZ, target);
            System.arraycopy(target, 0, simCur, 0, n);
            System.arraycopy(target, 0, simPrev, 0, n);
            return;
        }
        System.arraycopy(simCur, 0, simPrev, 0, n);
        targetCurve(tickCount, curTipX, curTipY, curTipZ, target);
        for (int i = 0; i <= SEGMENTS; i++) {
            float s = (float) i / SEGMENTS;
            float k = 0.7f - 0.45f * s;      // stiff root, loose tip
            for (int c = 0; c < 3; c++) {
                int idx = i * 3 + c;
                if (i == 0) { simCur[idx] = target[idx]; simVel[idx] = 0; continue; }
                float v = simVel[idx] * 0.62f + (target[idx] - simCur[idx]) * k;
                simVel[idx] = v;
                simCur[idx] += v;
            }
        }
    }

    /** Interpolated spine point i (relative to the root) for rendering; null before the first client tick. */
    public boolean hasSpine() { return simCur != null; }
    public float spineX(int i, float pt) { return simPrev[i * 3] + (simCur[i * 3] - simPrev[i * 3]) * pt; }
    public float spineY(int i, float pt) { return simPrev[i * 3 + 1] + (simCur[i * 3 + 1] - simPrev[i * 3 + 1]) * pt; }
    public float spineZ(int i, float pt) { return simPrev[i * 3 + 2] + (simCur[i * 3 + 2] - simPrev[i * 3 + 2]) * pt; }

    private Vec3 spineWorld(float[] arr, int i) {
        return position().add(arr[i * 3], arr[i * 3 + 1], arr[i * 3 + 2]);
    }

    /** Glow, smoke and motion streaks (Lodestone), client side only. */
    private void clientFx() {
        if (simCur == null) return;
        Level level = level();
        float th = thickness();
        int rise = entityData.get(RISE);
        if (tickCount <= rise + 2) {
            for (int k = 0; k < 3; k++) {
                com.alastor.staff.fx.LodestoneFx.glow(level, getX(), getY() + 0.2, getZ(), 0.35f + 0.35f * th, 14, 0.4 * th, 0.03);
            }
            if (tickCount == 1) com.alastor.staff.fx.LodestoneFx.sparks(level, getX(), getY() + 0.3, getZ(), 8, 0.18f, false);
        }
        if (extension(tickCount) > 0.3f) {
            if (tickCount % 3 == 0) {
                int i = 2 + random.nextInt(Math.max(1, SEGMENTS / 2));
                Vec3 p = spineWorld(simCur, i);
                com.alastor.staff.fx.LodestoneFx.shadowSmoke(level, p.x, p.y, p.z, 0.35f + 0.3f * th, 0.2 * th);
            }
            if (tickCount % 6 == 0) com.alastor.staff.fx.LodestoneFx.shadowSmoke(level, getX(), getY() + 0.1, getZ(), 0.5f + 0.4f * th, 0.6 * th);
            // fast swings leave a streak behind the tip (or the weapon's edge)
            Vec3 a = spineWorld(simPrev, SEGMENTS), b = spineWorld(simCur, SEGMENTS);
            if (a.distanceToSqr(b) > 0.25) {
                if (weapon() != PLAIN) {
                    Vec3 da = a.subtract(spineWorld(simPrev, SEGMENTS - 2)).normalize();
                    Vec3 db = b.subtract(spineWorld(simCur, SEGMENTS - 2)).normalize();
                    double reach = th * 2.3 * 1.25;
                    com.alastor.staff.fx.LodestoneFx.streak(level, a.add(da.scale(reach)), b.add(db.scale(reach)), 0.25f * th, false);
                }
                com.alastor.staff.fx.LodestoneFx.streak(level, a, b, 0.2f + 0.15f * th, true);
            }
        }
    }

    /** Server: tear through any breakable block the tentacle's body passes through. */
    private void smashAlongBody(net.minecraft.server.level.ServerLevel sl) {
        if (destroyer == null || extension(tickCount) < 0.15f) return;
        if (!(sl.getPlayerByUUID(destroyer) instanceof net.minecraft.world.entity.player.Player owner)) return;
        if (serverSpine == null) serverSpine = new float[(SEGMENTS + 1) * 3];
        targetCurve(tickCount, tipX(), tipY(), tipZ(), serverSpine);
        float th = thickness();
        int budget = 14;
        // root: the tentacle cracks the ground open as it bursts out
        if (tickCount == 2) budget -= com.alastor.staff.util.TerrainBreaker.smash(sl, owner, position().add(0, -0.3, 0), 0.6 + th * 0.45, 6);
        for (int i = 3; i <= SEGMENTS && budget > 0; i += 3) {
            float s = (float) i / SEGMENTS;
            double r = Math.max(0.6, th * 0.55 * (1.0 - 0.8 * s) + 0.35);
            Vec3 p = position().add(serverSpine[i * 3], serverSpine[i * 3 + 1], serverSpine[i * 3 + 2]);
            if (p.y < getY() + 0.5) continue;   // don't dig below its own root
            budget -= com.alastor.staff.util.TerrainBreaker.smash(sl, owner, p, r, Math.min(4, budget));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount % 2 == 0 && level() instanceof net.minecraft.server.level.ServerLevel sl) smashAlongBody(sl);
        if (level().isClientSide) {
            if (!tipInit) {
                curTipX = tipX(); curTipY = tipY(); curTipZ = tipZ();
                tipInit = true;
            }
            prevTipX = curTipX; prevTipY = curTipY; prevTipZ = curTipZ;
            // ease toward the synced tip so motion stays fluid
            curTipX += (tipX() - curTipX) * 0.6f;
            curTipY += (tipY() - curTipY) * 0.6f;
            curTipZ += (tipZ() - curTipZ) * 0.6f;
            clientSim();
            clientFx();
        }
        if (!level().isClientSide && this.tickCount == 1 && level() instanceof net.minecraft.server.level.ServerLevel sl) {
            sl.sendParticles(com.alastor.staff.fx.ShadowFx.GREEN, getX(), getY() + 0.2, getZ(), 14, 0.45, 0.15, 0.45, 0);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.GLOW, getX(), getY() + 0.3, getZ(), 6, 0.4, 0.3, 0.4, 0.02);
            float th = thickness();
            net.minecraft.world.level.block.state.BlockState ground = sl.getBlockState(blockPosition().below());
            if (!ground.isAir()) {
                sl.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, ground),
                        getX(), getY() + 0.2, getZ(), (int) (12 + 18 * th), 0.5 * th, 0.15, 0.5 * th, 0.3);
            }
            if (th >= 0.9f) com.alastor.staff.registry.ModSounds.play(sl, position(), com.alastor.staff.registry.ModSounds.TENTACLE_BURST, 0.6f + 0.3f * th, 1.25f - 0.2f * Math.min(th, 2f) + random.nextFloat() * 0.15f);
        }
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
