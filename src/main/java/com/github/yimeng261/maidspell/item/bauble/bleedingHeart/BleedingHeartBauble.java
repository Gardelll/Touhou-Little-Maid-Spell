package com.github.yimeng261.maidspell.item.bauble.bleedingHeart;

import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.api.IExtendBauble;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import net.minecraft.world.entity.player.Player;

public class BleedingHeartBauble implements IExtendBauble {
    static {
        Global.bauble_damageProcessors_aft.put(MaidSpellItems.itemDesc(MaidSpellItems.BLEEDING_HEART),(event, maid) -> {
            float amount = event.getOriginalDamage();
            Player owner = (Player) maid.getOwner();
            if (owner != null) {
                owner.heal(amount*0.1f);
            }
            maid.heal(amount*0.1f);
            return null;
        });

    }

}
