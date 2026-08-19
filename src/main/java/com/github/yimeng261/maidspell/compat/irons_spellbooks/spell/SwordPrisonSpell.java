package com.github.yimeng261.maidspell.compat.irons_spellbooks.spell;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.WinefoxSwordProjectileEntity;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.TargetEntityCastData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class SwordPrisonSpell extends AbstractSpell {
    public static final ResourceLocation SPELL_ID =
            new ResourceLocation(MaidSpellMod.MOD_ID, "sword_prison");
    private static final int TARGET_RANGE = 32;

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.RARE)
            .setSchoolResource(SchoolRegistry.ENDER_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(12)
            .build();

    public SwordPrisonSpell() {
        baseManaCost = 35;
        manaCostPerLevel = 8;
        baseSpellPower = 8;
        spellPowerPerLevel = 2;
        castTime = 0;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public CastType getCastType() {
        return CastType.INSTANT;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return SPELL_ID;
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity caster, MagicData magicData) {
        return Utils.preCastTargetHelper(level, caster, magicData, this, TARGET_RANGE, 0.2F,
                true, target -> target != caster && !MaidSpellAllyResolver.areFriendly(caster, target));
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("ui.irons_spellbooks.damage",
                        Utils.stringTruncation(getSwordDamage(spellLevel, caster), 2)),
                Component.translatable("ui.irons_spellbooks.projectile_count", getTotalSwordCount(spellLevel)));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster,
                       CastSource castSource, MagicData magicData) {
        if (!level.isClientSide
                && magicData.getAdditionalCastData() instanceof TargetEntityCastData targetData) {
            LivingEntity target = targetData.getTarget((ServerLevel) level);
            if (target != null && target.isAlive()
                    && !MaidSpellAllyResolver.areFriendly(caster, target)) {
                summonSwordRing(level, spellLevel, caster, target);
            }
            magicData.resetAdditionalCastData();
        }
        super.onCast(level, spellLevel, caster, castSource, magicData);
    }

    private void summonSwordRing(Level level, int spellLevel, LivingEntity caster, LivingEntity target) {
        int count = getSwordCount(spellLevel);
        float damage = getSwordDamage(spellLevel, caster);
        Vec3 center = target.getBoundingBox().getCenter();
        double groundY = target.getY();
        RandomSource random = caster.getRandom();
        double baseAngle = random.nextDouble() * Mth.TWO_PI;

        for (int i = 0; i < count; i++) {
            double angle = baseAngle + Mth.TWO_PI * i / count
                    + (random.nextDouble() - 0.5D) * 0.2D;
            double radius = target.getBbWidth() + 2.2D + random.nextDouble() * 1.4D;
            double landingX = center.x + Math.cos(angle) * radius;
            double landingZ = center.z + Math.sin(angle) * radius;
            Vec3 spawn = new Vec3(
                    landingX + (random.nextDouble() - 0.5D) * 0.65D,
                    groundY + 5.5D + random.nextDouble() * 2.5D,
                    landingZ + (random.nextDouble() - 0.5D) * 0.65D);

            WinefoxSwordProjectileEntity sword = new WinefoxSwordProjectileEntity(level, caster);
            sword.setPos(spawn);
            sword.shoot(new Vec3(
                    (random.nextDouble() - 0.5D) * 0.55D,
                    -1.0D,
                    (random.nextDouble() - 0.5D) * 0.55D).normalize());
            sword.setRoll(random.nextFloat() * 360.0F);
            sword.setDamage(damage);
            level.addFreshEntity(sword);
        }

        WinefoxSwordProjectileEntity centerSword = new WinefoxSwordProjectileEntity(level, caster);
        centerSword.setPos(center.x, groundY + 7.5D, center.z);
        centerSword.shoot(new Vec3(0.0D, -1.0D, 0.0D));
        centerSword.setRoll(random.nextFloat() * 360.0F);
        centerSword.setDamage(damage);
        level.addFreshEntity(centerSword);
    }

    public int getSwordCount(int spellLevel) {
        return Mth.clamp(spellLevel + 5, 6, 10);
    }

    public int getTotalSwordCount(int spellLevel) {
        return getSwordCount(spellLevel) + 1;
    }

    public float getSwordDamage(int spellLevel, LivingEntity caster) {
        return getSpellPower(spellLevel, caster) * 0.4F;
    }
}
