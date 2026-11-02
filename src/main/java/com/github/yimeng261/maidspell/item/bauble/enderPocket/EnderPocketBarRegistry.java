package com.github.yimeng261.maidspell.item.bauble.enderPocket;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.EnderPocketBars;
import com.mojang.logging.LogUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link EnderPocketBars} 的提供者表与附加条的网络编解码。
 * <p>
 * 编解码器放在这里而不是 {@code EnderPocketMaidInfo} 里：旧版附属模组的 mixin 会包住那个类静态初始化里的每一次 {@code StreamCodec.of}。
 */
public final class EnderPocketBarRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 注册顺序即显示顺序；写时整表替换，读时无锁 */
    private static volatile Map<ResourceLocation, EnderPocketBars.BarProvider> providers = Map.of();
    private static final Set<ResourceLocation> FAILED_PROVIDERS = ConcurrentHashMap.newKeySet();

    public static final StreamCodec<RegistryFriendlyByteBuf, EnderPocketBars.Bar> BAR_STREAM_CODEC = StreamCodec.of(
            (buf, bar) -> {
                buf.writeFloat(bar.current());
                buf.writeFloat(bar.max());
                buf.writeInt(bar.fillColor());
                buf.writeBoolean(bar.showsLabel());
                ByteBufCodecs.optional(ComponentSerialization.STREAM_CODEC).encode(buf, bar.label());
                ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC).encode(buf, bar.icon());
                ByteBufCodecs.VAR_INT.encode(buf, bar.width());
                ByteBufCodecs.VAR_INT.encode(buf, bar.height());
                ByteBufCodecs.VAR_INT.encode(buf, bar.iconSize());
                buf.writeFloat(bar.textScale());
            },
            buf -> new EnderPocketBars.Bar(
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readInt(),
                    buf.readBoolean(),
                    ByteBufCodecs.optional(ComponentSerialization.STREAM_CODEC).decode(buf).orElse(null),
                    ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC).decode(buf).orElse(null),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    buf.readFloat()
            )
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, List<EnderPocketBars.Bar>> BAR_LIST_STREAM_CODEC =
            ByteBufCodecs.collection(ArrayList::new, BAR_STREAM_CODEC, EnderPocketBars.MAX_BARS);

    private EnderPocketBarRegistry() {
    }

    public static synchronized void register(ResourceLocation id, EnderPocketBars.BarProvider provider) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(provider, "provider");
        Map<ResourceLocation, EnderPocketBars.BarProvider> next = new LinkedHashMap<>(providers);
        next.put(id, provider);
        providers = next;
    }

    /** 按注册顺序收集这位女仆的附加条，最多 {@link EnderPocketBars#MAX_BARS} 根 */
    public static List<EnderPocketBars.Bar> collect(EntityMaid maid) {
        Map<ResourceLocation, EnderPocketBars.BarProvider> snapshot = providers;
        if (snapshot.isEmpty()) {
            return List.of();
        }
        List<EnderPocketBars.Bar> bars = new ArrayList<>();
        for (Map.Entry<ResourceLocation, EnderPocketBars.BarProvider> entry : snapshot.entrySet()) {
            if (bars.size() >= EnderPocketBars.MAX_BARS) {
                break;
            }
            try {
                Optional<EnderPocketBars.Bar> bar = entry.getValue().get(maid);
                if (bar != null) {
                    bar.ifPresent(bars::add);
                }
            } catch (RuntimeException e) {
                if (FAILED_PROVIDERS.add(entry.getKey())) {
                    LOGGER.warn("Ender pocket bar provider {} threw; skipping it", entry.getKey(), e);
                }
            }
        }
        return List.copyOf(bars);
    }

    /** 条上默认文字 */
    public static Component defaultLabel(EnderPocketBars.Bar bar) {
        return Component.literal(Math.max(0, (int) Math.floor(bar.current())) + "/" + Math.max(0, (int) Math.floor(bar.max())));
    }
}
