package com.github.yimeng261.maidspell.compat.irons_spellbooks.event;

import com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox.MagicalWinefoxBossEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * 在实体真正加入世界的那一刻武装星之魔女的移动判定。
 *
 * <p>{@code EntityJoinLevelEvent} 早于 {@code Entity#onAddedToLevel()}，用它堵掉
 * "别人的 join 处理器还能写位置"的那一小段窗口；实体自己的 {@code onAddedToLevel}
 * 再兜一层（两条路都必须幂等）。</p>
 *
 * <p>这个类引用酒狐实体，而酒狐的父类来自铁魔法，所以只能由
 * {@link com.github.yimeng261.maidspell.compat.irons_spellbooks.IronsSpellbooksCompat#init}
 * 在确认铁魔法存在之后再注册。</p>
 */
public final class WinefoxMovementGuardEvents {
    private WinefoxMovementGuardEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof MagicalWinefoxBossEntity boss)) {
            return;
        }
        boss.maidspell$armMovementGuard();
    }
}
