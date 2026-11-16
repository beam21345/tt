package com.alastor.staff.item;

import com.alastor.staff.entity.ShadowFxEntity;
import com.alastor.staff.fx.ShadowFx;
import com.alastor.staff.skill.RadioDemonAura;
import com.alastor.staff.skill.SkillType;
import com.alastor.staff.skill.Skills;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Alastor Staff.
 * Right-click: use the selected skill. Sneak + right-click: switch to the next skill.
 */
public class AlastorStaffItem extends Item {
    private static final String TAG_SKILL = "AlastorSkill";
    private static final String TAG_RADIO = "RadioDemonOn";
    private static final String TAG_CD = "AlastorCd_";

    private final Multimap<Attribute, AttributeModifier> modifiers;

    public AlastorStaffItem(Properties props) {
        super(props);
        ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        b.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", 9.0, AttributeModifier.Operation.ADDITION));
        b.put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", -2.6, AttributeModifier.Operation.ADDITION));
        this.modifiers = b.build();
    }

    // ---------- NBT helpers ----------
    public static SkillType getSkill(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return SkillType.byIndex(tag == null ? 0 : tag.getInt(TAG_SKILL));
    }

    /** Ticks left before {@code skill} can be used again (0 = ready). */
    public static long cooldownRemaining(ItemStack stack, SkillType skill, long gameTime) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return 0;
        return Math.max(0, tag.getLong(TAG_CD + skill.id) - gameTime);
    }

    /** Switches to the next/previous skill with a little radio blip. Server side. */
    public static void cycleSkill(Player player, ItemStack stack, int direction) {
        if (!(player.level() instanceof ServerLevel server)) return;
        SkillType next = SkillType.byIndex(getSkill(stack).ordinal() + direction);
        stack.getOrCreateTag().putInt(TAG_SKILL, next.ordinal());
        player.displayClientMessage(Component.translatable("msg.alastorstaff.selected",
                Component.literal(String.valueOf(next.ordinal() + 1)), next.displayName()).withStyle(ChatFormatting.GREEN), true);
        server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 0.6f);
        com.alastor.staff.registry.ModSounds.play(server, player.position(), com.alastor.staff.registry.ModSounds.RADIO_STATIC, 0.35f, 1.6f + next.ordinal() * 0.05f);
    }

    /** Soul Harvest: knocks {@code ticks} off every running cooldown. */
    public static void reduceCooldowns(ItemStack stack, int ticks, long gameTime) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        for (SkillType s : SkillType.values()) {
            String key = TAG_CD + s.id;
            long ready = tag.getLong(key);
            if (ready > gameTime) tag.putLong(key, Math.max(gameTime, ready - ticks));
        }
    }

    // ---------- cast animation state (synced to every client through the held item) ----------
    private static final String TAG_CAST_AT = "CastAt";
    private static final String TAG_CAST_ANIM = "CastAnim";

    public static void markCast(ItemStack stack, int anim, long gameTime) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putLong(TAG_CAST_AT, gameTime);
        tag.putInt(TAG_CAST_ANIM, anim);
    }

    public static long castAt(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getLong(TAG_CAST_AT);
    }

    public static int castAnim(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(TAG_CAST_ANIM);
    }

    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(com.alastor.staff.client.StaffAnimations.INSTANCE);
    }

    /** Cooldown/animation data changes every cast – don't replay the "pull out" animation for that. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    public static boolean isRadioOn(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG_RADIO);
    }

    // ---------- Use ----------
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.consume(stack);
        ServerLevel server = (ServerLevel) level;
        CompoundTag tag = stack.getOrCreateTag();

        if (player.isShiftKeyDown()) {
            cycleSkill(player, stack, 1);
            return InteractionResultHolder.consume(stack);
        }
        SkillType skill = getSkill(stack);

        if (skill == SkillType.RADIO_DEMON) {
            boolean on = !tag.getBoolean(TAG_RADIO);
            tag.putBoolean(TAG_RADIO, on);
            player.displayClientMessage(Component.translatable(on ? "msg.alastorstaff.radio_on" : "msg.alastorstaff.radio_off")
                    .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY), true);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    on ? SoundEvents.WARDEN_EMERGE : SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, on ? 1.3f : 0.8f);
            com.alastor.staff.registry.ModSounds.play(server, player.position(), on ? com.alastor.staff.registry.ModSounds.RADIO_TUNE : com.alastor.staff.registry.ModSounds.RADIO_STATIC, 1.0f, on ? 1.0f : 0.7f);
            if (on) ShadowFx.spread(server, player.position(), (float) RadioDemonAura.HALF, 40, 6);
            player.getCooldowns().addCooldown(this, skill.cooldown);
            markCast(stack, skill.anim, level.getGameTime());
            return InteractionResultHolder.consume(stack);
        }

        long now = level.getGameTime();
        long ready = tag.getLong(TAG_CD + skill.id);
        if (now < ready) {
            long secs = (ready - now + 19) / 20;
            player.displayClientMessage(Component.translatable("msg.alastorstaff.cooldown", skill.displayName(),
                    Component.literal(String.valueOf(secs))).withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }

        if (Skills.cast(skill, server, player)) {
            tag.putLong(TAG_CD + skill.id, now + skill.cooldown);
            markCast(stack, skill.anim, now);
            player.getCooldowns().addCooldown(this, 10);
            player.displayClientMessage(Component.literal("✦ ").append(skill.displayName()).withStyle(ChatFormatting.DARK_GREEN), true);
        }
        return InteractionResultHolder.consume(stack);
    }

    // ---------- Radio Demon aura runs while it is anywhere in the inventory ----------
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && entity instanceof Player player && isRadioOn(stack) && !player.isSpectator()) {
            RadioDemonAura.tick((ServerLevel) level, player);
        }
        // faint green aura drifting off the staff while it is held
        if (level.isClientSide && selected && entity.tickCount % 3 == 0) {
            double yaw = Math.toRadians(entity.getYRot());
            double sx = entity.getX() - Math.cos(yaw) * 0.45 - Math.sin(yaw) * 0.3;
            double sz = entity.getZ() - Math.sin(yaw) * 0.45 + Math.cos(yaw) * 0.3;
            level.addParticle(com.alastor.staff.fx.ShadowFx.GREEN_SMALL,
                    sx + (level.random.nextDouble() - 0.5) * 0.3, entity.getY() + 1.6 + level.random.nextDouble() * 0.6,
                    sz + (level.random.nextDouble() - 0.5) * 0.3, 0, 0.02, 0);
        }
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? modifiers : super.getDefaultAttributeModifiers(slot);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isRadioOn(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack)).withStyle(ChatFormatting.DARK_GREEN);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        SkillType current = getSkill(stack);
        for (SkillType s : SkillType.values()) {
            boolean sel = s == current;
            tooltip.add(Component.literal((sel ? "▶ " : "   ") + (s.ordinal() + 1) + ". ")
                    .append(s.displayName())
                    .withStyle(sel ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable(isRadioOn(stack) ? "tooltip.alastorstaff.radio_on" : "tooltip.alastorstaff.radio_off")
                .withStyle(ChatFormatting.DARK_GREEN));
        tooltip.add(Component.translatable("tooltip.alastorstaff.passives").withStyle(ChatFormatting.DARK_GREEN));
        for (String k : new String[]{"cradle", "retaliation", "harvest", "sight"}) {
            tooltip.add(Component.literal(" • ").append(Component.translatable("tooltip.alastorstaff.passive." + k)).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.alastorstaff.controls").withStyle(ChatFormatting.GRAY));
    }
}
