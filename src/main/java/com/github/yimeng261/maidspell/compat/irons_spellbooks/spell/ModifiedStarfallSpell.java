package com.github.yimeng261.maidspell.compat.irons_spellbooks.spell;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.ModifiedStarfallCloudEntity;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.spells.ender.StarfallSpell;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class ModifiedStarfallSpell extends StarfallSpell {
    public static final ResourceLocation SPELL_ID =
            new ResourceLocation(MaidSpellMod.MOD_ID, "starfall_modified");

    private static final int CAST_TIME_TICKS = 5 * 20;

    public ModifiedStarfallSpell() {
        this.castTime = CAST_TIME_TICKS;
    }

    @Override
    public CastType getCastType() {
        return CastType.LONG;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return SPELL_ID;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource castSource, MagicData magicData) {
        if (level.isClientSide) {
            return;
        }

        float cometDamage = getSpellPower(spellLevel, caster) * 0.5F;
        ModifiedStarfallCloudEntity cloud = new ModifiedStarfallCloudEntity(level, caster, cometDamage);
        cloud.setPos(caster.position());
        level.addFreshEntity(cloud);
    }

    @Override
    public void onServerCastTick(Level level, int spellLevel, LivingEntity caster, @Nullable MagicData magicData) {
        // The modified storm starts once the five-second long cast completes.
    }
}
