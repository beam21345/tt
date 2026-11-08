package com.alastor.staff.registry;

import com.alastor.staff.AlastorStaffMod;
import com.alastor.staff.entity.ShadowDemonEntity;
import com.alastor.staff.entity.ShadowFxEntity;
import com.alastor.staff.entity.TentacleEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, AlastorStaffMod.MODID);

    public static final RegistryObject<EntityType<ShadowDemonEntity>> SHADOW_DEMON = ENTITIES.register("shadow_demon",
            () -> EntityType.Builder.<ShadowDemonEntity>of(ShadowDemonEntity::new, MobCategory.MISC)
                    .sized(0.7f, 2.4f)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .build("shadow_demon"));

    public static final RegistryObject<EntityType<TentacleEntity>> TENTACLE = ENTITIES.register("shadow_tentacle",
            () -> EntityType.Builder.<TentacleEntity>of(TentacleEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .fireImmune()
                    .noSave()
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("shadow_tentacle"));

    public static final RegistryObject<EntityType<ShadowFxEntity>> SHADOW_FX = ENTITIES.register("shadow_fx",
            () -> EntityType.Builder.<ShadowFxEntity>of(ShadowFxEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.1f)
                    .fireImmune()
                    .noSave()
                    .clientTrackingRange(8)
                    .updateInterval(20)
                    .build("shadow_fx"));

    private ModEntities() {}
}
