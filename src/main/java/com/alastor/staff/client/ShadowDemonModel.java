package com.alastor.staff.client;

import com.alastor.staff.AlastorStaffMod;
import com.alastor.staff.entity.ShadowDemonEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Original shadow demon: a tall, hunched, gaunt silhouette with curved horns, a long neck,
 * spikes along the spine, very long arms ending in three hooked claws, and thin digitigrade legs.
 * Animated: breathing hunch, swaying arms, prowling walk, claw swipe when attacking.
 */
public class ShadowDemonModel extends HierarchicalModel<ShadowDemonEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(AlastorStaffMod.MODID, "shadow_demon"), "main");

    private final ModelPart root, body, neck, head, jaw;
    private final ModelPart rightArm, leftArm, rightForearm, leftForearm, rightHand, leftHand;
    private final ModelPart rightLeg, leftLeg, rightShin, leftShin;
    private final ModelPart hornR, hornL;

    public ShadowDemonModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.neck = body.getChild("neck");
        this.head = neck.getChild("head");
        this.jaw = head.getChild("jaw");
        this.hornR = head.getChild("horn_r");
        this.hornL = head.getChild("horn_l");
        this.rightArm = body.getChild("right_arm");
        this.leftArm = body.getChild("left_arm");
        this.rightForearm = rightArm.getChild("forearm");
        this.leftForearm = leftArm.getChild("forearm");
        this.rightHand = rightForearm.getChild("hand");
        this.leftHand = leftForearm.getChild("hand");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
        this.rightShin = rightLeg.getChild("shin");
        this.leftShin = leftLeg.getChild("shin");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Torso pivots at the hips (y=6) and extends upward.
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(0, 16).addBox(-4.5f, -15f, -2.5f, 9f, 15f, 5f)          // ribcage
                        .texOffs(28, 36).addBox(-3f, -3f, -2f, 6f, 4f, 4f)                 // narrow waist
                        .texOffs(52, 16).addBox(-0.5f, -15f, 2.5f, 1f, 3f, 2f)             // spine spikes
                        .texOffs(52, 16).addBox(-0.5f, -10.5f, 2.5f, 1f, 3f, 2f)
                        .texOffs(52, 16).addBox(-0.5f, -6f, 2.5f, 1f, 3f, 2f),
                PartPose.offsetAndRotation(0f, 6f, 0f, 0.45f, 0f, 0f));

        PartDefinition neck = body.addOrReplaceChild("neck",
                CubeListBuilder.create().texOffs(56, 24).addBox(-1f, -4f, -1f, 2f, 4f, 2f),
                PartPose.offsetAndRotation(0f, -15f, -0.5f, -0.25f, 0f, 0f));

        PartDefinition head = neck.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4f, -7f, -4.5f, 8f, 7f, 8f),
                PartPose.offsetAndRotation(0f, -3.5f, 0f, -0.2f, 0f, 0f));
        head.addOrReplaceChild("jaw",
                CubeListBuilder.create().texOffs(32, 52).addBox(-3.5f, 0f, -4.5f, 7f, 2f, 6f),
                PartPose.offset(0f, -0.5f, 0f));

        // Curved horns: base sweeps outward/back, tip curls up.
        PartDefinition hornR = head.addOrReplaceChild("horn_r",
                CubeListBuilder.create().texOffs(40, 16).addBox(-1f, -4f, -1f, 2f, 4f, 2f),
                PartPose.offsetAndRotation(-3f, -6.5f, -1f, -0.6f, 0f, -0.7f));
        hornR.addOrReplaceChild("tip",
                CubeListBuilder.create().texOffs(48, 16).addBox(-0.5f, -4f, -0.5f, 1f, 4f, 1f),
                PartPose.offsetAndRotation(0f, -3.6f, 0f, -0.7f, 0f, 0.5f));
        PartDefinition hornL = head.addOrReplaceChild("horn_l",
                CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-1f, -4f, -1f, 2f, 4f, 2f),
                PartPose.offsetAndRotation(3f, -6.5f, -1f, -0.6f, 0f, 0.7f));
        hornL.addOrReplaceChild("tip",
                CubeListBuilder.create().texOffs(48, 16).mirror().addBox(-0.5f, -4f, -0.5f, 1f, 4f, 1f),
                PartPose.offsetAndRotation(0f, -3.6f, 0f, -0.7f, 0f, -0.5f));

        armDef(body, "right_arm", -5.5f, false);
        armDef(body, "left_arm", 5.5f, true);
        legDef(root, "right_leg", -2.2f, false);
        legDef(root, "left_leg", 2.2f, true);
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static void armDef(PartDefinition body, String name, float x, boolean mirror) {
        CubeListBuilder upper = CubeListBuilder.create().texOffs(32, 0);
        if (mirror) upper.mirror();
        upper.addBox(-1.5f, -1f, -1.5f, 3f, 12f, 3f)
                .texOffs(40, 24).addBox(x < 0 ? -2.5f : -0.5f, -2.5f, -1.5f, 3f, 3f, 3f); // bony shoulder knob
        PartDefinition arm = body.addOrReplaceChild(name, upper,
                PartPose.offsetAndRotation(x, -13f, 0f, 0f, 0f, x < 0 ? 0.15f : -0.15f));

        CubeListBuilder lower = CubeListBuilder.create().texOffs(44, 0);
        if (mirror) lower.mirror();
        lower.addBox(-1f, 0f, -1f, 2f, 13f, 2f);
        PartDefinition fore = arm.addOrReplaceChild("forearm", lower, PartPose.offset(0f, 10.5f, 0f));

        PartDefinition hand = fore.addOrReplaceChild("hand",
                CubeListBuilder.create().texOffs(52, 0).addBox(-1.5f, 0f, -1.5f, 3f, 2f, 3f),
                PartPose.offset(0f, 12.5f, 0f));
        // three long hooked claws
        float[][] claws = {{-1f, -1f}, {1f, -1f}, {0f, 1f}};
        for (int i = 0; i < claws.length; i++) {
            PartDefinition c = hand.addOrReplaceChild("claw" + i,
                    CubeListBuilder.create().texOffs(56, 6).addBox(-0.5f, 0f, -0.5f, 1f, 5f, 1f),
                    PartPose.offsetAndRotation(claws[i][0], 1.5f, claws[i][1], claws[i][1] < 0 ? -0.35f : 0.35f, 0f, claws[i][0] * 0.2f));
            c.addOrReplaceChild("hook",
                    CubeListBuilder.create().texOffs(60, 6).addBox(-0.5f, 0f, -0.5f, 1f, 3f, 1f),
                    PartPose.offsetAndRotation(0f, 4.6f, 0f, claws[i][1] < 0 ? -0.7f : 0.7f, 0f, 0f));
        }
    }

    private static void legDef(PartDefinition root, String name, float x, boolean mirror) {
        CubeListBuilder thigh = CubeListBuilder.create().texOffs(28, 16);
        if (mirror) thigh.mirror();
        thigh.addBox(-1.5f, 0f, -1.5f, 3f, 9f, 3f);
        PartDefinition leg = root.addOrReplaceChild(name, thigh, PartPose.offsetAndRotation(x, 6f, 0f, -0.35f, 0f, 0f));
        CubeListBuilder shin = CubeListBuilder.create().texOffs(0, 36);
        if (mirror) shin.mirror();
        shin.addBox(-1f, 0f, -1f, 2f, 10f, 2f)
                .texOffs(8, 36).addBox(-1.5f, 9f, -3.5f, 3f, 1f, 4f); // clawed foot
        leg.addOrReplaceChild("shin", shin, PartPose.offsetAndRotation(0f, 8.5f, 0f, 0.55f, 0f, 0f));
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(ShadowDemonEntity e, float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        float breathe = Mth.sin(age * 0.09f);
        float walk = limbSwing * 0.55f;

        // hunched, breathing torso that rocks while it prowls
        body.xRot += breathe * 0.04f + limbSwingAmount * 0.15f;
        body.zRot += Mth.cos(walk) * 0.08f * limbSwingAmount;
        body.y += Math.abs(Mth.sin(walk)) * -1.0f * limbSwingAmount;

        head.yRot = netHeadYaw * Mth.DEG_TO_RAD * 0.8f;
        head.xRot += headPitch * Mth.DEG_TO_RAD * 0.5f + Mth.sin(age * 0.05f) * 0.05f;
        head.zRot = Mth.sin(age * 0.037f) * 0.12f; // unsettling head tilt
        jaw.xRot = 0.15f + Mth.sin(age * 0.11f) * 0.12f;
        hornR.zRot += Mth.sin(age * 0.06f) * 0.03f;
        hornL.zRot -= Mth.sin(age * 0.06f) * 0.03f;

        // long arms hang and sway, swinging opposite to the legs
        rightArm.xRot = Mth.cos(walk + Mth.PI) * 0.7f * limbSwingAmount - 0.3f + Mth.sin(age * 0.08f) * 0.06f;
        leftArm.xRot = Mth.cos(walk) * 0.7f * limbSwingAmount - 0.3f + Mth.sin(age * 0.08f + 1f) * 0.06f;
        rightArm.zRot += Mth.sin(age * 0.06f) * 0.05f;
        leftArm.zRot -= Mth.sin(age * 0.06f) * 0.05f;
        rightForearm.xRot = -0.45f - Mth.sin(age * 0.07f) * 0.08f;
        leftForearm.xRot = -0.45f - Mth.sin(age * 0.07f + 1f) * 0.08f;
        rightHand.xRot = -0.2f;
        leftHand.xRot = -0.2f;

        // claw swipe: both arms raise high and rake down across the target
        if (this.attackTime > 0f) {
            float t = this.attackTime;
            float swing = Mth.sin(t * Mth.PI);
            rightArm.xRot = -2.6f * swing + 0.4f * t;
            rightArm.yRot = -0.4f * swing;
            leftArm.xRot = -2.2f * Mth.sin(Math.min(1f, t * 1.3f) * Mth.PI);
            rightForearm.xRot = -0.2f;
            leftForearm.xRot = -0.3f;
            body.xRot += 0.3f * swing;
            jaw.xRot = 0.5f * swing + 0.15f;
        }

        // digitigrade prowl
        rightLeg.xRot += Mth.cos(walk) * 0.9f * limbSwingAmount;
        leftLeg.xRot += Mth.cos(walk + Mth.PI) * 0.9f * limbSwingAmount;
        rightShin.xRot += Math.max(0f, -Mth.sin(walk)) * 0.8f * limbSwingAmount;
        leftShin.xRot += Math.max(0f, Mth.sin(walk)) * 0.8f * limbSwingAmount;
    }
}
