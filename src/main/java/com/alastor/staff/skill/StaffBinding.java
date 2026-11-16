package com.alastor.staff.skill;

import com.alastor.staff.AlastorStaffMod;
import com.alastor.staff.fx.ShadowFx;
import com.alastor.staff.item.AlastorStaffItem;
import com.alastor.staff.registry.ModItems;
import com.alastor.staff.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The staff is bound to its wielder's shadow: a key press summons it out of thin air or sends it
 * back. While "stored" it keeps its selected skill, cooldowns and Radio Demon state. Throwing the
 * staff away or dying sends it back into the shadow instead of dropping it.
 */
@Mod.EventBusSubscriber(modid = AlastorStaffMod.MODID)
public final class StaffBinding {
    private static final String STORED = "AlastorStoredStaff";

    private static CompoundTag persisted(Player p) {
        CompoundTag root = p.getPersistentData();
        CompoundTag tag = root.getCompound(Player.PERSISTED_NBT_TAG);
        root.put(Player.PERSISTED_NBT_TAG, tag);
        return tag;
    }

    private static void store(Player p, ItemStack staff) {
        CompoundTag copy = staff.copy().save(new CompoundTag());
        persisted(p).put(STORED, copy);
    }

    /** Summon if the player has no staff, otherwise dismiss it. */
    public static void toggle(Player p) {
        if (!(p.level() instanceof ServerLevel level)) return;
        Inventory inv = p.getInventory();

        // dismiss: any staff we carry melts back into the shadow
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof AlastorStaffItem) {
                store(p, s);
                inv.setItem(i, ItemStack.EMPTY);
                dismissFx(level, p);
                return;
            }
        }

        // summon
        CompoundTag pt = persisted(p);
        ItemStack staff = pt.contains(STORED) ? ItemStack.of(pt.getCompound(STORED)) : ItemStack.EMPTY;
        if (!(staff.getItem() instanceof AlastorStaffItem)) staff = new ItemStack(ModItems.ALASTOR_STAFF.get());

        if (p.getMainHandItem().isEmpty()) {
            inv.setItem(inv.selected, staff);
        } else {
            int free = inv.getFreeSlot();
            if (free >= 0) {
                inv.setItem(free, p.getMainHandItem().copy());
                inv.setItem(inv.selected, staff);
            } else if (p.getOffhandItem().isEmpty()) {
                inv.offhand.set(0, staff);
            } else {
                p.displayClientMessage(Component.translatable("msg.alastorstaff.inventory_full").withStyle(ChatFormatting.RED), true);
                return;
            }
        }
        pt.remove(STORED);
        AlastorStaffItem.markCast(staff, SkillType.CastAnim.RAISE, level.getGameTime());
        summonFx(level, p);
    }

    private static Vec3 hand(Player p) {
        double yaw = Math.toRadians(p.getYRot());
        return p.position().add(-Math.cos(yaw) * 0.4 - Math.sin(yaw) * 0.4, 1.2, -Math.sin(yaw) * 0.4 + Math.cos(yaw) * 0.4);
    }

    private static void summonFx(ServerLevel level, Player p) {
        Vec3 h = hand(p);
        ShadowFx.spread(level, p.position(), 2.5f, 30, 3);
        level.sendParticles(ParticleTypes.SQUID_INK, p.getX(), p.getY() + 0.2, p.getZ(), 25, 0.6, 0.05, 0.6, 0.05);
        level.sendParticles(ShadowFx.BLACK, h.x, h.y, h.z, 30, 0.25, 0.8, 0.25, 0);
        level.sendParticles(ShadowFx.GREEN, h.x, h.y + 1.2, h.z, 25, 0.25, 0.25, 0.25, 0);
        ShadowFx.zap(level, p.position().add(0, 0.1, 0), h.add(0, 1.4, 0), 6);
        ModSounds.play(level, h, ModSounds.RADIO_TUNE, 0.9f, 1.3f);
        ModSounds.play(level, h, ModSounds.TENTACLE_BURST, 0.5f, 1.6f);
    }

    private static void dismissFx(ServerLevel level, Player p) {
        Vec3 h = hand(p);
        ShadowFx.spread(level, p.position(), 2.0f, 24, 3);
        level.sendParticles(ShadowFx.BLACK, h.x, h.y, h.z, 35, 0.2, 0.9, 0.2, 0);
        level.sendParticles(ParticleTypes.SQUID_INK, h.x, h.y, h.z, 15, 0.2, 0.6, 0.2, 0.05);
        level.sendParticles(ShadowFx.GREEN_SMALL, h.x, h.y + 1, h.z, 10, 0.2, 0.4, 0.2, 0);
        ModSounds.play(level, h, ModSounds.RADIO_STATIC, 0.7f, 0.7f);
    }

    /** Throwing the staff away sends it back to the shadow (summon it again any time). */
    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        ItemEntity e = event.getEntity();
        Player p = event.getPlayer();
        if (e.getItem().getItem() instanceof AlastorStaffItem && p.level() instanceof ServerLevel level) {
            store(p, e.getItem());
            event.setCanceled(true);
            dismissFx(level, p);
        }
    }

    /** Dying never loses the staff. */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        event.getDrops().removeIf(drop -> {
            if (drop.getItem().getItem() instanceof AlastorStaffItem) {
                store(p, drop.getItem());
                return true;
            }
            return false;
        });
    }

    private StaffBinding() {}
}
