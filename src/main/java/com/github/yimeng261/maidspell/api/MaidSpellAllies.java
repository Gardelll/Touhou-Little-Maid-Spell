package com.github.yimeng261.maidspell.api;

import com.github.yimeng261.maidspell.compat.MaidSpellAllyResolver;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.function.Predicate;

/**
 * 女仆法术的敌我判定。各法术的伤害、控制、索敌与破法回响都按这里判定友军。
 * <p>
 * 内置判据：主人链（女仆、宠物、召唤物、弹射物逐级追到主人）与记分板队伍。附属模组可用 {@link #registerRule} 补充判据。
 *
 * @since API 1
 */
public final class MaidSpellAllies {
    private MaidSpellAllies() {
    }

    /**
     * 两个实体是否友好：同一实体、记分板队伍结盟、主人链相交，或任一已注册规则判为友好
     */
    public static boolean areFriendly(@Nullable Entity first, @Nullable Entity second) {
        return MaidSpellAllyResolver.areFriendly(first, second);
    }

    /**
     * 与 {@link #areFriendly} 判定相同，{@code caster} 一侧的主人链只算一次，供按同一个施法者过滤一批实体
     */
    public static Predicate<Entity> friendlyTo(Entity caster) {
        return MaidSpellAllyResolver.friendlyTo(caster);
    }

    /**
     * 注册友军规则，在模组构造或通用初始化阶段调用。规则对所有敌我判定生效，包括法术伤害拦截、索敌与破法回响。
     */
    public static void registerRule(AllyRule rule) {
        MaidSpellAllyResolver.registerAllyRule(rule);
    }

    /**
     * 友军规则，只能补充「友好」，不能否决内置判据已判定的友好。
     * <ul>
     *     <li>参数是调用方手里的原始实体（弹射物就是弹射物本身），需要时由规则自行解析所有者</li>
     *     <li>内置判据都判为不友好之后才查规则；{@code (a, b)} 不友好时再以 {@code (b, a)} 查一次，规则不必自行处理对称</li>
     *     <li>玩家造成的伤害只在间接伤害（法术、弹射物等）时以 {@code (受击者, 玩家)} 查规则，玩家近战不查</li>
     *     <li>可能在服务端每 tick 对一批实体调用，应保持廉价；抛出的异常记一次警告并视为不友好</li>
     * </ul>
     */
    @FunctionalInterface
    public interface AllyRule {
        boolean areAllied(Entity first, Entity second);
    }
}
