package com.github.yimeng261.maidspell.api;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.handler.BaubleItemHandler;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.bauble.spellWhiteList.SpellWhiteList;
import com.github.yimeng261.maidspell.item.bauble.spellWhiteList.contianer.SpellWhiteListSpellManager;
import com.github.yimeng261.maidspell.spell.manager.BaubleStateManager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.Set;

/**
 * 法术白名单饰品：名单内的铁魔法法术可以对主人施放。取法与内置铁魔法 provider 一致，以饰品栏里第一个法术白名单为准。
 *
 * @since API 1
 */
public final class SpellWhiteListApi {
    private SpellWhiteListApi() {
    }

    /**
     * 女仆饰品栏里第一个法术白名单记录的法术 id（带命名空间，如 {@code irons_spellbooks:blessing_of_life}），不可变；
     * 未佩戴、未安装铁魔法或在客户端调用时为空
     */
    public static Set<String> getOwnerCastableSpellIds(EntityMaid maid) {
        if (maid.level().isClientSide() || !ModList.get().isLoaded("irons_spellbooks")
                || !BaubleStateManager.hasBauble(maid, MaidSpellItems.SPELL_WHITE_LIST)) {
            return Set.of();
        }
        BaubleItemHandler baubles = maid.getMaidBauble();
        for (int slot = 0; slot < baubles.getSlots(); slot++) {
            ItemStack stack = baubles.getStackInSlot(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof SpellWhiteList) {
                return Set.copyOf(SpellWhiteListSpellManager.getStoredSpellIds(stack, maid.level().registryAccess()));
            }
        }
        return Set.of();
    }

    /**
     * 该法术是否在白名单内、可对主人施放
     */
    public static boolean canTargetOwner(EntityMaid maid, String spellId) {
        return getOwnerCastableSpellIds(maid).contains(spellId);
    }
}
