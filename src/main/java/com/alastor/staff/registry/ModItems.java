package com.alastor.staff.registry;

import com.alastor.staff.AlastorStaffMod;
import com.alastor.staff.item.AlastorStaffItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, AlastorStaffMod.MODID);

    public static final RegistryObject<Item> ALASTOR_STAFF = ITEMS.register("alastor_staff",
            () -> new AlastorStaffItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    private ModItems() {}
}
