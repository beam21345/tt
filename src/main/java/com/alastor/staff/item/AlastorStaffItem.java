package com.alastor.staff.item;

import com.alastor.staff.entity.ShadowFxEntity;
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

    public static boolean isRadioOn(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG_RADIO);
    }

    // ---------- Use ----------
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        ServerLevel server = (ServerLevel) level;
        CompoundTag tag = stack.getOrCreateTag();

        if (player.isShiftKeyDown()) {
            SkillType next = SkillType.byIndex(getSkill(stack).ordinal() + 1);
            tag.putInt(TAG_SKILL, next.ordinal());
            player.displayClientMessage(Component.translatable("msg.alastorstaff.selected",
                    Component.literal(String.valueOf(next.ordinal() + 1)), next.displayName()).withStyle(ChatFormatting.GREEN), true);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.6f);
            return InteractionResultHolder.success(stack);
        }

        SkillType skill = getSkill(stack);

        if (skill == SkillType.RADIO_DEMON) {
            boolean on = !tag.getBoolean(TAG_RADIO);
            tag.putBoolean(TAG_RADIO, on);
            player.displayClientMessage(Component.translatable(on ? "msg.alastorstaff.radio_on" : "msg.alastorstaff.radio_off")
                    .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY), true);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    on ? SoundEvents.WARDEN_EMERGE : SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, on ? 1.3f : 0.8f);
            if (on) ShadowFxEntity.spawn(server, ShadowFxEntity.SIGIL, player.position(), (float) RadioDemonAura.HALF, 40, 6, 0f);
            player.getCooldowns().addCooldown(this, skill.cooldown);
            return InteractionResultHolder.success(stack);
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
            player.getCooldowns().addCooldown(this, 10);
            player.displayClientMessage(Component.literal("✦ ").append(skill.displayName()).withStyle(ChatFormatting.DARK_GREEN), true);
        }
        return InteractionResultHolder.success(stack);
    }

    // ---------- Radio Demon aura runs while it is anywhere in the inventory ----------
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && entity instanceof Player player && isRadioOn(stack) && !player.isSpectator()) {
            RadioDemonAura.tick((ServerLevel) level, player);
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
        tooltip.add(Component.translatable("tooltip.alastorstaff.controls").withStyle(ChatFormatting.GRAY));
    }
}
