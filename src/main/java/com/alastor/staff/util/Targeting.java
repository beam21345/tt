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

    /** Finds the walkable ground height near (x, y, z), searching a few blocks up and down. */
    public static double groundY(Level level, double x, double y, double z) {
        BlockPos.MutableBlockPos p = BlockPos.containing(x, y + 2, z).mutable();
        for (int i = 0; i < 14; i++) {
            BlockPos below = p.below();
            boolean solidBelow = !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
            boolean freeHere = level.getBlockState(p).getCollisionShape(level, p).isEmpty();
            if (solidBelow && freeHere) return p.getY();
            p.move(0, -1, 0);
        }
        return Math.floor(y);
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
