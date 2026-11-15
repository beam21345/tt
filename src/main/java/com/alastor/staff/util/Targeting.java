package com.alastor.staff.util;

import com.alastor.staff.entity.ShadowDemonEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Who the staff's skills may hit, plus small shared helpers. */
public final class Targeting {

    /** Anything alive except the caster, the caster's demons/pets, armor stands and creative/spectator players. */
    public static boolean isValid(Player owner, Entity e) {
        if (!(e instanceof LivingEntity living) || !living.isAlive() || e == owner) return false;
        if (e instanceof ArmorStand) return false;
        if (e instanceof ShadowDemonEntity demon && demon.isOwnedBy(owner)) return false;
        if (e instanceof TamableAnimal pet && pet.isOwnedBy(owner)) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        return !e.isAlliedTo(owner);
    }

    public static void hurt(Player owner, LivingEntity target, float amount) {
        target.hurt(owner.damageSources().indirectMagic(owner, owner), amount);
    }

    public static void launch(Entity e, Vec3 velocity) {
        e.setDeltaMovement(velocity);
        e.hurtMarked = true; // forces the velocity to sync to clients (needed for players)
    }

    /**
     * Finds the real ground surface under/near (x, y, z): looks up to 6 blocks up (for slopes and
     * cliffs ahead) and up to 48 blocks down (for drops). Returns null over the void.
     */
    @javax.annotation.Nullable
    public static Double findGround(Level level, double x, double y, double z) {
        return findGround(level, x, y, z, 6, 48);
    }

    @javax.annotation.Nullable
    public static Double findGround(Level level, double x, double y, double z, int up, int down) {
        BlockPos.MutableBlockPos p = BlockPos.containing(x, y + up, z).mutable();
        for (int i = 0; i < up + down; i++) {
            if (p.getY() <= level.getMinBuildHeight()) break;
            BlockPos below = p.below();
            boolean freeHere = level.getBlockState(p).getCollisionShape(level, p).isEmpty();
            boolean solidBelow = !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
            if (freeHere && solidBelow) return (double) p.getY();
            p.move(0, -1, 0);
        }
        return null;
    }

    /** Ground height near (x, y, z), or y itself if there is no ground at all. */
    public static double groundY(Level level, double x, double y, double z) {
        Double g = findGround(level, x, y, z);
        return g != null ? g : Math.floor(y);
    }

    public static Vec3 horizontalLook(Player p) {
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0E-4) {
            double yaw = Math.toRadians(p.getYRot());
            flat = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        }
        return flat.normalize();
    }

    private Targeting() {}
}
