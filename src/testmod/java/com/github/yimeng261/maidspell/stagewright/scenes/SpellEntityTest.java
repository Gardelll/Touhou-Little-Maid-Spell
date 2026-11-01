package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.CompanionBlackHoleEntity;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Owners;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.spell.ModifiedMagicMissileEntity;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.registry.IronsSpellbooksCompatSpells;
import com.github.yimeng261.maidspell.compat.irons_spellbooks.spell.TripleStarArrowSpell;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.entity.spells.magic_arrow.MagicArrowProjectile;
import net.minecraft.world.entity.Mob;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.Set;
import java.util.List;
import java.util.HashSet;
import java.util.Map;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 本模组的铁魔法法术实体：女仆黑洞只吞敌方的箭；改造飞弹能被实体选择器选中；魔法霰弹的散射锥与三矢连星的连发间隔。
 */
@DisplayName("铁魔法法术实体：伴星黑洞与魔法飞弹")
@Scene("tlms")
class SpellEntityTest {
    private static final float RADIUS = 3;

    @DisplayName("伴星黑洞只吞敌方的箭：施法女仆自己的箭留下，野生女仆和他人女仆的箭被吞掉")
    @Test
    void consumesEnemyArrowsOnly(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        blackHole(Stage.flat(game, plot, reporter), false);
    }

    @DisplayName("伴星黑洞不吞主人和同主人女仆射出的箭")
    @Test
    void sparesOwnerSideArrows(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        blackHole(Stage.flat(game, plot, reporter), true);
    }

    @DisplayName("魔法飞弹实体能被按类型的实体选择器选中")
    @Test
    void selectableByType(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> missileSelectable(ctx));
    }

    @DisplayName("魔法霰弹：3 级一次射出 6 枚飞弹，全在前方半角 7.5° 的锥内且方向各不相同")
    @Test
    void shotgunCone(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            Mob caster = facingEast(ctx);
            AbstractSpell spell = IronsSpellbooksCompatSpells.MAGIC_SHOTGUN.get();
            spell.onCast(ctx.level(), 3, caster, CastSource.MOB, MagicData.getPlayerMagicData(caster));
            List<ModifiedMagicMissileEntity> missiles = ctx.level().getEntitiesOfClass(ModifiedMagicMissileEntity.class,
                    caster.getBoundingBox().inflate(4), missile -> missile.getOwner() == caster);
            missiles.forEach(missile -> ctx.cleanup(missile::discard));
            ctx.check(missiles).as("射出的飞弹").hasSize(6);
            Vec3 forward = caster.getLookAngle().normalize();
            Set<String> directions = new HashSet<>();
            double widest = 0;
            for (ModifiedMagicMissileEntity missile : missiles) {
                Vec3 direction = missile.getDeltaMovement().normalize();
                widest = Math.max(widest, Math.toDegrees(Math.acos(Math.min(1.0, direction.dot(forward)))));
                directions.add(String.format("%.4f,%.4f,%.4f", direction.x, direction.y, direction.z));
            }
            ctx.record("最大偏角", widest);
            ctx.check(widest).as("飞弹与正前方的最大夹角（度）").isAtMost(7.5 + 1e-3);
            ctx.check(directions).as("飞弹方向").hasSize(missiles.size());
        });
    }

    @DisplayName("三矢连星：连续射出三支魔法箭，每支相隔 4 tick")
    @Test
    void tripleArrowInterval(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        Mob caster = ctx.get(() -> facingEast(ctx));
        Map<UUID, Integer> firstSeen = new LinkedHashMap<>();
        int start = ctx.get(() -> {
            AbstractSpell spell = IronsSpellbooksCompatSpells.TRIPLE_STAR_ARROW.get();
            spell.onCast(ctx.level(), 1, caster, CastSource.MOB, MagicData.getPlayerMagicData(caster));
            return ctx.tick();
        });
        Runnable sample = () -> ctx.level().getEntitiesOfClass(MagicArrowProjectile.class, caster.getBoundingBox().inflate(64),
                arrow -> arrow.getOwner() == caster).forEach(arrow -> {
                    if (firstSeen.putIfAbsent(arrow.getUUID(), ctx.tick() - start) == null) {
                        ctx.cleanup(arrow::discard);
                    }
                });
        // 只在每个 tick 结束后采样：首箭在施法当时射出，后两支在到期 tick 的末尾射出，统一按 tick 后看到的时刻比较间隔
        ctx.watch(TripleStarArrowSpell.SHOT_INTERVAL_TICKS * 3, sample);
        List<Integer> seen = List.copyOf(firstSeen.values());
        ctx.record("每支箭首次看到时距施法的 tick", seen);
        ctx.check(seen).as("射出的箭").hasSize(TripleStarArrowSpell.SHOT_COUNT);
        if (seen.size() == TripleStarArrowSpell.SHOT_COUNT) {
            ctx.check(List.of(seen.get(1) - seen.get(0), seen.get(2) - seen.get(1))).as("相邻两支箭的间隔")
                    .isEqualTo(List.of(TripleStarArrowSpell.SHOT_INTERVAL_TICKS, TripleStarArrowSpell.SHOT_INTERVAL_TICKS));
        }
    }

    /** 无 AI、朝正东站着的僵尸施法者。 */
    private static Mob facingEast(Stage ctx) {
        Mob caster = Actors.spawn(ctx, "minecraft:zombie", 0, 0, 0, true);
        caster.setYRot(-90.0F);
        caster.setYHeadRot(-90.0F);
        caster.setXRot(0.0F);
        return caster;
    }

    /**
     * 女仆放出黑洞，球心处停着几支不同主人的箭。一 tick 后：
     * ownerSide 为 false 时看施法女仆自己的箭（留下）与野生女仆、别的玩家女仆的箭（吞掉）；
     * 为 true 时看主人和同主人女仆的箭（都应留下）。
     */
    private static void blackHole(Stage ctx, boolean ownerSide) {
        record Rig(Map<String, Arrow> arrows, Map<String, Boolean> expected) { }
        Rig rig = ctx.get(() -> {
            ServerLevel level = ctx.level();
            ServerPlayer owner = Owners.visitor(ctx, "TlmsHoleOwner", 3, 0, 0);
            ServerPlayer stranger = Owners.visitor(ctx, "TlmsHoleStranger", -3, 0, 0);
            EntityMaid caster = Owners.maid(ctx, owner, 0, 0, 0);
            CompanionBlackHoleEntity hole = new CompanionBlackHoleEntity(level, caster);
            hole.setRadius(RADIUS);
            Vec3 eyes = caster.getEyePosition();
            hole.setPos(eyes.x, CompanionBlackHoleEntity.placementY(eyes.y, RADIUS), eyes.z);
            Vec3 center = hole.position().add(0, RADIUS, 0);

            Map<String, LivingEntity> shooters = new LinkedHashMap<>();
            Map<String, Boolean> expected = new LinkedHashMap<>();
            if (ownerSide) {
                shooters.put("主人", owner);
                shooters.put("同主人的女仆", Owners.maid(ctx, owner, 0, 0, 3));
                shooters.keySet().forEach(k -> expected.put(k, false));
            } else {
                shooters.put("施法女仆", caster);
                expected.put("施法女仆", false);
                shooters.put("野生女仆", Actors.stillMaid(ctx, 0, 0, -3));
                expected.put("野生女仆", true);
                shooters.put("别的玩家的女仆", Owners.maid(ctx, stranger, 0, 0, 5));
                expected.put("别的玩家的女仆", true);
            }
            Map<String, Arrow> arrows = new LinkedHashMap<>();
            shooters.forEach((name, shooter) -> {
                Arrow arrow = new Arrow(EntityType.ARROW, level);
                arrow.setOwner(shooter);
                arrow.setNoGravity(true);
                arrow.setPos(center);
                arrow.setDeltaMovement(Vec3.ZERO);
                level.addFreshEntity(arrow);
                ctx.cleanup(arrow::discard);
                arrows.put(name, arrow);
            });
            level.addFreshEntity(hole);
            ctx.cleanup(hole::discard);
            return new Rig(arrows, expected);
        });
        ctx.ticks(2);
        ctx.run(() -> {
            Map<String, Boolean> consumed = new LinkedHashMap<>();
            rig.arrows().forEach((name, arrow) -> consumed.put(name, arrow.isRemoved()));
            ctx.check(consumed).as("各方的箭是否被黑洞吞掉").isEqualTo(rig.expected());
        });
    }

    private static void missileSelectable(Stage ctx) {
        ServerLevel level = ctx.level();
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(NS + "modified_magic_missile")).orElse(null);
        if (type == null) {
            throw ctx.fail(NS + "modified_magic_missile 未注册");
        }
        Entity missile = type.create(level);
        Vec3 at = Vec3.atCenterOf(ctx.rel(0, 3, 0));
        missile.setPos(at);
        missile.setNoGravity(true);
        level.addFreshEntity(missile);
        ctx.cleanup(missile::discard);
        CommandSourceStack source = ctx.server().createCommandSourceStack().withSuppressedOutput().withLevel(level).withPosition(at);
        int found;
        try {
            found = ctx.server().getCommands().getDispatcher()
                    .execute("execute if entity @e[type=" + NS + "modified_magic_missile,distance=..2]", source);
        } catch (CommandSyntaxException e) {
            throw ctx.fail("选择器执行失败：" + e.getMessage());
        }
        ctx.check(found).as("@e[type=" + NS + "modified_magic_missile] 选中的数量").isEqualTo(1);
    }
}
