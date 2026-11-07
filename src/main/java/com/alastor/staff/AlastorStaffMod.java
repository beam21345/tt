package com.alastor.staff;

import com.alastor.staff.entity.ShadowDemonEntity;
import com.alastor.staff.registry.ModEntities;
import com.alastor.staff.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(AlastorStaffMod.MODID)
public class AlastorStaffMod {
    public static final String MODID = "alastorstaff";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AlastorStaffMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        modBus.addListener(this::onAttributes);
        modBus.addListener(this::onCreativeTab);
    }

    private void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.SHADOW_DEMON.get(), ShadowDemonEntity.createAttributes().build());
    }

    private void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.ALASTOR_STAFF.get());
        }
    }
}
