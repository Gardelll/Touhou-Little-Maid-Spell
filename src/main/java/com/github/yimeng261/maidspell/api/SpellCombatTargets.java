package com.github.yimeng261.maidspell.api;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.task.SpellCombatMeleeTask;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.BiPredicate;

/**
 * 法术战斗任务（近战与远程）的目标判定。
 *
 * @since API 1
 */
public final class SpellCombatTargets {
    private SpellCombatTargets() {
    }

    /**
     * 注册目标过滤器，在模组构造或通用初始化阶段调用。法术战斗的目标须通过所有过滤器：
     * 不通过时索敌跳过它、继续找下一个最近的目标，已锁定的目标放弃（普通与坐下/骑乘姿态都是），施法与走位行为也不对它启动。
     * <p>
     * 只在服务端调用，每 tick 可能调用多次，应保持廉价；抛出的异常记一次警告并视为放行。
     */
    public static void addTargetFilter(BiPredicate<EntityMaid, LivingEntity> filter) {
        SpellCombatMeleeTask.addTargetFilter(filter);
    }

    /**
     * 该目标是否是有效的法术战斗目标：不是玩家、女仆不在压制区、不是坐姿 IMaid、不是友军（见 {@link MaidSpellAllies}），且通过所有过滤器
     */
    public static boolean isValidTarget(EntityMaid maid, LivingEntity target) {
        return SpellCombatMeleeTask.isValidSpellCombatTarget(maid, target);
    }
}
