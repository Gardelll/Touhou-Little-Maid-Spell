package com.github.yimeng261.maidspell.stagewright.client;

import com.github.yimeng261.maidspell.stagewright.support.Lang;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import net.magicterra.stagewright.test.Client;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Player;
import net.magicterra.stagewright.test.Requires;
import net.magicterra.stagewright.test.Scene;
import net.magicterra.worlddriver.api.methods.client.ClientContracts;
import net.magicterra.worlddriver.api.methods.interact.InteractBlockingApi;
import net.magicterra.worlddriver.api.methods.interact.InteractContracts;
import net.magicterra.worlddriver.api.methods.screen.ScreenBlockingApi;
import net.magicterra.worlddriver.api.methods.screen.ScreenContracts;
import net.magicterra.worlddriver.api.methods.shared.CommonTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 按玩家的操作方式驱动客户端界面：
 * 右键法术白名单打开法术收集界面；按末影腰包按键打开女仆列表，点选后打开女仆背包。
 */
@DisplayName("客户端界面操作流程")
@Scene("tlms.integrated")
class ScreenFlowClientTest {
    private static final String NS = "touhou_little_maid_spell:";
    /** 等界面出现的上限（刻）。 */
    private static final int WAIT_TICKS = 300;
    private static final String POCKET_MAID = "TlmsPocketMaid";

    /** 手持法术白名单右键：打开"法术白名单 - 法术收集"界面，27 个卷轴格加玩家背包 36 格。 */
    @DisplayName("手持法术白名单右键，打开 27 个卷轴格加玩家背包的法术收集界面")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void blueNoteOpensSpellCollection(Game game, Client client, @Plot.Size(32) @Plot.Centered Plot plot, Player player,
                                      TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        Players.host(ctx, player);
        ScreenBlockingApi screen = client.api(ScreenBlockingApi::of);
        try {
            player.give(BuiltInRegistries.ITEM.get(ResourceLocation.parse(NS + "blue_note")), 1);
            awaitScreen(ctx, screen, "客户端加载完区块", type -> type.isEmpty());
            // 给的物品要等同步到客户端，客户端的手持检查才看得到
            ctx.ticks(5);
            hold(game, player, NS + "blue_note");
            ctx.ticks(5);
            use(game, player);
            awaitScreen(ctx, screen, "法术白名单界面", type -> type.contains("SpellWhiteList"));
            String title = screen.screenInfo(new CommonTypes.NoParams()).title().orElse("");
            Set<String> titles = Set.of(Lang.read("zh_cn").get("container.maidspell.blue_note"),
                    Lang.read("en_us").get("container.maidspell.blue_note"));
            assertTrue(titles.contains(title), "界面标题：" + title);
            var container = assertInstanceOf(ScreenContracts.MenuContainer.class,
                    screen.container(new ScreenContracts.ContainerParams(Optional.empty(), Optional.empty())));
            assertEquals(27 + 36, container.slots().orElse(List.of()).size(), "界面格数");
        } finally {
            closeScreen(screen);
        }
    }

    /** 按末影腰包按键：列出装备末影腰包的自有女仆，点选后打开她的背包界面。 */
    @DisplayName("按末影腰包按键列出戴腰包的自有女仆，点选后打开她的背包界面")
    @Test
    @Requires(Requires.Capability.CLIENT)
    void enderPocketKeyOpensMaidBackpack(Game game, Client client, @Plot.Size(32) @Plot.Centered Plot plot, Player player,
                                         TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        Players.host(ctx, player);
        ScreenBlockingApi screen = client.api(ScreenBlockingApi::of);
        UUID uuid = player.onServer(p -> p.getUUID());
        Vec3 at = ctx.get(() -> Vec3.atBottomCenterOf(ctx.rel(2, 0, 0)));
        ctx.run(() -> command(ctx, String.format("summon touhou_little_maid:maid %.1f %.1f %.1f {NoAI:1b,PersistenceRequired:1b,"
                        + "CustomName:'\"%s\"',Owner:%s,MaidBaubleInventory:{Items:[{Slot:0,id:\"%sender_pocket\",count:1}]}}",
                at.x, at.y, at.z, POCKET_MAID, intArray(uuid), NS)));
        ctx.cleanup(() -> command(ctx, "kill @e[type=touhou_little_maid:maid,name=" + POCKET_MAID + "]"));
        try {
            // 列表由服务端在按键时生成，打开后不会刷新；女仆刚召唤时可能还没登记，列表里没有她就关掉重按
            Integer entryRef = null;
            for (int tick = 0; tick < WAIT_TICKS && entryRef == null; tick++) {
                if (screenType(screen).contains("EnderPocket")) {
                    entryRef = findWidget(screen, POCKET_MAID);
                    if (entryRef != null) {
                        break;
                    }
                    closeScreen(screen);
                }
                screen.keybind(new ScreenContracts.KeybindParams(Optional.of("key.touhou_little_maid_spell.open_ender_pocket"),
                        ScreenContracts.KeyAction.CLICK, false, Optional.empty()));
                ctx.ticks(1);
            }
            assertTrue(entryRef != null, "末影腰包列表里出现女仆 " + POCKET_MAID);
            var reply = screen.click(new ScreenContracts.ClickParams(Optional.of(entryRef), Optional.empty(), Optional.empty(),
                    0, ScreenContracts.InputRoute.SCREEN, false, Optional.empty()));
            assertInstanceOf(ClientContracts.Clicked.class, reply, "点选女仆条目");
            // 打开的是 TLM 的女仆背包界面（按背包类型不同，类名如 EmptyBackpackContainerScreen、MaidMainContainerScreen）
            awaitScreen(ctx, screen, "女仆背包界面", type -> type.endsWith("ContainerScreen") && !type.contains("EnderPocket"));
        } finally {
            closeScreen(screen);
        }
    }

    // ---- 工具 ----

    private static void command(Stage ctx, String command) {
        ctx.server().getCommands().performPrefixedCommand(
                ctx.server().createCommandSourceStack().withPosition(Vec3.atCenterOf(ctx.origin())), command);
    }

    private static void hold(Game game, Player player, String item) {
        var reply = game.api(InteractBlockingApi::of).holdItem(new InteractContracts.HoldParams("self", item));
        assertFalse(reply instanceof net.magicterra.worlddriver.api.methods.shared.Responses.Refused, "手持 " + item + "：" + reply);
    }

    private static void use(Game game, Player player) {
        var reply = game.api(InteractBlockingApi::of).useItem(new InteractContracts.UseParams("self", Optional.empty(),
                Optional.empty(), Optional.empty(), InteractContracts.Hand.MAIN, true, false));
        assertFalse(reply instanceof net.magicterra.worlddriver.api.methods.shared.Responses.Refused, "右键使用：" + reply);
    }

    private static String screenType(ScreenBlockingApi screen) {
        return screen.screenInfo(new CommonTypes.NoParams()).type().orElse("");
    }

    private static void awaitScreen(Stage ctx, ScreenBlockingApi screen, String what, Predicate<String> accept) {
        for (int tick = 0; tick < WAIT_TICKS; tick++) {
            if (accept.test(screenType(screen))) {
                return;
            }
            ctx.ticks(1);
        }
        throw ctx.fail("等待" + what + "超时，当前界面：" + screenType(screen));
    }

    private static void closeScreen(ScreenBlockingApi screen) {
        screen.closeScreen(new ScreenContracts.CloseParams(Optional.empty()));
    }

    /** 控件树里按显示文字找控件，返回它的引用（没有时为 null）。 */
    private static Integer findWidget(ScreenBlockingApi screen, String message) {
        for (var node : screen.screenTree(new CommonTypes.NoParams()).nodes()) {
            if (node.message().filter(text -> text.contains(message)).isPresent()) {
                return node.ref();
            }
        }
        return null;
    }

    private static String intArray(UUID uuid) {
        long most = uuid.getMostSignificantBits();
        long least = uuid.getLeastSignificantBits();
        return "[I;" + (int) (most >> 32) + "," + (int) most + "," + (int) (least >> 32) + "," + (int) least + "]";
    }
}
