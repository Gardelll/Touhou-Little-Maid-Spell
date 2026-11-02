package com.github.yimeng261.maidspell.api;

/**
 * 万法皆通对外 API 的版本信息。类本身存在即表示提供公开 API，附属模组可按类资源是否存在决定走 API 还是兜底。
 */
public final class MaidSpellApi {
    private static final int VERSION = 1;

    private MaidSpellApi() {
    }

    /**
     * API 版本，新增或变更公开 API 时递增。用方法而不是常量，避免调用方编译时把值内联进自己的字节码。
     * <ul>
     *     <li>1：{@link MaidSpellAllies}、{@link EnderPocketBars}、{@link RegisterSpellBookProvidersEvent}、{@link SpellCombatTargets}、{@link MaidSpellHooks}、{@link SpellWhiteListApi}</li>
     * </ul>
     */
    public static int version() {
        return VERSION;
    }
}
