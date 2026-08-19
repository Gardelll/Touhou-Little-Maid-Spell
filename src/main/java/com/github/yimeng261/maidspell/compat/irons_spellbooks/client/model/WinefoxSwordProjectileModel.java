package com.github.yimeng261.maidspell.compat.irons_spellbooks.client.model;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.WinefoxSwordProjectileEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class WinefoxSwordProjectileModel extends GeoModel<WinefoxSwordProjectileEntity> {
    private static final ResourceLocation MODEL =
            new ResourceLocation(MaidSpellMod.MOD_ID, "geo/winefox_spear_projectile.geo.json");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MaidSpellMod.MOD_ID, "textures/entity/winefox_spear_projectile.png");
    private static final ResourceLocation ANIMATION =
            new ResourceLocation(MaidSpellMod.MOD_ID, "animations/winefox_spear_projectile.animation.json");

    @Override
    public ResourceLocation getModelResource(WinefoxSwordProjectileEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(WinefoxSwordProjectileEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(WinefoxSwordProjectileEntity animatable) {
        return ANIMATION;
    }
}
