package com.github.yimeng261.maidspell.compat.irons_spellbooks.registry;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.effect.VoidPhaseEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class IronsSpellbooksCompatEffects {
    private static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, MaidSpellMod.MOD_ID);

    public static final RegistryObject<MobEffect> VOID_PHASE =
            EFFECTS.register("void_phase", VoidPhaseEffect::new);

    private IronsSpellbooksCompatEffects() {
    }

    public static void register(IEventBus eventBus) {
        EFFECTS.register(eventBus);
    }
}
