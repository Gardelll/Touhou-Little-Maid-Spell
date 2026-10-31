package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.api.ISpellBookProvider;
import com.github.yimeng261.maidspell.spell.manager.SpellBookManager;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.util.ArrayList;
import java.util.List;

/** 法术黑名单配置。 */
@Scene("tlms")
class SpellBlacklistTest {
    private static final String FIREBOLT_BOOK = "irons_spellbooks:gold_spell_book[irons_spellbooks:spell_container="
            + "{maxSpells:1,mustEquip:false,spellWheel:true,data:[{id:\"irons_spellbooks:firebolt\",index:0,level:1,locked:false}]}]";

    /** 黑名单里的铁魔法法术女仆不会施放；移出黑名单后照常施放。 */
    @Test
    void blocksIronsSpell(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        record Rig(ISpellBookProvider<?, ?> iss, EntityMaid maid, ItemStack book, Mob target) { }
        Rig rig = ctx.get(() -> {
            List<String> original = Config.ironsSpellBlacklist;
            Config.ironsSpellBlacklist = new ArrayList<>(List.of("irons_spellbooks:firebolt"));
            ctx.cleanup(() -> Config.ironsSpellBlacklist = original);
            ISpellBookProvider<?, ?> iss = SpellBookManager.getProvider("irons_spellbooks");
            EntityMaid maid = Actors.sittingMaid(ctx, 0, 0, 0);
            ItemStack book = Actors.parse(ctx, FIREBOLT_BOOK);
            maid.setItemSlot(EquipmentSlot.OFFHAND, book);
            return new Rig(iss, maid, book, Actors.spawn(ctx, "minecraft:zombie", 0, 0, 5, true));
        });
        ctx.ticks(2);
        ctx.run(() -> cast(rig.maid(), rig.iss(), rig.book(), rig.target()));
        ctx.ticks(20);
        ctx.run(() -> {
            ctx.check(rig.iss().isCasting(rig.maid())).as("火球术在黑名单时女仆施法").isFalse();
            ctx.check(rig.target().getHealth()).as("火球术在黑名单时目标生命").isEqualTo(rig.target().getMaxHealth());
            Config.ironsSpellBlacklist = new ArrayList<>();
            cast(rig.maid(), rig.iss(), rig.book(), rig.target());
        });
        ctx.await(80, "移出黑名单后开始施法",
                () -> rig.iss().isCasting(rig.maid()) || rig.target().getHealth() < rig.target().getMaxHealth());
        ctx.record("note", "移出黑名单后开始施法");
    }

    private static void cast(EntityMaid maid, ISpellBookProvider<?, ?> iss, ItemStack book, Mob target) {
        SpellBookManager manager = SpellBookManager.getOrCreateManager(maid);
        manager.initSpellBooks(maid);
        manager.addSpellItem(maid, book);
        iss.setTarget(maid, target);
        manager.castSpell(maid);
    }
}
