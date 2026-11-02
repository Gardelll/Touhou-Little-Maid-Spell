package com.github.yimeng261.maidspell.stagewright.client;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.AbstractMaidContainerGui;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.client.EnderPocketClientConfig;
import com.github.yimeng261.maidspell.client.gui.EnderPocketHudEditorScreen;
import com.github.yimeng261.maidspell.client.gui.EnderPocketScreen;
import com.github.yimeng261.maidspell.client.overlay.EnderPocketHudOverlay;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Owners;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import com.github.yimeng261.maidspell.stagewright.support.Reflect;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Player;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Requires;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 末影腰包的客户端界面（集成服，直接操作同一 JVM 里的客户端）：面板的状态栏开关、每只女仆的显示勾选框、
 * 超过 8 只时的滚动、锚定女仆行的传送按钮布局；状态栏位置编辑界面的输入、重置、保存、取消与越界限制；
 * 状态栏数据每秒刷新；客户端配置文件生成并写回。
 */
@Scene("tlms.integrated")
@DisplayName("末影腰包客户端界面")
class EnderPocketClientTest {
    private static final Path CLIENT_CONFIG = FMLPaths.CONFIGDIR.get()
            .resolve("touhou_little_maid_spell").resolve("client").resolve("ender_pocket.toml");

    private static EnderPocketService.EnderPocketMaidInfo info(String name, boolean anchor) {
        return new EnderPocketService.EnderPocketMaidInfo(UUID.nameUUIDFromBytes(name.getBytes()), Component.literal(name), Level.OVERWORLD, -1,
                20, 20, 0, 0, 64, 0, anchor, "touhou_little_maid:hakurei_reimu", List.of());
    }

    private static List<EnderPocketService.EnderPocketMaidInfo> infos(int count) {
        return IntStream.range(0, count).mapToObj(i -> info("TlmsHud" + i, false)).toList();
    }

    /**
     * 打开末影腰包面板；测试结束时关掉界面，并恢复状态栏开关、位置与隐藏名单。
     * 服务端每秒的状态栏推送会把面板里的列表换成真实女仆，要检查面板控件的测试在同一次客户端调用里重开面板。
     */
    private static void open(Stage ctx, List<EnderPocketService.EnderPocketMaidInfo> infos) {
        ClientSide.run(ctx, () -> {
            boolean enabled = EnderPocketClientConfig.HUD_ENABLED.get();
            List<String> hidden = List.copyOf(EnderPocketClientConfig.HUD_HIDDEN_MAIDS.get());
            int x = EnderPocketClientConfig.HUD_X.get();
            int y = EnderPocketClientConfig.HUD_Y.get();
            ctx.cleanup(() -> Minecraft.getInstance().execute(() -> {
                Minecraft.getInstance().setScreen(null);
                EnderPocketClientConfig.HUD_HIDDEN_MAIDS.set(hidden);
                EnderPocketClientConfig.setHudEnabled(enabled);
                EnderPocketClientConfig.setPosition(x, y);
            }));
            Minecraft.getInstance().setScreen(new EnderPocketScreen(infos));
        });
    }

    private static Screen screen() {
        return Minecraft.getInstance().screen;
    }

    private static String configText() {
        try {
            return Files.readString(CLIENT_CONFIG);
        } catch (IOException e) {
            return "<读不到 " + CLIENT_CONFIG + ": " + e.getMessage() + ">";
        }
    }

    /** 客户端配置文件已生成，含状态栏的四项。 */
    @DisplayName("客户端配置文件已生成，含状态栏的四项配置")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void clientConfigGenerated(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player host, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        Players.host(ctx, host);
        ClientSide.awaitIdle(ctx);
        String text = configText();
        for (String key : List.of("hudEnabled", "hudX", "hudY", "hudHiddenMaidUuids")) {
            ctx.check(text.contains(key)).as(CLIENT_CONFIG.getFileName() + " 含 " + key).isTrue();
        }
    }

    /** 配置界面里每个配置文件入口、配置项与分组（通用与客户端）在中英文下都有标题；铁魔法法术黑名单的标题点明是铁魔法。 */
    @DisplayName("配置界面里每个配置文件入口、配置项与分组在中英文下都有标题，铁魔法法术黑名单标题点明是铁魔法")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void everyKeyTranslated(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player host, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        Players.host(ctx, host);
        ClientSide.awaitIdle(ctx);
        List<String> keys = new ArrayList<>();
        for (ModConfigSpec spec : List.of(Config.COMBAT_SPEC, Config.BAUBLES_SPEC, Config.RETREAT_SPEC,
                Config.COMPAT_SPEC, Config.WINEFOX_SPEC, Config.WORLDGEN_SPEC)) {
            collectKeys(spec.getSpec(), keys);
        }
        collectKeys(EnderPocketClientConfig.SPEC.getSpec(), keys);
        for (String file : List.of("combat", "baubles", "retreat", "compat", "boss", "worldgen", "client.ender.pocket")) {
            String section = NS.replace(':', '.') + "configuration.section.touhou.little.maid.spell." + file + ".toml";
            keys.add(section);
            keys.add(section + ".title");
        }
        List<String> result = ClientSide.call(ctx, () -> {
            List<String> lines = new ArrayList<>();
            for (String code : List.of("en_us", "zh_cn")) {
                ClientLanguage language = ClientSide.language(code);
                keys.stream().filter(key -> !language.has(key)).forEach(key -> lines.add(code + " 缺 " + key));
                lines.add(code + " 黑名单=" + language.getOrDefault(NS.replace(':', '.') + "configuration.spellBlacklist"));
            }
            return lines;
        });
        ctx.record("keys", keys.size());
        ctx.check(result.stream().filter(line -> line.contains(" 缺 ")).toList()).as("缺翻译的配置键").isEmpty();
        String en = result.stream().filter(line -> line.startsWith("en_us 黑名单=")).findFirst().orElse("");
        String zh = result.stream().filter(line -> line.startsWith("zh_cn 黑名单=")).findFirst().orElse("");
        ctx.check(en.contains("Iron")).as(en).isTrue();
        ctx.check(zh.contains("铁魔法")).as(zh).isTrue();
    }

    private static void collectKeys(UnmodifiableConfig spec, Collection<String> keys) {
        spec.valueMap().forEach((key, value) -> {
            keys.add(NS.replace(':', '.') + "configuration." + key);
            if (value instanceof UnmodifiableConfig section) {
                collectKeys(section, keys);
            }
        });
    }

    /** 面板左上角的"关闭/显示"按钮切换状态栏开关，文字随之切换，并写回配置文件。 */
    @DisplayName("面板的状态栏开关按钮切换开关与按钮文字，并写回配置文件")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void hudToggleButton(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player host, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        Players.host(ctx, host);
        ClientSide.awaitIdle(ctx);
        open(ctx, infos(1));
        String result = ClientSide.call(ctx, () -> {
            EnderPocketClientConfig.setHudEnabled(true);
            Minecraft.getInstance().setScreen(new EnderPocketScreen(infos(1)));
            AbstractWidget toggle = ClientSide.widget(screen(), "gui.maidspell.ender_pocket.hud_hide");
            if (toggle == null) {
                return "找不到状态栏开关按钮";
            }
            toggle.onClick(toggle.getX() + 1, toggle.getY() + 1);
            return EnderPocketClientConfig.HUD_ENABLED.get() + "|" + ClientSide.key(toggle.getMessage());
        });
        ctx.check(result).as("点一下后的开关状态与按钮文字").isEqualTo("false|gui.maidspell.ender_pocket.hud_show");
        ctx.check(configText().contains("hudEnabled = false")).as("配置文件写回 hudEnabled = false").isTrue();
    }

    /** 取消某只女仆的勾选：写进隐藏名单与配置文件，提示变为"隐藏"；编辑预览只按可见的女仆计高度。 */
    @DisplayName("取消某只女仆的勾选后写进隐藏名单与配置文件，编辑预览只按可见的女仆计高度")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void maidVisibilityCheckbox(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player host, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        Players.host(ctx, host);
        ClientSide.awaitIdle(ctx);
        List<EnderPocketService.EnderPocketMaidInfo> infos = infos(3);
        UUID first = infos.getFirst().maidUUID;
        open(ctx, infos);
        List<Object> result = ClientSide.call(ctx, () -> {
            Minecraft.getInstance().setScreen(new EnderPocketScreen(infos));
            EnderPocketHudOverlay.update(infos);
            int before = EnderPocketHudOverlay.getEditorPreviewHeight(1000, 0);
            AbstractWidget box = ClientSide.widget(screen(), "gui.maidspell.ender_pocket.hud_maid_visible");
            if (box == null) {
                return List.<Object>of("找不到勾选框");
            }
            box.onClick(box.getX() + 1, box.getY() + 1);
            return List.<Object>of(EnderPocketClientConfig.isMaidVisible(first), ClientSide.key(box.getMessage()),
                    before, EnderPocketHudOverlay.getEditorPreviewHeight(1000, 0));
        });
        ctx.record("result", result);
        ctx.check(result.size()).as("勾选框操作结果 " + result).isEqualTo(4);
        if (result.size() != 4) {
            return;
        }
        ctx.check(result.get(0)).as("取消勾选后第一只女仆可见").isEqualTo(false);
        ctx.check(result.get(1)).as("取消勾选后的提示").isEqualTo("gui.maidspell.ender_pocket.hud_maid_hidden");
        ctx.check((int) result.get(3)).as("预览高度（3 只 → 2 只可见）").isLessThan((int) result.get(2));
        ctx.check(configText().contains(first.toString())).as("配置文件写回隐藏的女仆").isTrue();
    }

    /** 全部取消勾选：编辑预览退回一个占位框的高度。 */
    @DisplayName("全部女仆取消勾选后，编辑预览退回一个占位框的高度")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void allHiddenLeavesPlaceholder(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player host, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        Players.host(ctx, host);
        ClientSide.awaitIdle(ctx);
        List<EnderPocketService.EnderPocketMaidInfo> infos = infos(2);
        open(ctx, infos);
        int height = ClientSide.call(ctx, () -> {
            EnderPocketHudOverlay.update(infos);
            infos.forEach(info -> EnderPocketClientConfig.setMaidVisible(info.maidUUID, false));
            return EnderPocketHudOverlay.getEditorPreviewHeight(1000, 0);
        });
        ctx.check(height).as("全部隐藏后的预览高度（一个占位框）").isEqualTo(44);
    }

    /** 超过 8 只时列表只显示 8 行，滚轮向下翻出第 9、10 只；不超过 8 只时滚轮无效。 */
    @DisplayName("超过 8 只女仆时列表显示 8 行并可滚轮翻出其余，不超过 8 只时滚轮无效")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void listScrollsBeyondEight(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player host, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        Players.host(ctx, host);
        ClientSide.awaitIdle(ctx);
        List<EnderPocketService.EnderPocketMaidInfo> infos = infos(10);
        open(ctx, infos);
        List<List<String>> result = ClientSide.call(ctx, () -> {
            Minecraft.getInstance().setScreen(new EnderPocketScreen(infos));
            Screen screen = screen();
            List<String> before = names(screen);
            screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0, -1);
            screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0, -1);
            List<String> after = names(screen());
            Minecraft.getInstance().setScreen(new EnderPocketScreen(infos(5)));
            Screen few = screen();
            List<String> fewBefore = names(few);
            few.mouseScrolled(few.width / 2.0, few.height / 2.0, 0, -1);
            return List.of(before, after, fewBefore, names(few));
        });
        ctx.check(result.get(0).size()).as("10 只时显示的行数").isEqualTo(8);
        ctx.check(result.get(1).contains("TlmsHud9")).as("向下滚两格后显示第 10 只").isTrue();
        ctx.check(result.get(1).contains("TlmsHud0")).as("向下滚两格后仍显示第 1 只").isFalse();
        ctx.check(result.get(3)).as("5 只时滚动后的行").isEqualTo(result.get(2));
    }

    private static List<String> names(Screen screen) {
        return ClientSide.widgets(screen).stream()
                .filter(w -> ClientSide.key(w.getMessage()) == null && !w.getMessage().getString().isEmpty())
                .map(w -> w.getMessage().getString()).toList();
    }

    /** 锚定女仆的行多一个传送按钮；勾选框、名字按钮、传送按钮互不重叠。 */
    @DisplayName("锚定女仆的行多一个传送按钮，勾选框、名字按钮、传送按钮互不重叠")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void anchorRowLayout(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player host, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        Players.host(ctx, host);
        ClientSide.awaitIdle(ctx);
        List<EnderPocketService.EnderPocketMaidInfo> infos = List.of(info("TlmsAnchor", true), info("TlmsPlain", false));
        open(ctx, infos);
        String result = ClientSide.call(ctx, () -> {
            Minecraft.getInstance().setScreen(new EnderPocketScreen(infos));
            Screen screen = screen();
            List<AbstractWidget> teleports = ClientSide.widgets(screen).stream()
                    .filter(w -> "gui.maidspell.ender_pocket.teleport".equals(ClientSide.key(w.getMessage()))).toList();
            AbstractWidget name = ClientSide.widget(screen, w -> w.getMessage().getString().equals("TlmsAnchor"));
            AbstractWidget box = ClientSide.widget(screen, w -> "gui.maidspell.ender_pocket.hud_maid_visible".equals(ClientSide.key(w.getMessage())));
            List<String> overlaps = new ArrayList<>();
            if (teleports.size() == 1 && name != null && box != null) {
                AbstractWidget teleport = teleports.getFirst();
                if (box.getRight() > name.getX()) {
                    overlaps.add("勾选框与名字");
                }
                if (name.getRight() > teleport.getX()) {
                    overlaps.add("名字与传送");
                }
            }
            return teleports.size() + "|" + overlaps;
        });
        ctx.check(result).as("传送按钮个数|重叠的控件").isEqualTo("1|[]");
    }

    /** 编辑界面：输入坐标后保存写回配置；重置回 (6, 6)；取消不保存。 */
    @DisplayName("状态栏位置编辑界面：输入坐标后保存写回配置，重置回 (6, 6)，取消不保存")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void hudEditorSaveResetCancel(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player host, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        Players.host(ctx, host);
        ClientSide.awaitIdle(ctx);
        open(ctx, infos(1));
        List<String> result = ClientSide.call(ctx, () -> {
            EnderPocketClientConfig.setPosition(6, 6);
            Screen parent = screen();
            Minecraft.getInstance().setScreen(new EnderPocketHudEditorScreen(parent));
            setInputs(screen(), "40", "30");
            press(screen(), "gui.maidspell.ender_pocket.hud_editor.save");
            String saved = EnderPocketClientConfig.HUD_X.get() + "," + EnderPocketClientConfig.HUD_Y.get() + "|" + (screen() == parent);
            Minecraft.getInstance().setScreen(new EnderPocketHudEditorScreen(parent));
            setInputs(screen(), "90", "80");
            press(screen(), "gui.maidspell.ender_pocket.hud_editor.cancel");
            String cancelled = EnderPocketClientConfig.HUD_X.get() + "," + EnderPocketClientConfig.HUD_Y.get();
            Minecraft.getInstance().setScreen(new EnderPocketHudEditorScreen(parent));
            press(screen(), "gui.maidspell.ender_pocket.hud_editor.reset");
            press(screen(), "gui.maidspell.ender_pocket.hud_editor.save");
            String reset = EnderPocketClientConfig.HUD_X.get() + "," + EnderPocketClientConfig.HUD_Y.get();
            return List.of(saved, cancelled, reset);
        });
        ctx.check(result).as("保存 / 取消 / 重置后保存 的位置").isEqualTo(List.of("40,30|true", "40,30", "6,6"));
        ctx.check(configText().contains("hudX = 6") && configText().contains("hudY = 6")).as("配置文件写回位置").isTrue();
    }

    /** 输入越界的坐标：预览不出屏，也不压到底部工具栏上。 */
    @DisplayName("状态栏位置编辑界面输入越界坐标时，预览不出屏也不压到底部工具栏")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void hudEditorClampsPosition(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player host, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        Players.host(ctx, host);
        ClientSide.awaitIdle(ctx);
        open(ctx, infos(1));
        List<Integer> result = ClientSide.call(ctx, () -> {
            Screen parent = screen();
            Minecraft.getInstance().setScreen(new EnderPocketHudEditorScreen(parent));
            Screen editor = screen();
            setInputs(editor, "99999", "99999");
            press(editor, "gui.maidspell.ender_pocket.hud_editor.save");
            int x = EnderPocketClientConfig.HUD_X.get();
            int y = EnderPocketClientConfig.HUD_Y.get();
            int maxX = editor.width - EnderPocketHudOverlay.ROW_WIDTH;
            int previewBottom = editor.height - 30 - 4;
            return List.of(x, maxX, y + EnderPocketHudOverlay.getEditorPreviewHeight(previewBottom, y), previewBottom);
        });
        ctx.check(result.get(0)).as("x 被限制在屏幕内（最大 " + result.get(1) + "）").isEqualTo(result.get(1));
        ctx.check(result.get(2)).as("预览底边不压到工具栏（" + result.get(3) + "）").isAtMost(result.get(3));
    }

    private static void setInputs(Screen editor, String x, String y) {
        List<EditBox> boxes = ClientSide.widgets(editor).stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();
        boxes.get(0).setValue(x);
        boxes.get(1).setValue(y);
    }

    private static void press(Screen screen, String key) {
        AbstractWidget button = ClientSide.widget(screen, key);
        if (button == null) {
            throw new IllegalStateException("界面里没有按钮 " + key);
        }
        button.onClick(button.getX() + 1, button.getY() + 1);
    }

    /** 自己戴末影腰包的女仆出现在状态栏数据里，生命变化约 1 秒内刷新。 */
    @DisplayName("自己戴末影腰包的女仆出现在状态栏数据里，生命变化约 1 秒内刷新")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void hudDataRefreshes(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player host, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer player = Players.host(ctx, host);
        ClientSide.awaitIdle(ctx);
        EntityMaid maid = ctx.get(() -> Players.ownedMaid(ctx, player, 3, 0, 0, NS + "ender_pocket"));
        ClientSide.run(ctx, () -> {
            boolean enabled = EnderPocketClientConfig.HUD_ENABLED.get();
            ctx.cleanup(() -> Minecraft.getInstance().execute(() -> EnderPocketClientConfig.setHudEnabled(enabled)));
            EnderPocketClientConfig.setHudEnabled(true);
        });
        float max = ctx.get(maid::getMaxHealth);
        awaitHudHealth(ctx, maid, max, 100);
        ctx.run(() -> Actors.setMaidHealth(maid, max / 2));
        awaitHudHealth(ctx, maid, max / 2, 40);
        ctx.record("note", "状态栏生命已刷新");
    }

    /**
     * 玩家留在主世界，女仆在下界：远程打开背包时客户端只拿到代理女仆，界面能开；关掉界面后客户端世界里没有这只女仆，
     * 不会多出一个站着不动的幽灵女仆。
     */
    @DisplayName("远程打开另一维度女仆的背包能开界面，关掉后客户端世界里不残留幽灵女仆")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void remoteOpenLeavesNoGhost(Game game, @Plot.In("minecraft:the_nether") @Plot.Size(32) @Plot.Centered Plot plot,
                                 Player host, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer player = Stage.serverPlayer(host);
        ctx.run(() -> Players.keepState(ctx, player));
        ClientSide.awaitIdle(ctx);
        if (ctx.get(() -> player.level() == ctx.level())) {
            throw ctx.fail("真实玩家不该在下界：本测试要求女仆与玩家不在同一维度");
        }
        EntityMaid maid = ctx.get(() -> {
            EntityMaid owned = Owners.maid(ctx, player, 0, 0, 0);
            Actors.equipBauble(owned, NS + "ender_pocket");
            return owned;
        });
        UUID id = maid.getUUID();
        ctx.check(ctx.get(() -> EnderPocketService.openMaidInventory(player, id))).as("服务端开始远程打开").isTrue();
        ctx.await(100, "客户端打开女仆背包界面", () -> Minecraft.getInstance()
                .submit(() -> Minecraft.getInstance().screen instanceof AbstractMaidContainerGui<?>).join());
        ctx.check(ctx.get(() -> EnderPocketService.isRemoteSessionActive(player, maid))).as("远程会话建立").isTrue();
        ClientSide.run(ctx, () -> Minecraft.getInstance().player.closeContainer());
        ctx.await(40, "服务端关掉女仆背包", () -> player.containerMenu == player.inventoryMenu);
        ClientSide.awaitIdle(ctx);
        String ghosts = ClientSide.call(ctx, () -> {
            List<String> found = new ArrayList<>();
            Minecraft.getInstance().level.entitiesForRendering().forEach(entity -> {
                if (entity.getUUID().equals(id)) {
                    found.add(entity.getClass().getSimpleName() + "@" + entity.blockPosition().toShortString());
                }
            });
            return String.join(", ", found);
        });
        ctx.check(ghosts).as("关掉界面后客户端世界里的这只女仆").isEmpty();
    }

    /** 在女仆背包界面的末影腰包侧栏点另一只女仆的名字，界面切到那只女仆的背包。 */
    @DisplayName("女仆背包界面的末影腰包侧栏点另一只女仆的名字，切到她的背包")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void sidebarSwitchesMaid(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player host, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ServerPlayer player = Players.host(ctx, host);
        ClientSide.awaitIdle(ctx);
        List<EntityMaid> maids = ctx.get(() -> List.of(sidebarMaid(ctx, player, "TlmsSideA", 2), sidebarMaid(ctx, player, "TlmsSideB", -2)));
        ctx.check(ctx.get(() -> EnderPocketService.openMaidInventory(player, maids.get(0).getUUID()))).as("打开 A 的背包").isTrue();
        ctx.await(100, "客户端打开 A 的背包且侧栏列出 B", () -> Minecraft.getInstance().submit(() ->
                openMaid() == maids.get(0).getId() && sidebarButton("TlmsSideB") != null).join());
        String clicked = ClientSide.call(ctx, () -> {
            AbstractWidget button = sidebarButton("TlmsSideB");
            if (button == null) {
                return "侧栏里没有 B";
            }
            button.onClick(button.getX() + 1, button.getY() + 1);
            return "ok";
        });
        ctx.check(clicked).as("点侧栏里 B 的名字").isEqualTo("ok");
        ctx.await(100, "界面切到 B 的背包", () -> Minecraft.getInstance().submit(() -> openMaid() == maids.get(1).getId()).join());
        ctx.check(ctx.get(() -> player.containerMenu instanceof com.github.tartaricacid.touhoulittlemaid.inventory.container.AbstractMaidContainer menu
                && menu.getMaid() == maids.get(1))).as("服务端打开的是 B 的背包").isTrue();
    }

    /**
     * 女仆很多时状态栏只画放得下的行，最后一行的底边在左下角血量、盔甲栏之上；按 F1 隐藏界面、打开了别的界面、
     * 关掉状态栏时一行都不画。直接调状态栏的 render，用只记录底框的画布数行。
     */
    @DisplayName("状态栏只画放得下的行且不压到血量、盔甲栏，隐藏界面、打开界面或关掉状态栏时不画")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void hudFitsAndHides(Game game, @Plot.Size(32) @Plot.Centered Plot plot, Player host, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        Players.host(ctx, host);
        ClientSide.awaitIdle(ctx);
        open(ctx, infos(1));
        List<String> result = ClientSide.call(ctx, () -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(null);
            EnderPocketClientConfig.setHudEnabled(true);
            EnderPocketClientConfig.setPosition(6, 6);
            boolean hideGui = mc.options.hideGui;
            try {
                List<String> out = new ArrayList<>();
                EnderPocketHudOverlay.update(infos(40));
                List<int[]> rows = hudRows();
                int limit = mc.getWindow().getGuiScaledHeight() - Math.max(mc.gui.leftHeight, mc.gui.rightHeight) - 6;
                int bottom = rows.stream().mapToInt(r -> r[1]).max().orElse(-1);
                out.add("rows=" + rows.size());
                out.add("bottomOk=" + (!rows.isEmpty() && bottom <= limit));
                mc.options.hideGui = true;
                out.add("hideGui=" + hudRows().size());
                mc.options.hideGui = hideGui;
                mc.setScreen(new EnderPocketScreen(infos(1)));
                out.add("screen=" + hudRows().size());
                mc.setScreen(null);
                EnderPocketClientConfig.setHudEnabled(false);
                out.add("disabled=" + hudRows().size());
                ctx.record("limit", limit + " bottom=" + bottom + " guiHeight=" + mc.getWindow().getGuiScaledHeight());
                return out;
            } finally {
                mc.options.hideGui = hideGui;
            }
        });
        ctx.record("result", result);
        int rows = result.isEmpty() ? 0 : Integer.parseInt(result.getFirst().substring("rows=".length()));
        ctx.check(rows).as("40 只女仆时画出的行数").isBetween(1, 39);
        ctx.check(result.subList(1, result.size())).as("底边位置与三种不画的情形")
                .isEqualTo(List.of("bottomOk=true", "hideGui=0", "screen=0", "disabled=0"));
    }

    /** 调一次状态栏的 render，返回画出的每一行底框的 [上边, 下边]（客户端线程）。 */
    private static List<int[]> hudRows() {
        Minecraft mc = Minecraft.getInstance();
        List<int[]> rows = new ArrayList<>();
        GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource()) {
            @Override
            public void fill(int minX, int minY, int maxX, int maxY, int color) {
                if (color == 0xB8101018 && maxX - minX == EnderPocketHudOverlay.ROW_WIDTH) {
                    rows.add(new int[]{minY, maxY});
                }
                super.fill(minX, minY, maxX, maxY, color);
            }
        };
        new EnderPocketHudOverlay().render(graphics, DeltaTracker.ZERO);
        mc.renderBuffers().bufferSource().endBatch();
        return rows;
    }

    /** 主人名下、戴末影腰包、带名字的女仆。 */
    private static EntityMaid sidebarMaid(Stage ctx, ServerPlayer player, String name, int dx) {
        EntityMaid maid = Owners.maid(ctx, player, dx, 0, 2);
        maid.setCustomName(Component.literal(name));
        Actors.equipBauble(maid, NS + "ender_pocket");
        return maid;
    }

    /** 当前打开的女仆界面对应的实体 id；不是女仆界面时为 -1（客户端线程）。 */
    private static int openMaid() {
        return Minecraft.getInstance().screen instanceof AbstractMaidContainerGui<?> gui ? gui.getMenu().getMaid().getId() : -1;
    }

    private static AbstractWidget sidebarButton(String name) {
        Screen screen = screen();
        return screen == null ? null : ClientSide.widget(screen, w -> w.getMessage().getString().equals(name));
    }

    private static void awaitHudHealth(Stage ctx, EntityMaid maid, float health, int within) {
        float[] seen = {Float.NaN};
        try {
            ctx.await(within, "状态栏数据里的女仆生命变为 " + health, () -> {
                CompletableFuture<Float> future = Minecraft.getInstance().submit(() -> {
                    List<EnderPocketService.EnderPocketMaidInfo> infos = Reflect.field(null, EnderPocketHudOverlay.class, "maidInfos");
                    return infos.stream().filter(info -> info.maidUUID.equals(maid.getUUID())).map(info -> info.health).findFirst().orElse(Float.NaN);
                });
                seen[0] = future.join();
                return Math.abs(seen[0] - health) < 1e-3;
            });
        } finally {
            ctx.record("hudHealthLastSeen", seen[0]);
        }
    }
}
