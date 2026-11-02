package com.github.yimeng261.maidspell.api;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * 注册或替换法术书 provider。
 * <p>
 * mod 总线事件，在通用初始化阶段由主线程触发，客户端与服务端都会触发。内置 provider 已装好后才触发；
 * 事件结束后先应用全部 {@link #register}，再按调用顺序应用 {@link #override}，之后 provider 表冻结。
 *
 * @since API 1
 */
public final class RegisterSpellBookProvidersEvent extends Event implements IModBusEvent {
    private final Set<String> registeredModIds;
    private final List<ProviderRegistration> registrations = new ArrayList<>();
    private final List<ProviderOverride> overrides = new ArrayList<>();

    /** 由本模组构造，传入内置 provider 的 modId */
    public RegisterSpellBookProvidersEvent(Set<String> registeredModIds) {
        this.registeredModIds = Set.copyOf(registeredModIds);
    }

    /**
     * 为新模组注册 provider；modId 已有 provider 时记错误并忽略，要替换请用 {@link #override}
     */
    public synchronized void register(String modId, Supplier<? extends ISpellBookProvider<?, ?>> factory) {
        registrations.add(new ProviderRegistration(Objects.requireNonNull(modId, "modId"), Objects.requireNonNull(factory, "factory")));
    }

    /**
     * 替换已有 provider，原实例传入以便委托；多个替换按调用顺序串联，即按 mod 加载顺序：
     * 后调用的拿到的是前一个替换后的实例，而不是内置 provider。
     * modId 没有 provider（该模组未安装、版本不兼容而未装上等）时不生效
     */
    public synchronized void override(String modId, UnaryOperator<ISpellBookProvider<?, ?>> replacer) {
        overrides.add(new ProviderOverride(Objects.requireNonNull(modId, "modId"), Objects.requireNonNull(replacer, "replacer")));
    }

    /** 内置 provider 或本事件中已 {@link #register} 的 modId */
    public synchronized boolean isRegistered(String modId) {
        return registeredModIds.contains(modId) || registrations.stream().anyMatch(r -> r.modId().equals(modId));
    }

    /** 本模组内部读取 */
    public synchronized List<ProviderRegistration> registrations() {
        return Collections.unmodifiableList(new ArrayList<>(registrations));
    }

    /** 本模组内部读取 */
    public synchronized List<ProviderOverride> overrides() {
        return Collections.unmodifiableList(new ArrayList<>(overrides));
    }

    public record ProviderRegistration(String modId, Supplier<? extends ISpellBookProvider<?, ?>> factory) {
    }

    public record ProviderOverride(String modId, UnaryOperator<ISpellBookProvider<?, ?>> replacer) {
    }
}
