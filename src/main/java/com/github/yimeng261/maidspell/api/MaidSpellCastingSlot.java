package com.github.yimeng261.maidspell.api;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.spell.manager.SpellBookManager;

/**
 * 施法槽：占用施法槽的 provider（{@link ISpellBookProvider#occupiesCastingSlot}）同一时刻只有一个在施法。
 * 某个占槽 provider 正在施法或持有优先权（{@link ISpellBookProvider#holdsCastPriority}）时，其余占槽 provider 本轮不出手。
 * 自行选术的附属模组可据此反向让出。
 *
 * @since API 1
 */
public final class MaidSpellCastingSlot {
    private MaidSpellCastingSlot() {
    }

    /**
     * 除 {@code selfModId} 外，是否有占用施法槽的 provider 正在施法或持有施法优先权
     */
    public static boolean isOccupiedByOthers(EntityMaid maid, String selfModId) {
        return SpellBookManager.isCastingSlotOccupiedByOthers(maid, selfModId);
    }
}
