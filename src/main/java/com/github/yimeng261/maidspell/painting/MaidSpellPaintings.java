package com.github.yimeng261.maidspell.painting;

import com.github.yimeng261.maidspell.MaidSpellMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class MaidSpellPaintings {
    public static final DeferredRegister<PaintingVariant> PAINTINGS =
        DeferredRegister.create(Registries.PAINTING_VARIANT, MaidSpellMod.MOD_ID);

    public static final RegistryObject<PaintingVariant> ASTRONOMICAL_OBJECT =
        register("astronomical_object", 2, 3);
    public static final RegistryObject<PaintingVariant> FALLING_STAR =
        register("falling_star", 2, 3);
    public static final RegistryObject<PaintingVariant> MAGIC_WINE_FOX =
        register("magic_wine_fox", 3, 2);
    public static final RegistryObject<PaintingVariant> STARRY_FLOWER_SEA =
        register("starry_flower_sea", 2, 2);

    private MaidSpellPaintings() {
    }

    private static RegistryObject<PaintingVariant> register(String name, int width, int height) {
        return PAINTINGS.register(name, () -> new PaintingVariant(width * 16, height * 16));
    }

    public static void register(IEventBus eventBus) {
        PAINTINGS.register(eventBus);
    }
}
