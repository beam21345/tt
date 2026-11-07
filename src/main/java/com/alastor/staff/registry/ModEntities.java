package com.alastor.staff.registry;

import com.alastor.staff.AlastorStaffMod;
import com.alastor.staff.entity.ShadowDemonEntity;
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
                    .sized(0.6f, 1.95f)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .build("shadow_demon"));

    private ModEntities() {}
}
