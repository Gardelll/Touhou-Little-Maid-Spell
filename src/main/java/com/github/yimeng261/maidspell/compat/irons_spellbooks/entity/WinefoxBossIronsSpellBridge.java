package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.WinefoxSwordProjectileEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatEffects;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatSpells;
import com.github.yimeng261.maidspell.entity.MagicalWinefoxBossEntity;
import com.github.yimeng261.maidspell.entity.WinefoxBossSpellAction;
import com.github.yimeng261.maidspell.entity.WinefoxBossSpellBridge;
import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.capabilities.magic.TargetEntityCastData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class WinefoxBossIronsSpellBridge implements WinefoxBossSpellBridge.Delegate {
    @Override
    public void addAttributes(AttributeSupplier.Builder builder) {
        builder.add(AttributeRegistry.MAX_MANA.get(), 1_000_000.0D)
                .add(AttributeRegistry.SPELL_POWER.get(), 1.0D)
                .add(AttributeRegistry.CASTING_MOVESPEED.get(), 1.0D)
                .add(AttributeRegistry.ENDER_SPELL_POWER.get(), 1.2D)
                .add(AttributeRegistry.FIRE_SPELL_POWER.get(), 1.0D)
                .add(AttributeRegistry.LIGHTNING_SPELL_POWER.get(), 1.0D)
                .add(AttributeRegistry.HOLY_SPELL_POWER.get(), 1.0D);
    }

    @Override
    public boolean cast(MagicalWinefoxBossEntity boss, @Nullable LivingEntity target,
                        WinefoxBossSpellAction action, int spellLevel) {
        if (boss.level().isClientSide) {
            return false;
        }
        int clampedLevel = Mth.clamp(spellLevel, 1, 10);
        if (action == WinefoxBossSpellAction.SPEAR_THROW) {
            return throwSpear(boss, target);
        }

        AbstractSpell spell = getSpell(action);
        if (spell == null) {
            return false;
        }
        if (target != null && action != WinefoxBossSpellAction.HEAL
                && action != WinefoxBossSpellAction.VOID_PHASE
                && action != WinefoxBossSpellAction.ECHOING_STRIKES) {
            faceTarget(boss, target);
        }

        MagicData magicData = new MagicData(true);
        if (action == WinefoxBossSpellAction.MODIFIED_TELEPORT
                || action == WinefoxBossSpellAction.SWORD_PRISON) {
            if (target == null || !target.isAlive()) {
                return false;
            }
            magicData.setAdditionalCastData(new TargetEntityCastData(target));
        }

        spell.onServerPreCast(boss.level(), clampedLevel, boss, magicData);
        spell.onCast(boss.level(), clampedLevel, boss, CastSource.MOB, magicData);
        return true;
    }

    @Override
    public boolean isCasting(LivingEntity entity) {
        if (entity instanceof Player) {
            return MagicData.getPlayerMagicData(entity).isCasting();
        }
        return entity instanceof IMagicEntity magicEntity && magicEntity.isCasting();
    }

    @Override
    public boolean hasVoidPhase(MagicalWinefoxBossEntity boss) {
        return boss.hasEffect(IronsSpellbooksCompatEffects.VOID_PHASE.get());
    }

    @Override
    public int getCooldownTicks(WinefoxBossSpellAction action, double multiplier,
                                int fallbackTicks) {
        AbstractSpell spell = getSpell(action);
        return spell == null
                ? fallbackTicks
                : Math.max(1, Mth.ceil(spell.getSpellCooldown() * multiplier));
    }

    @Override
    public int getCastTimeTicks(MagicalWinefoxBossEntity boss,
                                WinefoxBossSpellAction action, int spellLevel) {
        AbstractSpell spell = getSpell(action);
        return spell == null || spell.getCastType() != CastType.LONG
                ? 0
                : Math.max(0, spell.getEffectiveCastTime(spellLevel, boss));
    }

    @Override
    public String getCastAnimation(WinefoxBossSpellAction action, boolean finish) {
        AbstractSpell spell = getSpell(action);
        if (spell == null) {
            return null;
        }
        AnimationHolder animation = finish
                ? spell.getCastFinishAnimation()
                : spell.getCastStartAnimation();
        if (animation.isPass) {
            return null;
        }
        return animation.getForPlayer()
                .map(resource -> resource.getPath())
                .orElse(finish ? WinefoxBossSpellBridge.STOP_CAST_ANIMATION : null);
    }

    @Nullable
    private AbstractSpell getSpell(WinefoxBossSpellAction action) {
        return switch (action) {
            case MAGIC_MISSILE -> SpellRegistry.MAGIC_MISSILE_SPELL.get();
            case COUNTERSPELL -> SpellRegistry.COUNTERSPELL_SPELL.get();
            case MAGIC_ARROW -> SpellRegistry.MAGIC_ARROW_SPELL.get();
            case SUMMON_SWORDS -> SpellRegistry.SUMMON_SWORDS.get();
            case FIREBALL -> SpellRegistry.FIREBALL_SPELL.get();
            case LIGHTNING_LANCE -> SpellRegistry.LIGHTNING_LANCE_SPELL.get();
            case HEAL -> SpellRegistry.HEAL_SPELL.get();
            case MODIFIED_STARFALL -> IronsSpellbooksCompatSpells.MODIFIED_STARFALL.get();
            case MAGIC_SHOTGUN -> IronsSpellbooksCompatSpells.MAGIC_SHOTGUN.get();
            case VOID_PHASE -> IronsSpellbooksCompatSpells.VOID_PHASE.get();
            case ECHOING_STRIKES -> SpellRegistry.ECHOING_STRIKES_SPELL.get();
            case SHADOW_SLASH -> SpellRegistry.SHADOW_SLASH.get();
            case MODIFIED_TELEPORT -> IronsSpellbooksCompatSpells.MODIFIED_TELEPORT.get();
            case FLAMING_STRIKE -> SpellRegistry.FLAMING_STRIKE_SPELL.get();
            case DIVINE_SMITE -> SpellRegistry.DIVINE_SMITE_SPELL.get();
            case SWORD_PRISON -> IronsSpellbooksCompatSpells.SWORD_PRISON.get();
            case SPEAR_THROW -> null;
        };
    }

    private boolean throwSpear(MagicalWinefoxBossEntity boss, @Nullable LivingEntity target) {
        if (target == null || !target.isAlive()) {
            return false;
        }
        Vec3 spawn = boss.getEyePosition().add(boss.getLookAngle().scale(0.5D));
        Vec3 targetPosition = target.getBoundingBox().getCenter()
                .add(target.getDeltaMovement().scale(0.35D));
        Vec3 direction = targetPosition.subtract(spawn).normalize();
        WinefoxSwordProjectileEntity spear = new WinefoxSwordProjectileEntity(boss.level(), boss);
        spear.setPos(spawn);
        spear.shoot(direction);
        spear.setDeltaMovement(direction.scale(3.5D));
        spear.setDamage(20.0F);
        boss.level().addFreshEntity(spear);
        return true;
    }

    private void faceTarget(MagicalWinefoxBossEntity boss, LivingEntity target) {
        Vec3 direction = target.getEyePosition().subtract(boss.getEyePosition());
        float yaw = (float) (Mth.atan2(direction.z, direction.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(direction.y, direction.horizontalDistance()) * Mth.RAD_TO_DEG);
        boss.setYRot(yaw);
        boss.setXRot(pitch);
        boss.setYHeadRot(yaw);
        boss.yBodyRot = yaw;
    }
}
