package com.github.yimeng261.maidspell.api;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketBarRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;

/**
 * 末影腰包 HUD 上女仆信息行的附加条（例如法力）。
 * <p>
 * 服务端生成末影腰包女仆列表时对每位女仆调用已注册的提供者，结果随原有的包下发，客户端统一排版：
 * 附加条按注册顺序排在血条下方，行高随条数增长。只在 HUD 显示，女仆选择界面与背包侧栏不显示。
 *
 * @since API 1
 */
public final class EnderPocketBars {
    /** 每位女仆最多下发的附加条数，超出的丢弃 */
    public static final int MAX_BARS = 4;

    private EnderPocketBars() {
    }

    /**
     * 注册附加条提供者，在模组构造或通用初始化阶段调用；同一 id 重复注册时后注册的覆盖，位置不变
     */
    public static void register(ResourceLocation id, BarProvider provider) {
        EnderPocketBarRegistry.register(id, provider);
    }

    /**
     * 只在服务端调用；返回空则这位女仆不显示该条。抛出的异常记一次警告并视为空
     */
    @FunctionalInterface
    public interface BarProvider {
        Optional<Bar> get(EntityMaid maid);
    }

    /**
     * 一根附加条。尺寸与字号在构造时夹到 HUD 行容得下的范围
     */
    public static final class Bar {
        public static final int MIN_WIDTH = 20;
        public static final int MAX_WIDTH = 79;
        public static final int MIN_HEIGHT = 3;
        public static final int MAX_HEIGHT = 12;
        public static final int MIN_ICON_SIZE = 6;
        public static final int MAX_ICON_SIZE = 16;
        public static final float MIN_TEXT_SCALE = 0.5F;
        public static final float MAX_TEXT_SCALE = 1.0F;

        private final float current;
        private final float max;
        private final int fillColor;
        private final boolean showLabel;
        @Nullable
        private final Component label;
        @Nullable
        private final ResourceLocation icon;
        private final int width;
        private final int height;
        private final int iconSize;
        private final float textScale;

        /** 内部与网络解码用，外部请用 {@link #builder} */
        public Bar(float current, float max, int fillColor, boolean showLabel, @Nullable Component label,
                   @Nullable ResourceLocation icon, int width, int height, int iconSize, float textScale) {
            this.current = current;
            this.max = max;
            this.fillColor = fillColor;
            this.showLabel = showLabel;
            this.label = label;
            this.icon = icon;
            this.width = Mth.clamp(width, MIN_WIDTH, MAX_WIDTH);
            this.height = Mth.clamp(height, MIN_HEIGHT, MAX_HEIGHT);
            this.iconSize = Mth.clamp(iconSize, MIN_ICON_SIZE, MAX_ICON_SIZE);
            this.textScale = Float.isFinite(textScale) ? Mth.clamp(textScale, MIN_TEXT_SCALE, MAX_TEXT_SCALE) : MIN_TEXT_SCALE;
        }

        /**
         * @param fillColor ARGB 填充色；边框与空槽配色由本模组统一
         */
        public static Builder builder(float current, float max, int fillColor) {
            return new Builder(current, max, fillColor);
        }

        public float current() {
            return current;
        }

        public float max() {
            return max;
        }

        public int fillColor() {
            return fillColor;
        }

        /** 是否在条上显示文字 */
        public boolean showsLabel() {
            return showLabel;
        }

        /** 自定义文字；为空且 {@link #showsLabel} 时显示 {@code floor(current)/floor(max)} */
        public Optional<Component> label() {
            return Optional.ofNullable(label);
        }

        /** 条尾图标的物品 id；客户端找不到该物品时不画 */
        public Optional<ResourceLocation> icon() {
            return Optional.ofNullable(icon);
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public int iconSize() {
            return iconSize;
        }

        public float textScale() {
            return textScale;
        }
    }

    public static final class Builder {
        private final float current;
        private final float max;
        private final int fillColor;
        private boolean showLabel = true;
        @Nullable
        private Component label;
        @Nullable
        private ResourceLocation icon;
        private int width = Bar.MAX_WIDTH;
        private int height = 5;
        private int iconSize = 9;
        private float textScale = 0.625F;

        private Builder(float current, float max, int fillColor) {
            this.current = current;
            this.max = max;
            this.fillColor = fillColor;
        }

        /** 条上居中的文字；不设则显示 {@code floor(current)/floor(max)} */
        public Builder label(Component label) {
            this.label = Objects.requireNonNull(label, "label");
            this.showLabel = true;
            return this;
        }

        /** 条上不显示文字 */
        public Builder hideLabel() {
            this.label = null;
            this.showLabel = false;
            return this;
        }

        /** 条尾图标的物品 id */
        public Builder icon(ResourceLocation itemId) {
            this.icon = Objects.requireNonNull(itemId, "itemId");
            return this;
        }

        /** 条的宽高，默认 79×5，夹在 {@link Bar#MIN_WIDTH}–{@link Bar#MAX_WIDTH}、{@link Bar#MIN_HEIGHT}–{@link Bar#MAX_HEIGHT} */
        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        /** 图标边长，默认 9，夹在 {@link Bar#MIN_ICON_SIZE}–{@link Bar#MAX_ICON_SIZE} */
        public Builder iconSize(int size) {
            this.iconSize = size;
            return this;
        }

        /** 文字缩放，默认 0.625，夹在 {@link Bar#MIN_TEXT_SCALE}–{@link Bar#MAX_TEXT_SCALE} */
        public Builder textScale(float scale) {
            this.textScale = scale;
            return this;
        }

        public Bar build() {
            return new Bar(current, max, fillColor, showLabel, label, icon, width, height, iconSize, textScale);
        }
    }
}
