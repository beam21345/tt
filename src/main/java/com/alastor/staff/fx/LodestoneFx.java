package com.alastor.staff.fx;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import team.lodestar.lodestone.registry.common.particle.LodestoneParticleRegistry;
import team.lodestar.lodestone.systems.easing.Easing;
import team.lodestar.lodestone.systems.particle.builder.WorldParticleBuilder;
import team.lodestar.lodestone.systems.particle.data.GenericParticleData;
import team.lodestar.lodestone.systems.particle.data.color.ColorParticleData;
import team.lodestar.lodestone.systems.particle.data.spin.SpinParticleData;
import team.lodestar.lodestone.systems.particle.render_types.LodestoneWorldParticleRenderType;

import java.awt.Color;

/**
 * Soft, glowing Lodestone particles. Client side only: call these from code that runs when
 * {@code level.isClientSide} (entity client ticks), never from the server.
 */
public final class LodestoneFx {
    private static final Color GREEN = new Color(70, 255, 140);
    private static final Color DEEP_GREEN = new Color(20, 140, 60);
    private static final Color WHITE_GREEN = new Color(210, 255, 225);
    private static final Color RED = new Color(255, 60, 40);
    private static final Color BLACK = new Color(8, 6, 12);

    /** Glowing green wisps (additive, full bright). */
    public static void glow(Level level, double x, double y, double z, float scale, int life, double spread, double rise) {
        RandomSource r = level.getRandom();
        WorldParticleBuilder.create(LodestoneParticleRegistry.WISP_PARTICLE)
                .setColorData(ColorParticleData.create(WHITE_GREEN, DEEP_GREEN).setEasing(Easing.QUAD_OUT).build())
                .setTransparencyData(GenericParticleData.create(0.0f, 0.75f, 0.0f).setEasing(Easing.QUAD_OUT, Easing.QUAD_IN).build())
                .setScaleData(GenericParticleData.create(scale * 0.4f, scale, 0.0f).setEasing(Easing.QUAD_OUT, Easing.QUAD_IN).build())
                .setSpinData(SpinParticleData.create(r.nextFloat() * 0.2f - 0.1f).build())
                .setLifetime(life + r.nextInt(Math.max(1, life / 3)))
                .setRandomOffset(spread)
                .setMotion(0, rise, 0)
                .setRandomMotion(0.01, 0.01)
                .setFullBrightLighting()
                .enableNoClip()
                .spawn(level, x, y, z);
    }

    /** Small bright sparks thrown outward (green, sometimes red). */
    public static void sparks(Level level, double x, double y, double z, int count, float speed, boolean red) {
        for (int i = 0; i < count; i++) {
            WorldParticleBuilder.create(LodestoneParticleRegistry.SPARK_PARTICLE)
                    .setColorData(ColorParticleData.create(red ? Color.WHITE : WHITE_GREEN, red ? RED : GREEN).build())
                    .setTransparencyData(GenericParticleData.create(0.9f, 0.0f).setEasing(Easing.QUAD_IN).build())
                    .setScaleData(GenericParticleData.create(0.12f, 0.0f).build())
                    .setLifetime(8 + level.getRandom().nextInt(6))
                    .setRandomMotion(speed, speed)
                    .setGravityStrength(0.4f)
                    .setFullBrightLighting()
                    .spawn(level, x, y, z);
        }
    }

    /** Twinkling green stars (for sigils and summoning). */
    public static void twinkle(Level level, double x, double y, double z, float scale, double spread) {
        WorldParticleBuilder.create(LodestoneParticleRegistry.TWINKLE_PARTICLE)
                .setColorData(ColorParticleData.create(WHITE_GREEN, GREEN).build())
                .setTransparencyData(GenericParticleData.create(0.0f, 0.9f, 0.0f).build())
                .setScaleData(GenericParticleData.create(0.0f, scale, 0.0f).build())
                .setLifetime(14 + level.getRandom().nextInt(8))
                .setRandomOffset(spread, 0.2)
                .setMotion(0, 0.02, 0)
                .setFullBrightLighting()
                .spawn(level, x, y, z);
    }

    /** Thick, slow black smoke curling up out of shadows. */
    public static void shadowSmoke(Level level, double x, double y, double z, float scale, double spread) {
        RandomSource r = level.getRandom();
        WorldParticleBuilder.create(LodestoneParticleRegistry.SMOKE_PARTICLE)
                .setRenderType(LodestoneWorldParticleRenderType.TRANSPARENT)
                .setColorData(ColorParticleData.create(BLACK, BLACK).build())
                .setTransparencyData(GenericParticleData.create(0.0f, 0.65f, 0.0f).setEasing(Easing.QUAD_OUT, Easing.QUAD_IN).build())
                .setScaleData(GenericParticleData.create(scale * 0.5f, scale, scale * 1.3f).build())
                .setSpinData(SpinParticleData.create((r.nextFloat() - 0.5f) * 0.1f).build())
                .setLifetime(26 + r.nextInt(14))
                .setRandomOffset(spread, 0.1)
                .setMotion(0, 0.025, 0)
                .setRandomMotion(0.01, 0.005)
                .setNaturalLighting()
                .spawn(level, x, y, z);
    }

    /** A wisp (green glow or black shadow) drawn in toward {@code center} from {@code radius} away. */
    public static void suck(Level level, Vec3 center, float radius, boolean glow) {
        RandomSource r = level.getRandom();
        double a = r.nextDouble() * Math.PI * 2, b = (r.nextDouble() - 0.5) * Math.PI;
        Vec3 off = new Vec3(Math.cos(a) * Math.cos(b), Math.sin(b), Math.sin(a) * Math.cos(b)).scale(radius);
        Vec3 from = center.add(off);
        Vec3 vel = off.scale(-1.0 / 14);
        WorldParticleBuilder.create(glow ? LodestoneParticleRegistry.WISP_PARTICLE : LodestoneParticleRegistry.SMOKE_PARTICLE)
                .setRenderType(glow ? LodestoneWorldParticleRenderType.ADDITIVE : LodestoneWorldParticleRenderType.TRANSPARENT)
                .setColorData(glow ? ColorParticleData.create(WHITE_GREEN, GREEN).build() : ColorParticleData.create(BLACK, BLACK).build())
                .setTransparencyData(GenericParticleData.create(0.0f, glow ? 0.8f : 0.7f, 0.0f).build())
                .setScaleData(GenericParticleData.create(glow ? 0.35f : 0.8f, glow ? 0.15f : 0.4f).build())
                .setLifetime(14)
                .setMotion(vel)
                .setFullBrightLighting()
                .enableNoClip()
                .spawn(level, from.x, from.y, from.z);
    }

    /** A ribbon of glowing particles from a to b (used for whipping tips and weapon edges). */
    public static void streak(Level level, Vec3 a, Vec3 b, float scale, boolean dark) {
        Vec3 d = b.subtract(a);
        int n = (int) Math.min(14, Math.max(2, d.length() / 0.35));
        for (int i = 0; i <= n; i++) {
            Vec3 p = a.add(d.scale((double) i / n));
            if (dark) shadowSmoke(level, p.x, p.y, p.z, scale * 1.4f, 0.05);
            else {
                WorldParticleBuilder.create(LodestoneParticleRegistry.WISP_PARTICLE)
                        .setColorData(ColorParticleData.create(WHITE_GREEN, DEEP_GREEN).build())
                        .setTransparencyData(GenericParticleData.create(0.55f, 0.0f).setEasing(Easing.QUAD_OUT).build())
                        .setScaleData(GenericParticleData.create(scale, 0.0f).build())
                        .setLifetime(7)
                        .setFullBrightLighting()
                        .enableNoClip()
                        .spawn(level, p.x, p.y, p.z);
            }
        }
    }

    private LodestoneFx() {}
}
