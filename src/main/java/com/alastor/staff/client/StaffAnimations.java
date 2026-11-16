package com.alastor.staff.client;

import com.alastor.staff.item.AlastorStaffItem;
import com.alastor.staff.skill.SkillType.CastAnim;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.IArmPoseTransformer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * How the caster holds and swings the staff.
 * Third person: a wizard's stance (staff held upright in front), plus three cast motions –
 * POINT (rear back, then level the staff at the target), PLANT (both hands lift it and drive it into
 * the ground) and RAISE (hold it high, free hand thrown out). First person gets matching motions.
 */
public final class StaffAnimations implements IClientItemExtensions {
    public static final StaffAnimations INSTANCE = new StaffAnimations();

    private static final HumanoidModel.ArmPose STAFF_POSE =
            HumanoidModel.ArmPose.create("ALASTOR_STAFF", false, (IArmPoseTransformer) StaffAnimations::poseArms);

    private static final float IDLE_X = -1.30f;

    @Override
    public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        return STAFF_POSE;
    }

    // ------------------------------------------------------------------ timeline helpers

    /** Ticks since the last cast (with partial ticks), or -1 when no cast is playing. */
    private static float castTime(LivingEntity e, ItemStack stack) {
        if (stack.isEmpty() || e.level() == null) return -1;
        long at = AlastorStaffItem.castAt(stack);
        if (at <= 0) return -1;
        float t = (e.level().getGameTime() - at) + Minecraft.getInstance().getFrameTime();
        return t >= 0 && t < 24 ? t : -1;
    }

    /** Smoothly interpolates between keyframes (times ascending). */
    private static float key(float t, float[] times, float[] values) {
        if (t <= times[0]) return values[0];
        for (int i = 1; i < times.length; i++) {
            if (t <= times[i]) {
                float f = (t - times[i - 1]) / (times[i] - times[i - 1]);
                f = f * f * (3 - 2 * f);
                return values[i - 1] + (values[i] - values[i - 1]) * f;
            }
        }
        return values[values.length - 1];
    }

    // ------------------------------------------------------------------ third person

    private static void poseArms(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        boolean right = arm == HumanoidArm.RIGHT;
        ModelPart main = right ? model.rightArm : model.leftArm;
        ModelPart off = right ? model.leftArm : model.rightArm;
        float side = right ? 1f : -1f;   // mirror yaw/roll for the left hand
        ItemStack stack = right == (entity.getMainArm() == HumanoidArm.RIGHT) ? entity.getMainHandItem() : entity.getOffhandItem();

        // wizard stance: forearm forward, staff standing upright in front, gentle sway
        float sway = Mth.sin(entity.tickCount * 0.07f) * 0.04f;
        main.xRot = IDLE_X + main.xRot * 0.3f + sway;
        main.yRot = -0.2f * side;
        main.zRot = 0.05f * side;

        float t = castTime(entity, stack);
        if (t < 0) return;
        switch (AlastorStaffItem.castAnim(stack)) {
            case CastAnim.POINT -> {
                main.xRot = key(t, new float[]{0, 3, 6, 12, 16}, new float[]{IDLE_X, -2.1f, -0.25f, -0.25f, IDLE_X});
                main.yRot = -0.2f * side;
                off.xRot = key(t, new float[]{0, 3, 6, 12, 16}, new float[]{0, -0.6f, -0.9f, -0.9f, 0});
                off.zRot = key(t, new float[]{0, 3, 16}, new float[]{0, -0.45f * side, 0});
            }
            case CastAnim.PLANT -> {
                float x = key(t, new float[]{0, 5, 8, 14, 18}, new float[]{IDLE_X, -2.4f, -0.95f, -0.95f, IDLE_X});
                main.xRot = x;
                main.yRot = -0.1f * side;
                off.xRot = x + 0.1f;
                off.yRot = key(t, new float[]{0, 3, 15, 18}, new float[]{0, 0.45f * side, 0.45f * side, 0});
                off.zRot = 0;
            }
            case CastAnim.RAISE -> {
                main.xRot = key(t, new float[]{0, 6, 16, 20}, new float[]{IDLE_X, -2.2f, -2.2f, IDLE_X});
                main.zRot = key(t, new float[]{0, 6, 16, 20}, new float[]{0.05f * side, 0.3f * side, 0.3f * side, 0.05f * side});
                off.xRot = key(t, new float[]{0, 6, 16, 20}, new float[]{0, -1.5f, -1.5f, 0});
                off.zRot = key(t, new float[]{0, 6, 16, 20}, new float[]{0, -0.95f * side, -0.95f * side, 0});
            }
            default -> {}
        }
    }

    // ------------------------------------------------------------------ first person

    @Override
    public boolean applyForgeHandTransform(PoseStack ps, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                           float partialTick, float equipProcess, float swingProcess) {
        int i = arm == HumanoidArm.RIGHT ? 1 : -1;
        // vanilla "item in hand" placement
        ps.translate(i * 0.56f, -0.52f + equipProcess * -0.6f, -0.72f);

        float bob = Mth.sin((player.tickCount + partialTick) * 0.08f) * 0.012f;
        ps.translate(0, bob, 0);

        float t = castTime(player, stack);
        if (t >= 0) {
            switch (AlastorStaffItem.castAnim(stack)) {
                case CastAnim.POINT -> {
                    float back = key(t, new float[]{0, 3, 6, 16}, new float[]{0, 1, 0, 0});
                    float aim = key(t, new float[]{0, 3, 6, 12, 16}, new float[]{0, 0, 1, 1, 0});
                    ps.translate(-i * 0.08f * aim, 0.1f * back + 0.12f * aim, -0.25f * aim);
                    ps.mulPose(Axis.XP.rotationDegrees(25f * back - 70f * aim));
                }
                case CastAnim.PLANT -> {
                    float lift = key(t, new float[]{0, 5, 8, 18}, new float[]{0, 1, 0, 0});
                    float slam = key(t, new float[]{0, 5, 8, 14, 18}, new float[]{0, 0, 1, 1, 0});
                    ps.translate(-i * 0.2f * (lift + slam), 0.3f * lift - 0.35f * slam, -0.15f * slam);
                    ps.mulPose(Axis.XP.rotationDegrees(-15f * slam));
                }
                case CastAnim.RAISE -> {
                    float up = key(t, new float[]{0, 6, 16, 20}, new float[]{0, 1, 1, 0});
                    ps.translate(-i * 0.15f * up, 0.4f * up, -0.1f * up);
                    ps.mulPose(Axis.ZP.rotationDegrees(-i * 12f * up));
                }
                default -> {}
            }
        } else if (swingProcess > 0) {
            // regular melee swing
            float f = Mth.sin(swingProcess * swingProcess * Mth.PI);
            float f1 = Mth.sin(Mth.sqrt(swingProcess) * Mth.PI);
            ps.mulPose(Axis.YP.rotationDegrees(i * (45f + f * -20f)));
            ps.mulPose(Axis.ZP.rotationDegrees(i * f1 * -20f));
            ps.mulPose(Axis.XP.rotationDegrees(f1 * -80f));
            ps.mulPose(Axis.YP.rotationDegrees(i * -45f));
        }
        return true;
    }

    private StaffAnimations() {}
}
