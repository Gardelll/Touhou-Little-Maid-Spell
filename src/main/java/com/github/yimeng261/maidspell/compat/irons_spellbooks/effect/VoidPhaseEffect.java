package com.github.yimeng261.maidspell.compat.irons_spellbooks.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public class VoidPhaseEffect extends MobEffect {
    private static final int COLOR = 0x6A35A8;

    public VoidPhaseEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
    }
}
