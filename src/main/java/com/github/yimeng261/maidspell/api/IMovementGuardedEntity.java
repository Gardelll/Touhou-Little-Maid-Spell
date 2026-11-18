package com.github.yimeng261.maidspell.api;

/**
 * 需要无视外部位移写入的实体所实现的移动权限契约。
 *
 * <p>与 {@code IAuthoritativeHealth} 不同，这里关心的是<b>谁在驱动</b>：只有实体自己的
 * {@code aiStep} 调用栈正在执行，或处于显式的内部放行期间，写入才被接受。
 * {@code Entity#setPosRaw} 的 mixin 与实体自身的覆写在改位置或速度之前都会询问
 * {@link #maidspell$canChangeMovement()}。</p>
 *
 * <p>客户端从不拦截：生成、传送与插值包必须照常生效，实体实际在哪里以服务端为准。</p>
 */
public interface IMovementGuardedEntity {
    /**
     * @return 本次移动写入是否允许
     */
    boolean maidspell$canChangeMovement();

    /**
     * 记录一次被拒绝的写入，供诊断使用。
     *
     * @param method 被拒绝的公开入口
     */
    void maidspell$onRejectedMovement(String method);

    /**
     * 执行一次允许绕过判定的内部移动写入（生命周期切换、管理员命令）。
     */
    void maidspell$withMovementBypass(Runnable action);

    /**
     * @return 此刻外部移动写入是否会被拒绝
     */
    boolean maidspell$blocksExternalMovement();
}
