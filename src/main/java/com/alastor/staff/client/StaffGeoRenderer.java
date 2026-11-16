package com.alastor.staff.client;

import com.alastor.staff.AlastorStaffMod;
import com.alastor.staff.item.AlastorStaffItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Animated 3D staff (GeckoLib): orb spins and bobs, charms sway, claws flare open when casting. */
public class StaffGeoRenderer extends GeoItemRenderer<AlastorStaffItem> {

    public StaffGeoRenderer() {
        super(new Model());
        addRenderLayer(new AutoGlowingGeoLayer<>(this)); // green parts glow in the dark
    }

    static class Model extends GeoModel<AlastorStaffItem> {
        private static final ResourceLocation GEO = new ResourceLocation(AlastorStaffMod.MODID, "geo/alastor_staff.geo.json");
        private static final ResourceLocation TEX = new ResourceLocation(AlastorStaffMod.MODID, "textures/item/alastor_staff_model.png");
        private static final ResourceLocation ANIM = new ResourceLocation(AlastorStaffMod.MODID, "animations/alastor_staff.animation.json");

        @Override
        public ResourceLocation getModelResource(AlastorStaffItem animatable) { return GEO; }

        @Override
        public ResourceLocation getTextureResource(AlastorStaffItem animatable) { return TEX; }

        @Override
        public ResourceLocation getAnimationResource(AlastorStaffItem animatable) { return ANIM; }
    }
}
