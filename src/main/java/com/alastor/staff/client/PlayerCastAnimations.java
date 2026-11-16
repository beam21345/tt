package com.alastor.staff.client;

import com.alastor.staff.AlastorStaffMod;
import com.alastor.staff.item.AlastorStaffItem;
import com.alastor.staff.skill.SkillType.CastAnim;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Full-body cast animations (playerAnimator): when any player's staff records a new cast, the matching
 * keyframe animation (lean, step, crouch, arms) plays on that player for everyone who can see them.
 */
@Mod.EventBusSubscriber(modid = AlastorStaffMod.MODID, value = Dist.CLIENT)
public final class PlayerCastAnimations {
    private static final ResourceLocation LAYER = new ResourceLocation(AlastorStaffMod.MODID, "cast");
    private static final Map<UUID, Long> PLAYED = new HashMap<>();

    /** Called once during client setup. */
    public static void init() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, 42, player -> new ModifierLayer<IAnimation>());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        long now = mc.level.getGameTime();
        for (Player pl : mc.level.players()) {
            if (!(pl instanceof AbstractClientPlayer player)) continue;
            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof AlastorStaffItem)) stack = player.getOffhandItem();
            if (!(stack.getItem() instanceof AlastorStaffItem)) continue;
            long at = AlastorStaffItem.castAt(stack);
            if (at <= 0 || now - at > 6) continue;
            Long last = PLAYED.get(player.getUUID());
            if (last != null && last == at) continue;
            PLAYED.put(player.getUUID(), at);
            play(player, AlastorStaffItem.castAnim(stack));
        }
        if (now % 600 == 0) PLAYED.keySet().removeIf(id -> mc.level.getPlayerByUUID(id) == null);
    }

    @SuppressWarnings("unchecked")
    private static void play(AbstractClientPlayer player, int anim) {
        String name = switch (anim) {
            case CastAnim.PLANT -> "cast_plant";
            case CastAnim.RAISE -> "cast_raise";
            default -> "cast_point";
        };
        KeyframeAnimation data = PlayerAnimationRegistry.getAnimation(new ResourceLocation(AlastorStaffMod.MODID, name));
        if (data == null) return;
        Object layer = PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER);
        if (layer instanceof ModifierLayer<?> ml) {
            ((ModifierLayer<IAnimation>) ml).replaceAnimationWithFade(
                    AbstractFadeModifier.standardFadeIn(3, Ease.INOUTSINE), new KeyframeAnimationPlayer(data));
        }
    }

    private PlayerCastAnimations() {}
}
