package com.github.yimeng261.maidspell.client.overlay;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.EnderPocketBars;
import com.github.yimeng261.maidspell.client.EnderPocketClientConfig;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketBarRegistry;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 末影腰包关联女仆的状态栏
 */
@OnlyIn(Dist.CLIENT)
public final class EnderPocketHudOverlay implements LayeredDraw.Layer {
    public static final int ROW_WIDTH = 176;
    private static final int ROW_HEIGHT = 44;
    private static final int ROW_SPACING = 3;
    /** 附加条之间、附加条与坐标之间的间距 */
    private static final int BAR_GAP = 2;
    private static final int EXTRA_BAR_BORDER = 0xFF0E0E14;
    private static final int EXTRA_BAR_EMPTY = 0xFF262630;
    private static final Map<ResourceLocation, ItemStack> ICON_STACKS = new HashMap<>();
    private static final Map<UUID, EntityMaid> PREVIEW_MAIDS = new HashMap<>();
    private static List<EnderPocketService.EnderPocketMaidInfo> maidInfos = List.of();
    private static ItemStack enderPocketIcon;

    public static void update(List<EnderPocketService.EnderPocketMaidInfo> infos) {
        maidInfos = List.copyOf(infos);
        PREVIEW_MAIDS.keySet().removeIf(id -> infos.stream().noneMatch(info -> info.maidUUID.equals(id)));
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.screen != null
                || !EnderPocketClientConfig.HUD_ENABLED.get()) {
            return;
        }
        List<EnderPocketService.EnderPocketMaidInfo> visibleInfos = getVisibleMaidInfos();
        if (visibleInfos.isEmpty()) {
            return;
        }

        int usableBottom = screenHeight - Math.max(mc.gui.leftHeight, mc.gui.rightHeight) - 6;
        int preferredX = Mth.clamp(EnderPocketClientConfig.HUD_X.get(),
                0, Math.max(0, screenWidth - ROW_WIDTH));
        int preferredY = Mth.clamp(EnderPocketClientConfig.HUD_Y.get(),
                0, Math.max(0, usableBottom - rowHeight(visibleInfos.get(0))));
        int maxRows = rowsThatFit(visibleInfos, usableBottom);
        if (maxRows == 0) {
            return;
        }

        List<Rect2i> exclusions = HudExclusionAreas.get();
        int[] position = null;
        int visibleRows = maxRows;
        while (visibleRows > 0 && position == null) {
            int hudHeight = stackHeight(visibleInfos, visibleRows);
            position = findNearestFreePosition(preferredX, preferredY, ROW_WIDTH, hudHeight,
                    screenWidth, usableBottom, exclusions);
            if (position == null) {
                visibleRows--;
            }
        }
        if (position == null) {
            return;
        }

        renderRows(graphics, mc, visibleInfos, visibleRows, position[0], position[1]);
    }

    /** 一行的高度：基础行高加上附加条占的高度 */
    private static int rowHeight(EnderPocketService.EnderPocketMaidInfo info) {
        return ROW_HEIGHT + barsHeight(info);
    }

    private static int barsHeight(EnderPocketService.EnderPocketMaidInfo info) {
        int height = 0;
        for (EnderPocketBars.Bar bar : info.extraBars) {
            height += barLineHeight(bar) + BAR_GAP;
        }
        return height;
    }

    private static int barLineHeight(EnderPocketBars.Bar bar) {
        return bar.icon().isPresent() ? Math.max(bar.height(), bar.iconSize()) : bar.height();
    }

    /** 前 {@code rows} 行叠起来（含行间距）的总高度 */
    private static int stackHeight(List<EnderPocketService.EnderPocketMaidInfo> infos, int rows) {
        int height = 0;
        for (int i = 0; i < rows; i++) {
            height += rowHeight(infos.get(i)) + ROW_SPACING;
        }
        return Math.max(0, height - ROW_SPACING);
    }

    /** 从上往下叠，高度不超过 {@code available} 时最多容得下几行 */
    private static int rowsThatFit(List<EnderPocketService.EnderPocketMaidInfo> infos, int available) {
        int rows = 0;
        while (rows < infos.size() && stackHeight(infos, rows + 1) <= available) {
            rows++;
        }
        return rows;
    }

    private static void renderRows(GuiGraphics graphics, Minecraft mc, List<EnderPocketService.EnderPocketMaidInfo> infos,
                                   int rows, int x, int y) {
        int rowY = y;
        for (int i = 0; i < rows; i++) {
            EnderPocketService.EnderPocketMaidInfo info = infos.get(i);
            renderRow(graphics, mc, info, x, rowY);
            rowY += rowHeight(info) + ROW_SPACING;
        }
    }

    private static int[] findNearestFreePosition(int preferredX, int preferredY, int width, int height,
                                                 int screenWidth, int screenBottom, List<Rect2i> exclusions) {
        int maxX = Math.max(0, screenWidth - width);
        int maxY = Math.max(0, screenBottom - height);
        List<Integer> xCandidates = new ArrayList<>();
        List<Integer> yCandidates = new ArrayList<>();
        xCandidates.add(Mth.clamp(preferredX, 0, maxX));
        xCandidates.add(0);
        xCandidates.add(maxX);
        yCandidates.add(Mth.clamp(preferredY, 0, maxY));
        yCandidates.add(0);
        yCandidates.add(maxY);

        for (Rect2i area : exclusions) {
            xCandidates.add(Mth.clamp(area.getX() - width - 4, 0, maxX));
            xCandidates.add(Mth.clamp(area.getX() + area.getWidth() + 4, 0, maxX));
            yCandidates.add(Mth.clamp(area.getY() - height - 4, 0, maxY));
            yCandidates.add(Mth.clamp(area.getY() + area.getHeight() + 4, 0, maxY));
        }

        int[] best = null;
        long bestDistance = Long.MAX_VALUE;
        for (int x : xCandidates) {
            for (int y : yCandidates) {
                if (!isFree(x, y, width, height, exclusions)) {
                    continue;
                }
                long dx = x - (long) preferredX;
                long dy = y - (long) preferredY;
                long distance = dx * dx + dy * dy;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = new int[]{x, y};
                }
            }
        }
        return best;
    }

    private static boolean isFree(int x, int y, int width, int height, List<Rect2i> exclusions) {
        for (Rect2i area : exclusions) {
            if (area.getWidth() <= 0 || area.getHeight() <= 0) {
                continue;
            }
            if (x < area.getX() + area.getWidth() && x + width > area.getX()
                    && y < area.getY() + area.getHeight() && y + height > area.getY()) {
                return false;
            }
        }
        return true;
    }

    public static int getEditorPreviewHeight(int availableBottom, int y) {
        List<EnderPocketService.EnderPocketMaidInfo> visibleInfos = getVisibleMaidInfos();
        if (visibleInfos.isEmpty()) {
            return ROW_HEIGHT;
        }
        return stackHeight(visibleInfos, Math.max(1, rowsThatFit(visibleInfos, availableBottom - y)));
    }

    public static void renderEditorPreview(GuiGraphics graphics, int x, int y, int availableBottom) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        List<EnderPocketService.EnderPocketMaidInfo> visibleInfos = getVisibleMaidInfos();
        if (visibleInfos.isEmpty()) {
            graphics.fill(x, y, x + ROW_WIDTH, y + ROW_HEIGHT, 0xB8101018);
            graphics.renderOutline(x, y, ROW_WIDTH, ROW_HEIGHT, 0xFF565664);
            graphics.drawCenteredString(mc.font,
                    Component.translatable("gui.maidspell.ender_pocket.hud_editor.title"),
                    x + ROW_WIDTH / 2, y + 18, 0xFFFFFFFF);
            return;
        }

        int visibleRows = Math.max(1, rowsThatFit(visibleInfos, availableBottom - y));
        renderRows(graphics, mc, visibleInfos, visibleRows, x, y);
    }

    private static List<EnderPocketService.EnderPocketMaidInfo> getVisibleMaidInfos() {
        return maidInfos.stream()
                .filter(info -> EnderPocketClientConfig.isMaidVisible(info.maidUUID))
                .toList();
    }

    private static void renderRow(GuiGraphics graphics, Minecraft mc,
                                  EnderPocketService.EnderPocketMaidInfo info, int x, int y) {
        int rowHeight = rowHeight(info);
        graphics.fill(x, y, x + ROW_WIDTH, y + rowHeight, 0xB8101018);
        graphics.renderOutline(x, y, ROW_WIDTH, rowHeight,
                info.hasAnchorCore ? 0xFF8A6BC7 : 0xFF565664);

        renderAvatar(graphics, mc, info, x + 2, y + 2);

        // 在客户端解析翻译组件（服务端只传组件，不传已解析文本）。
        String name = trimToWidth(mc, info.maidName.getString(), 92);
        graphics.drawString(mc.font, name, x + 42, y + 4, 0xFFFFFFFF, true);
        graphics.pose().pushPose();
        graphics.pose().translate(x + ROW_WIDTH - 13, y + 2, 0);
        graphics.pose().scale(0.625F, 0.625F, 1.0F);
        graphics.renderItem(enderPocketIcon(), 0, 0);
        graphics.pose().popPose();

        int barX = x + 42;
        int barY = y + 17;
        int barWidth = 79;
        float ratio = info.maxHealth <= 0.0F ? 0.0F : Mth.clamp(info.health / info.maxHealth, 0.0F, 1.0F);
        graphics.fill(barX, barY, barX + barWidth, barY + 8, 0xFF28191D);
        graphics.fill(barX + 1, barY + 1, barX + 1 + Math.round((barWidth - 2) * ratio), barY + 7,
                ratio > 0.25F ? 0xFFD74752 : 0xFF9E2430);
        String health = Math.max(0, Mth.ceil(info.health)) + "/" + Math.max(0, Mth.ceil(info.maxHealth));
        graphics.drawCenteredString(mc.font, health, barX + barWidth / 2, barY, 0xFFFFFFFF);

        graphics.drawString(mc.font, "\u25C6 " + info.armor, x + 125, y + 17, 0xFFB8D5ED, true);

        // 附加条插在血条与坐标之间，坐标一行随之下移
        int lineY = y + 27;
        for (EnderPocketBars.Bar bar : info.extraBars) {
            int lineHeight = barLineHeight(bar);
            renderExtraBar(graphics, mc, bar, barX, lineY, lineHeight);
            lineY += lineHeight + BAR_GAP;
        }
        int textY = y + 31 + barsHeight(info);

        String coordinates = Mth.floor(info.x) + ", " + Mth.floor(info.y) + ", " + Mth.floor(info.z);
        graphics.drawString(mc.font, trimToWidth(mc, coordinates, 96),
                x + 42, textY, 0xFFB8B8C5, false);

        boolean sameDimension = mc.level.dimension().equals(info.levelKey);
        renderDirectionArrow(graphics, mc, info, x + 163, y + 14,
                sameDimension ? 0xFFFFFFFF : 0xFFB78CFF);
        if (sameDimension) {
            int distance = Mth.floor(Math.sqrt(mc.player.distanceToSqr(info.x, info.y, info.z)));
            graphics.drawCenteredString(mc.font, distance + "m", x + 158, textY, 0xFFD6D6DE);
        } else {
            graphics.drawCenteredString(mc.font, "DIM", x + 158, textY, 0xFFB78CFF);
        }
    }

    private static void renderExtraBar(GuiGraphics graphics, Minecraft mc, EnderPocketBars.Bar bar,
                                       int barX, int lineY, int lineHeight) {
        int width = bar.width();
        int height = bar.height();
        int barY = lineY + (lineHeight - height) / 2;
        float ratio = bar.max() <= 0.0F ? 0.0F : Mth.clamp(bar.current() / bar.max(), 0.0F, 1.0F);
        graphics.fill(barX, barY, barX + width, barY + height, EXTRA_BAR_BORDER);
        graphics.fill(barX + 1, barY + 1, barX + width - 1, barY + height - 1, EXTRA_BAR_EMPTY);
        int fillWidth = Math.round((width - 2) * ratio);
        if (fillWidth > 0) {
            graphics.fill(barX + 1, barY + 1, barX + 1 + fillWidth, barY + height - 1, bar.fillColor());
        }

        if (bar.showsLabel()) {
            Component label = bar.label().orElseGet(() -> EnderPocketBarRegistry.defaultLabel(bar));
            float scale = bar.textScale();
            graphics.pose().pushPose();
            // 字形约 7px 高，在条内垂直居中
            graphics.pose().translate(barX + width / 2.0F, barY + (height - 7 * scale) / 2.0F, 0.0F);
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.drawCenteredString(mc.font, label, 0, 0, 0xFFFFFFFF);
            graphics.pose().popPose();
        }

        bar.icon().map(EnderPocketHudOverlay::iconStack).filter(stack -> !stack.isEmpty()).ifPresent(stack -> {
            int iconSize = bar.iconSize();
            float iconScale = iconSize / 16.0F;
            graphics.pose().pushPose();
            graphics.pose().translate(barX + width + 2, lineY + (lineHeight - iconSize) / 2.0F, 0.0F);
            graphics.pose().scale(iconScale, iconScale, 1.0F);
            graphics.renderItem(stack, 0, 0);
            graphics.pose().popPose();
        });
    }

    /** 找不到的物品得到空堆，不画 */
    private static ItemStack iconStack(ResourceLocation itemId) {
        return ICON_STACKS.computeIfAbsent(itemId, id -> BuiltInRegistries.ITEM.getOptional(id)
                .map(ItemStack::new)
                .orElse(ItemStack.EMPTY));
    }

    private static void renderAvatar(GuiGraphics graphics, Minecraft mc,
                                     EnderPocketService.EnderPocketMaidInfo info, int x, int y) {
        Entity entity = mc.level.getEntity(info.maidEntityId);
        EntityMaid maid = entity instanceof EntityMaid candidate
                && candidate.getUUID().equals(info.maidUUID) ? candidate : null;
        if (maid == null) {
            maid = PREVIEW_MAIDS.get(info.maidUUID);
            if (maid == null || maid.level() != mc.level) {
                maid = new EntityMaid(mc.level);
                PREVIEW_MAIDS.put(info.maidUUID, maid);
            }
            maid.setModelId(info.modelId);
            maid.setCustomNameVisible(false);
        }

        graphics.enableScissor(x, y, x + 34, y + 40);
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI).rotateY((float) Math.PI);
        InventoryScreen.renderEntityInInventory(graphics, x + 17, y + 39, 16, new Vector3f(), pose, null, maid);
        graphics.disableScissor();
    }

    private static void renderDirectionArrow(GuiGraphics graphics, Minecraft mc,
                                             EnderPocketService.EnderPocketMaidInfo info,
                                             int x, int y, int color) {
        double dx = info.x - mc.player.getX();
        double dz = info.z - mc.player.getZ();
        float bearing = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float rotation = Mth.wrapDegrees(bearing - mc.player.getYRot());
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(rotation));
        graphics.drawCenteredString(mc.font, "\u2191", 0, -4, color);
        graphics.pose().popPose();
    }

    private static ItemStack enderPocketIcon() {
        if (enderPocketIcon == null) {
            enderPocketIcon = new ItemStack(MaidSpellItems.ENDER_POCKET.get());
        }
        return enderPocketIcon;
    }

    private static String trimToWidth(Minecraft mc, String text, int width) {
        if (mc.font.width(text) <= width) {
            return text;
        }
        return mc.font.plainSubstrByWidth(text, Math.max(0, width - mc.font.width("..."))) + "...";
    }
}
