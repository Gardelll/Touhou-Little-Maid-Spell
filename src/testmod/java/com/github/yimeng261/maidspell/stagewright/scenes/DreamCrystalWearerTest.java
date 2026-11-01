package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.stagewright.support.Maids;
import com.github.yimeng261.maidspell.stagewright.support.Owners;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.Map;
import java.util.TreeMap;

import static com.github.yimeng261.maidspell.stagewright.scenes.DreamCrystalFixtures.CRYSTAL;
import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 女仆佩戴梦云水晶的常驻效果（专用服）：生命上限 +50%、攻速翻倍、背包工具每秒修 1 点耐久、
 * Curios 每种栏位多 1 格，主人获得「万法皆通」进度；取下后这些都撤掉。效果每 20 tick 刷新一次。
 */
@Scene("tlms")
@DisplayName("梦云水晶：女仆佩戴的常驻效果")
class DreamCrystalWearerTest {
    private static final int REFRESH_WAIT = 30;
    private static final int TOOL_DAMAGE = 50;

    @DisplayName("戴上后生命上限 +50%、攻速翻倍、背包工具修耐久、Curios 各栏位 +1、主人获得万法皆通；取下后撤掉")
    @Test
    void wearerEffects(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer owner = ctx.get(() -> Owners.visitor(ctx, "TlmsCrystalOwner", 2, 0, 0));
        EntityMaid maid = ctx.get(() -> Owners.maid(ctx, owner, 0, 0, 0));
        double[] base = ctx.get(() -> new double[]{maid.getMaxHealth(), maid.getAttributeValue(Attributes.ATTACK_SPEED)});
        Map<String, Integer> slotsBefore = ctx.get(() -> curioSlots(maid));
        ItemStack tool = ctx.get(() -> {
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            sword.setDamageValue(TOOL_DAMAGE);
            ((IItemHandlerModifiable) maid.getAvailableInv(false)).setStackInSlot(0, sword);
            Maids.putOn(maid, 0, com.github.yimeng261.maidspell.stagewright.support.Actors.stack(CRYSTAL));
            return maid.getAvailableInv(false).getStackInSlot(0);
        });
        ctx.ticks(REFRESH_WAIT * 2);
        ctx.run(() -> {
            ctx.check(maid.getMaxHealth()).as("生命上限").isCloseTo(base[0] * 1.5, 1e-3);
            ctx.check(maid.getAttributeValue(Attributes.ATTACK_SPEED)).as("攻速").isCloseTo(base[1] * 2.0, 1e-3);
            ctx.check(tool.getDamageValue()).as("背包里铁剑的损耗").isLessThan(TOOL_DAMAGE);
            Map<String, Integer> slots = curioSlots(maid);
            Map<String, Integer> expected = new TreeMap<>();
            slotsBefore.forEach((type, count) -> expected.put(type, count + 1));
            ctx.record("curioSlotsBefore", slotsBefore);
            ctx.check(slots).as("Curios 各栏位格数").isEqualTo(expected);
            AdvancementHolder advancement = ctx.server().getAdvancements()
                    .get(ResourceLocation.parse(NS + "dream_crystal/all_spells_mastered"));
            ctx.check(advancement).as("万法皆通进度").isNotNull();
            if (advancement != null) {
                ctx.check(owner.getAdvancements().getOrStartProgress(advancement).isDone()).as("主人获得万法皆通").isTrue();
            }
            // 生命高过原上限，取下后要截回去
            maid.heal(maid.getMaxHealth());
            ctx.check(maid.getHealth()).as("取下前的生命").isGreaterThan(base[0]);
            Maids.takeOff(maid, 0);
        });
        ctx.ticks(REFRESH_WAIT);
        ctx.run(() -> {
            ctx.check(maid.getMaxHealth()).as("取下后的生命上限").isCloseTo(base[0], 1e-3);
            ctx.check(maid.getHealth()).as("取下后的生命").isAtMost(maid.getMaxHealth());
            ctx.check(maid.getAttributeValue(Attributes.ATTACK_SPEED)).as("取下后的攻速").isCloseTo(base[1], 1e-3);
            ctx.check(curioSlots(maid)).as("取下后 Curios 各栏位格数").isEqualTo(slotsBefore);
        });
    }

    /** 女仆每种 Curios 栏位的格数；没有 Curios 物品栏时为空。 */
    private static Map<String, Integer> curioSlots(EntityMaid maid) {
        Map<String, Integer> out = new TreeMap<>();
        CuriosApi.getCuriosInventory(maid).ifPresent(handler ->
                handler.getCurios().forEach((type, stacks) -> out.put(type, stacks.getSlots())));
        return out;
    }
}
