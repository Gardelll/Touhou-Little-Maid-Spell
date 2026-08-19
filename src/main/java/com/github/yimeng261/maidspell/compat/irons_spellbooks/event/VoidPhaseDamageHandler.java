package com.github.yimeng261.maidspell.compat.irons_spellbooks.event;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatEffects;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatSpells;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.spell.VoidPhaseSpell;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class VoidPhaseDamageHandler {
    private static final ThreadLocal<Boolean> APPLYING_VOID_DAMAGE =
            ThreadLocal.withInitial(() -> false);

    private VoidPhaseDamageHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        if (APPLYING_VOID_DAMAGE.get() || event.getEntity().level().isClientSide) {
            return;
        }

        Entity sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof LivingEntity attacker)) {
            return;
        }

        MobEffectInstance phase = attacker.getEffect(IronsSpellbooksCompatEffects.VOID_PHASE.get());
        if (phase == null || !isEmpoweredHit(event, attacker)) {
            return;
        }

        int spellLevel = phase.getAmplifier() + 1;
        VoidPhaseSpell spell = (VoidPhaseSpell) IronsSpellbooksCompatSpells.VOID_PHASE.get();
        float bonusDamage = spell.getBonusDamage(spellLevel, attacker);
        if (bonusDamage <= 0.0F) {
            return;
        }

        APPLYING_VOID_DAMAGE.set(true);
        int previousInvulnerabilityTime = event.getEntity().invulnerableTime;
        Vec3 previousMotion = event.getEntity().getDeltaMovement();
        try {
            event.getEntity().invulnerableTime = 0;
            DamageSources.applyDamage(event.getEntity(), bonusDamage,
                    spell.getDamageSource(attacker).setIFrames(0));
            MagicManager.spawnParticles(event.getEntity().level(), ParticleHelper.UNSTABLE_ENDER,
                    event.getEntity().getX(), event.getEntity().getY(0.5D), event.getEntity().getZ(),
                    12, 0.25D, 0.35D, 0.25D, 0.08D, true);
        } finally {
            event.getEntity().invulnerableTime = previousInvulnerabilityTime;
            event.getEntity().setDeltaMovement(previousMotion);
            APPLYING_VOID_DAMAGE.set(false);
        }
    }

    private static boolean isEmpoweredHit(LivingDamageEvent event, LivingEntity attacker) {
        if (event.getSource() instanceof SpellDamageSource spellDamageSource) {
            return SchoolRegistry.ENDER_RESOURCE.equals(spellDamageSource.spell().getSchoolType().getId());
        }
        return event.getSource().getEntity() == attacker
                && event.getSource().getDirectEntity() == attacker;
    }
}
