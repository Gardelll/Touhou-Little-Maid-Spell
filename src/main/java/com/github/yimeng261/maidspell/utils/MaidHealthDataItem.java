package com.github.yimeng261.maidspell.utils;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

/** 由 Mixin 混入女仆生命同步数据项，记下它属于哪位女仆。 */
public interface MaidHealthDataItem {
    void maidspell$bindHealthOwner(EntityMaid maid);
}
