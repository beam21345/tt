package com.alastor.staff.skill;

import com.alastor.staff.AlastorStaffMod;
import com.alastor.staff.entity.ShadowFxEntity;
import com.alastor.staff.fx.ShadowFx;
import com.alastor.staff.item.AlastorStaffItem;
import com.alastor.staff.registry.ModSounds;
import com.alastor.staff.skill.task.CatchTask;
import com.alastor.staff.skill.task.SkillTaskManager;
import com.alastor.staff.skill.task.TentacleTask;
import com.alastor.staff.util.Targeting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Passive abilities of the Alastor Staff.
 * <ul>
 *   <li>Shadow Cradle (staff anywhere in inventory): tentacles catch you before a hard landing; no fall damage.</li>
 *   <li>Shadow Retaliation (staff in hand): incoming hits are 25% weaker and a tentacle strikes the attacker.</li>
 *   <li>Soul Harvest (staff in hand): every kill heals you and shortens all skill cooldowns by 1 second.</li>
 *   <li>Shadow Sight (staff in hand): you see in the dark.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AlastorStaffMod.MODID)
public final class StaffPassives {
    private static final Map<UUID, Long> CATCH_CD = new HashMap<>();
    private static final Map<UUID, Long> RETALIATE_CD = new HashMap<>();

    public static boolean hasStaff(Player p) {
        for (ItemStack s : p.getInventory().items) if (s.getItem() instanceof AlastorStaffItem) return true;
        return p.getOffhandItem().getItem() instanceof AlastorStaffItem;
    }

    public static ItemStack heldStaff(Player p) {
        if (p.getMainHandItem().getItem() instanceof AlastorStaffItem) return p.getMainHandItem();
        if (p.getOffhandItem().getItem() instanceof AlastorStaffItem) return p.getOffhandItem();
        return ItemStack.EMPTY;
    }

    // ---------------- Shadow Cradle ----------------
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player.level() instanceof ServerLevel level)) return;
        Player p = event.player;
        // Shadow Sight
        if (p.tickCount % 80 == 0 && !heldStaff(p).isEmpty()) {
            p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, true, false, false));
        }
        if (p.isSpectator() || p.getAbilities().flying || p.isFallFlying() || p.onGround() || p.isInWater()) return;

        if (p.getDeltaMovement().y > -0.7 || p.fallDistance < 6f) return;
        long now = level.getGameTime();
        Long ready = CATCH_CD.get(p.getUUID());
        if (ready != null && ready > now) return;
        if (!hasStaff(p)) return;

        // ground within reach of the next few ticks of falling?
        BlockPos.MutableBlockPos m = p.blockPosition().mutable();
        for (int i = 0; i < 5; i++) {
            m.move(0, -1, 0);
            if (!level.getBlockState(m).getCollisionShape(level, m).isEmpty()) {
                CATCH_CD.put(p.getUUID(), now + 30);
                Vec3 ground = new Vec3(p.getX(), m.getY() + 1, p.getZ());
                SkillTaskManager.add(level, new CatchTask(p, ground));
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player p && event.getDistance() > 3f && hasStaff(p)) {
            event.setDamageMultiplier(0f);
            if (p.level() instanceof ServerLevel level && event.getDistance() > 6f) {
                level.sendParticles(ParticleTypes.SQUID_INK, p.getX(), p.getY() + 0.1, p.getZ(), 15, 0.8, 0.05, 0.8, 0.05);
            }
        }
    }

    /** Nothing can touch you while you travel as a shadow. */
    @SubscribeEvent
    public static void onAttacked(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (event.getEntity() instanceof Player p && com.alastor.staff.skill.task.ShadowStepTask.TRAVELLING.contains(p.getUUID())) {
            event.setCanceled(true);
        }
    }

    // ---------------- Shadow Retaliation ----------------
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player p) || !(p.level() instanceof ServerLevel level)) return;
        if (heldStaff(p).isEmpty()) return;
        Entity src = event.getSource().getEntity();
        if (!(src instanceof LivingEntity attacker) || attacker == p || !Targeting.isValid(p, attacker)) return;

        event.setAmount(event.getAmount() * 0.75f);
        level.sendParticles(ShadowFx.BLACK, p.getX(), p.getY() + 1, p.getZ(), 12, 0.4, 0.6, 0.4, 0);

        long now = level.getGameTime();
        Long ready = RETALIATE_CD.get(p.getUUID());
        if (ready != null && ready > now) return;
        RETALIATE_CD.put(p.getUUID(), now + 60);
        Double g = Targeting.findGround(level, attacker.getX(), attacker.getY(), attacker.getZ());
        if (g != null) {
            double h = Math.max(6.0, attacker.getY() - g + 4.0);
            SkillTaskManager.add(level, new TentacleTask(p, new Vec3(attacker.getX(), g, attacker.getZ()), 2, h, 7f, 1.6));
        }
        ShadowFx.zap(level, p.position().add(0, 1.2, 0), attacker.position().add(0, attacker.getBbHeight() * 0.5, 0), 6);
    }

    // ---------------- Soul Harvest ----------------
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Player p) || !(p.level() instanceof ServerLevel level)) return;
        LivingEntity victim = event.getEntity();
        if (victim == p) return;
        ItemStack staff = heldStaff(p);
        if (staff.isEmpty()) return;

        p.heal(2f);
        AlastorStaffItem.reduceCooldowns(staff, 20, level.getGameTime());
        Vec3 from = victim.position().add(0, victim.getBbHeight() * 0.5, 0);
        ShadowFxEntity.bolt(level, from, p.position().add(0, 1.2, 0), false, 6);
        level.sendParticles(ParticleTypes.SCULK_SOUL, from.x, from.y, from.z, 6, 0.3, 0.3, 0.3, 0.05);
        level.sendParticles(ShadowFx.GREEN, p.getX(), p.getY() + 1, p.getZ(), 10, 0.4, 0.6, 0.4, 0);
        ModSounds.play(level, p.position(), ModSounds.RADIO_STATIC, 0.4f, 1.8f);
    }

    private StaffPassives() {}
}
