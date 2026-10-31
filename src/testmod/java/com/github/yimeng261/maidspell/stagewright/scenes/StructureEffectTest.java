package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.stagewright.support.Stage;
import com.github.yimeng261.maidspell.stagewright.support.StructureStage;
import com.github.yimeng261.maidspell.stagewright.support.WorldExtract;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.util.ArrayList;
import java.util.List;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 结构里预置生物身上的效果：效果带来的属性加成随效果消失（不残留旧版转换出的 legacy_effect_ 修饰符）；
 * 圣遗礼拜堂顶层首领女仆的效果都是永久。
 */
@Scene("tlms")
class StructureEffectTest {
    private static final int SETTLE_TICKS = 5;

    @Test
    void elvenRealmEffectBonusesEndWithEffects(Game game,
            @Plot.Size(x = 208, y = 256, z = 208) @Plot.Centered Plot plot, TestReporter reporter) {
        bonusesEnd(Stage.wideFlat(game, plot, reporter), "elven_realm");
    }

    @Test
    void fallenSanctumEffectBonusesEndWithEffects(Game game,
            @Plot.Size(x = 240, y = 256, z = 240) @Plot.Centered Plot plot, TestReporter reporter) {
        bonusesEnd(Stage.wideFlat(game, plot, reporter), "fallen_sanctum");
    }

    /** 带雷暴效果的就是顶层首领女仆：她身上的每个效果都是永久的。 */
    @Test
    void relicSanctumBossEffectsPermanent(Game game,
            @Plot.Size(x = 144, y = 256, z = 144) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        List<LivingEntity> living = placedLiving(ctx, "relic_sanctum");
        ResourceLocation thunderstorm = ResourceLocation.parse("irons_spellbooks:thunderstorm");
        List<LivingEntity> bosses = ctx.get(() -> living.stream().filter(e -> e.getActiveEffects().stream()
                .anyMatch(effect -> effect.getEffect().is(thunderstorm))).toList());
        ctx.check(bosses.size()).as("带雷暴效果的预置生物数").isAtLeast(1);
        ctx.ticks(SETTLE_TICKS);
        ctx.run(() -> {
            List<String> finite = new ArrayList<>();
            for (LivingEntity boss : bosses) {
                for (MobEffectInstance effect : boss.getActiveEffects()) {
                    if (!effect.isInfiniteDuration()) {
                        finite.add(describe(boss) + " " + effect.getEffect().getRegisteredName() + " 剩 " + effect.getDuration());
                    }
                }
            }
            ctx.check(finite).as("首领女仆身上不是永久的效果").isEmpty();
        });
    }

    private static List<LivingEntity> placedLiving(Stage ctx, String structure) {
        return ctx.get(() -> {
            StructureStage.Placed placed = StructureStage.place(ctx, NS + structure);
            return WorldExtract.entities(ctx.level(), placed).stream()
                    .filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast).toList();
        });
    }

    private static String describe(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()) + "@" + entity.blockPosition().toShortString();
    }

    /** 实体所有属性上的修饰符 ID。 */
    private static List<String> modifierIds(LivingEntity living) {
        List<String> ids = new ArrayList<>();
        for (Tag attribute : living.getAttributes().save()) {
            ListTag modifiers = ((CompoundTag) attribute).getList("modifiers", Tag.TAG_COMPOUND);
            for (Tag modifier : modifiers) {
                ids.add(((CompoundTag) modifier).getString("id"));
            }
        }
        return ids;
    }

    /**
     * 放置结构后，所有预置生物身上没有 legacy_effect_ 修饰符；喝奶（清除全部效果）后不再留有效果修饰符，
     * 攻击力不再被压到 0。
     */
    private static void bonusesEnd(Stage ctx, String structure) {
        List<LivingEntity> living = placedLiving(ctx, structure);
        ctx.check(ctx.get(() -> living.stream().anyMatch(e -> !e.getActiveEffects().isEmpty())))
                .as(structure + " 里有带效果的预置生物").isTrue();
        ctx.ticks(SETTLE_TICKS);
        ctx.run(() -> {
            List<String> legacy = new ArrayList<>();
            List<String> leftover = new ArrayList<>();
            List<String> zeroAttack = new ArrayList<>();
            for (LivingEntity entity : living) {
                modifierIds(entity).stream().filter(id -> ResourceLocation.parse(id).getPath().startsWith("legacy_effect_"))
                        .forEach(id -> legacy.add(describe(entity) + " " + id));
                if (entity.getActiveEffects().isEmpty()) {
                    continue;
                }
                entity.removeAllEffects();
                modifierIds(entity).stream().filter(id -> ResourceLocation.parse(id).getPath().startsWith("effect."))
                        .forEach(id -> leftover.add(describe(entity) + " " + id));
                if (entity.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)
                        && entity.getAttributeValue(Attributes.ATTACK_DAMAGE) <= 0) {
                    zeroAttack.add(describe(entity));
                }
            }
            ctx.check(legacy).as("legacy_effect_ 开头的修饰符").isEmpty();
            ctx.check(leftover).as("清除效果后仍留着的效果修饰符").isEmpty();
            ctx.check(zeroAttack).as("清除效果后攻击力仍为 0 的生物").isEmpty();
        });
    }
}
