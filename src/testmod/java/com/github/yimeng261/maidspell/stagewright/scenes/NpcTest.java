package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.crafting.AltarRecipe;
import com.github.yimeng261.maidspell.Config;
import com.github.yimeng261.maidspell.api.IAuthoritativeHealth;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Owners;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.datafixers.util.Either;
import io.redspace.ironsspellbooks.entity.mobs.wizards.IMerchantWizard;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Locks;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.parallel.ResourceLock;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PinkPetalsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 观星术士、精灵圣卫、星之魔女这几个 NPC：交易互斥与放开、观星术士的报价与中立、星之魔女的属性/床/命令/保底/权威最大生命，
 * 以及星辉花簇和改用星云核心的梦云水晶祭坛配方。
 */
@DisplayName("NPC：观星术士、精灵守卫与星之魔女")
@ResourceLock(Locks.TICK_RATE)
@Scene("tlms")
class NpcTest {
    private static final String ASTRO = NS + "astro_mancer";
    private static final String TEMPLAR = NS + "elf_templar";
    private static final String WITCH = NS + "stellar_witch";
    private static final ResourceLocation MAX_HEALTH_PROBE = ResourceLocation.parse(NS + "test_max_health_probe");

    // ---- 交易 ----

    @DisplayName("观星术士正与一名玩家交易时，第二名玩家打不开交易")
    @Test
    void astroMancerSecondPlayerCannotOpen(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter).fastForward();
        ctx.run(() -> secondPlayer(ctx, ASTRO));
    }

    @DisplayName("观星术士的交易玩家走远后释放，另一名玩家可以打开交易")
    @Test
    void astroMancerReleasedWhenTraderLeaves(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        traderLeaves(Stage.flat(game, plot, reporter).fastForward(), ASTRO);
    }

    @DisplayName("观星术士消失后，玩家的交易界面失效")
    @Test
    void astroMancerMenuInvalidWhenNpcGone(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter).fastForward();
        ctx.run(() -> npcGone(ctx, ASTRO));
    }

    @DisplayName("精灵守卫正与一名玩家交易时，第二名玩家打不开交易")
    @Test
    void elfTemplarSecondPlayerCannotOpen(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter).fastForward();
        ctx.run(() -> secondPlayer(ctx, TEMPLAR));
    }

    @DisplayName("精灵守卫的交易玩家走远后释放，另一名玩家可以打开交易")
    @Test
    void elfTemplarReleasedWhenTraderLeaves(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        traderLeaves(Stage.flat(game, plot, reporter).fastForward(), TEMPLAR);
    }

    @DisplayName("精灵守卫消失后，玩家的交易界面失效")
    @Test
    void elfTemplarMenuInvalidWhenNpcGone(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter).fastForward();
        ctx.run(() -> npcGone(ctx, TEMPLAR));
    }

    @DisplayName("星之魔女正与一名玩家交易时，第二名玩家拿绿宝石也打不开交易")
    @Test
    void witchSecondPlayerCannotOpen(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter).fastForward();
        ctx.run(() -> secondPlayer(ctx, WITCH));
    }

    @DisplayName("星之魔女的交易玩家走远后释放，另一名玩家可以打开交易")
    @Test
    void witchReleasedWhenTraderLeaves(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        traderLeaves(Stage.flat(game, plot, reporter).fastForward(), WITCH);
    }

    @DisplayName("星之魔女被授权移除后，玩家的交易界面失效")
    @Test
    void witchMenuInvalidWhenNpcGone(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter).fastForward();
        ctx.run(() -> npcGone(ctx, WITCH));
    }

    @DisplayName("普通村民的交易不受互斥影响，A 关掉后 B 可继续交易")
    @Test
    void villagerUnaffected(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter).fastForward();
        ctx.run(() -> villager(ctx));
    }

    // ---- 观星术士 ----

    @DisplayName("观星术士收购墨水、奥术精华和食物，出售星锚珍珠等，价格不随需求上涨")
    @Test
    void offers(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter).fastForward();
        ctx.run(() -> astroOffers(ctx));
    }

    @DisplayName("观星术士补货后记下补货时刻，半天内交易用光再右键不补，过了半天才补，星陨石始终不刷回")
    @Test
    void astroRestockOncePerHalfDay(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            Mob npc = npc(ctx, ASTRO);
            IMerchantWizard wizard = (IMerchantWizard) npc;
            ServerPlayer player = Owners.visitor(ctx, "TlmsRestocker", 2, 0, 0);
            long now = ctx.level().getGameTime();
            sellOut(wizard);
            wizard.setLastRestockGameTime(now - 2401);
            ctx.check(reopen(player, npc)).as("第一次右键打开交易").isTrue();
            ctx.check(soldOut(wizard, false)).as("第一次右键后仍卖光的普通交易数").isEqualTo(0);
            ctx.check(soldOut(wizard, true)).as("第一次右键后仍卖光的星陨石").isEqualTo(1);
            CompoundTag saved = new CompoundTag();
            npc.addAdditionalSaveData(saved);
            ctx.check(saved.getLong("LastRestock")).as("存档里的 LastRestock").isEqualTo(now);
            sellOut(wizard);
            ctx.check(reopen(player, npc)).as("半天内再右键打开交易").isTrue();
            ctx.check(soldOut(wizard, false) > 0).as("半天内再右键后普通交易仍卖光").isTrue();
            wizard.setLastRestockGameTime(now - 12001);
            ctx.check(reopen(player, npc)).as("过了半天右键打开交易").isTrue();
            ctx.check(soldOut(wizard, false)).as("过了半天右键后仍卖光的普通交易数").isEqualTo(0);
            ctx.check(soldOut(wizard, true)).as("过了半天右键后仍卖光的星陨石").isEqualTo(1);
            player.closeContainer();
        });
    }

    @DisplayName("观星术士保持中立，被玩家打了才以玩家为目标，两只之间不互相为敌")
    @Test
    void neutralAndRetaliates(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter).fastForward();
        Brawl brawl = ctx.get(() -> {
            Mob a = Actors.spawn(ctx, ASTRO, 0, 0, 0, false);
            Mob b = Actors.spawn(ctx, ASTRO, 0, 0, 3, false);
            ServerPlayer player = Owners.visitor(ctx, "TlmsStargazer", 4, 0, 0);
            return new Brawl(a, b, player);
        });
        boolean[] targeted = {false};
        ctx.watch(60, () -> targeted[0] |= brawl.a().getTarget() == brawl.player() || brawl.b().getTarget() == brawl.player());
        ctx.run(() -> {
            ctx.check(targeted[0]).as("观星术士主动以路过的玩家为目标").isFalse();
            brawl.a().hurt(brawl.a().damageSources().playerAttack(brawl.player()), 1);
            brawl.b().hurt(brawl.b().damageSources().indirectMagic(brawl.a(), brawl.a()), 1);
        });
        ctx.await(40, "被打的观星术士以玩家为目标", () -> brawl.a().getTarget() == brawl.player());
        ctx.ticks(20);
        ctx.run(() -> ctx.check(brawl.a().getTarget() == brawl.b() || brawl.b().getTarget() == brawl.a())
                .as("两只观星术士互相为目标").isFalse());
    }

    @DisplayName("观星术士被击杀后只按战利品表掉落")
    @Test
    void dropsFromTableOnly(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter).fastForward();
        ctx.run(() -> LootTest.checkKillDrops(ctx, ASTRO, NS + "entities/astro_mancer"));
    }

    // ---- 星之魔女 ----

    @DisplayName("星之魔女戴的法帽不加护甲和韧性")
    @Test
    void hatAddsNoArmor(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter).fastForward();
        ctx.run(() -> witchHat(ctx));
    }

    @DisplayName("星之魔女附近的床对生存玩家不安全且重生点不变，创造模式不受影响")
    @Test
    void bedUnsafeNearby(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter).fastForward();
        ctx.run(() -> witchBed(ctx));
    }

    @DisplayName("星之魔女被击杀或虚空伤害后既不死亡也不被移除")
    @Test
    void survivesKill(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter).fastForward();
        Mob witch = ctx.get(() -> {
            Mob spawned = npc(ctx, WITCH);
            spawned.kill();
            spawned.hurt(spawned.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
            return spawned;
        });
        ctx.ticks(20);
        ctx.run(() -> {
            ctx.check(witch.isRemoved()).as("被移除").isFalse();
            ctx.check(witch.isDeadOrDying()).as("死亡").isFalse();
        });
    }

    @DisplayName("星之魔女生命周期命令仅 OP 可用，子命令齐全")
    @Test
    void lifecycleCommandNeedsOp(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter).fastForward();
        ctx.run(() -> witchCommand(ctx));
    }

    /**
     * 星之魔女的 getMaxHealth 返回权威最大生命（配置值），外部 max_health 修饰符改不动它；
     * 每 tick 反推补偿量，属性值也回到权威值（不超过原版 1024 上限时）。
     */
    @DisplayName("星之魔女最大生命为权威配置值，外部修饰符改不动，属性值每 tick 回到权威值")
    @Test
    void maxHealthIsAuthoritative(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter).fastForward();
        float expected = (float) Config.winefoxMaxHealth;
        Mob witch = ctx.get(() -> {
            Mob spawned = npc(ctx, WITCH);
            ctx.record("configuredMaxHealth", expected);
            ctx.check(spawned.getMaxHealth()).as("生成时 getMaxHealth").isCloseTo(expected, 1e-3);
            ctx.check(((IAuthoritativeHealth) spawned).maidspell$authoritativeMaxHealth()).as("权威最大生命").isCloseTo(expected, 1e-3);
            return spawned;
        });
        AttributeInstance attribute = ctx.get(() -> witch.getAttribute(Attributes.MAX_HEALTH));
        ctx.ticks(2);
        List<AttributeModifier> probes = List.of(
                new AttributeModifier(MAX_HEALTH_PROBE, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
                new AttributeModifier(MAX_HEALTH_PROBE, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                new AttributeModifier(MAX_HEALTH_PROBE, 100, AttributeModifier.Operation.ADD_VALUE));
        for (AttributeModifier probe : probes) {
            String label = probe.operation().getSerializedName();
            ctx.run(() -> {
                attribute.removeModifier(MAX_HEALTH_PROBE);
                attribute.addTransientModifier(probe);
                ctx.check(witch.getMaxHealth()).as(label + " 修饰刚加上时 getMaxHealth").isCloseTo(expected, 1e-3);
            });
            ctx.ticks(2);
            ctx.run(() -> {
                ctx.check(witch.getMaxHealth()).as(label + " 修饰两 tick 后 getMaxHealth").isCloseTo(expected, 1e-3);
                if (expected <= 1024) {
                    ctx.check(attribute.getValue()).as(label + " 修饰两 tick 后属性值").isCloseTo(expected, 1e-3);
                }
                ctx.check(witch.getHealth() <= witch.getMaxHealth() + 1e-3).as(label + " 修饰后当前生命不超过最大生命").isTrue();
            });
        }
        ctx.run(() -> attribute.removeModifier(MAX_HEALTH_PROBE));
        ctx.ticks(2);
        ctx.run(() -> {
            ctx.check(witch.getMaxHealth()).as("去掉修饰后 getMaxHealth").isCloseTo(expected, 1e-3);
            if (expected <= 1024) {
                ctx.check(attribute.getValue()).as("去掉修饰后属性值").isCloseTo(expected, 1e-3);
            }
        });
    }

    /** /summon、刷怪蛋与直接创建生成的星之魔女都是满血：当前生命等于权威最大生命。 */
    @DisplayName("星之魔女经命令、刷怪蛋或直接创建生成时都是满血")
    @Test
    void spawnsAtFullHealth(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter).fastForward();
        Mob[] witches = ctx.get(() -> {
            Mob created = npc(ctx, WITCH);
            String tag = "tlms_spawn_health";
            CommandSourceStack source = ctx.server().createCommandSourceStack().withLevel(ctx.level())
                    .withPosition(Vec3.atCenterOf(ctx.origin()));
            ctx.server().getCommands().performPrefixedCommand(source, "summon " + WITCH + " ~3 ~ ~ {Tags:[\"" + tag + "\"]}");
            List<Mob> summoned = ctx.level().getEntitiesOfClass(Mob.class, new AABB(ctx.rel(0, 0, 0)).inflate(8),
                    mob -> mob.getTags().contains(tag));
            summoned.forEach(mob -> ctx.cleanup(() -> Actors.cleanupEntity(mob)));
            if (summoned.size() != 1) {
                throw ctx.fail("/summon 生成的星之魔女有 " + summoned.size() + " 只");
            }
            // 与 SpawnEggItem#useOn 相同的生成调用
            ItemStack egg = Actors.stack(NS + "stellar_witch_spawn_egg");
            Entity hatched = ((SpawnEggItem) egg.getItem()).getType(egg).spawn(ctx.level(), egg, null, ctx.rel(-3, 0, 0),
                    MobSpawnType.SPAWN_EGG, true, false);
            if (!(hatched instanceof Mob eggWitch)) {
                throw ctx.fail("刷怪蛋没有生成星之魔女");
            }
            ctx.cleanup(() -> Actors.cleanupEntity(eggWitch));
            return new Mob[]{summoned.getFirst(), eggWitch, created};
        });
        ctx.ticks(2);
        ctx.run(() -> {
            checkFullHealth(ctx, "summon", witches[0]);
            checkFullHealth(ctx, "spawnEgg", witches[1]);
            checkFullHealth(ctx, "created", witches[2]);
        });
    }

    /** getMaxHealth 的注入只改星之魔女，其他生物仍按属性值计算。 */
    @DisplayName("权威最大生命的注入只作用于星之魔女，其他生物仍按属性值计算")
    @Test
    void otherMaxHealthUnaffected(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter).fastForward();
        ctx.run(() -> otherMaxHealth(ctx));
    }

    /** 最多 4 朵，破坏时按朵数掉落，属于花。 */
    @DisplayName("星荧花簇最多 4 朵，破坏时按朵数掉落，属于花")
    @Test
    void starGlowFlowerClusterBehavesLikePetals(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter).fastForward();
        ctx.run(() -> flowerCluster(ctx));
    }

    @DisplayName("梦云水晶的祭坛配方改用星云核心")
    @Test
    void altarRecipesUseNebulaCore(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter).fastForward();
        ctx.run(() -> altarRecipes(ctx));
    }

    // ---- 实现 ----

    private record Brawl(Mob a, Mob b, ServerPlayer player) {
    }

    private record Trade(Mob npc, ServerPlayer a, ServerPlayer b, AbstractContainerMenu opened) {
    }

    private static Mob npc(Stage ctx, String type) {
        Mob mob = Actors.spawn(ctx, type, 0, 0, 0, true);
        mob.setPersistenceRequired();
        return mob;
    }

    private static void sellOut(IMerchantWizard wizard) {
        wizard.getOffers().forEach(MerchantOffer::setToOutOfStock);
    }

    /** 卖光的交易条数：meteorite 为 true 时只数星陨石，否则只数其余交易。 */
    private static long soldOut(IMerchantWizard wizard, boolean meteorite) {
        return wizard.getOffers().stream()
                .filter(offer -> id(offer.getResult()).equals(NS + "star_meteorite") == meteorite)
                .filter(MerchantOffer::isOutOfStock)
                .count();
    }

    /** 关掉当前界面后重新右键打开交易，返回是否打开。 */
    private static boolean reopen(ServerPlayer player, Mob npc) {
        player.closeContainer();
        player.interactOn(npc, InteractionHand.MAIN_HAND);
        return trading(player);
    }

    private static boolean trading(Player player) {
        return player.containerMenu instanceof MerchantMenu;
    }

    /** 来交易的测试玩家；星之魔女要手持绿宝石右击才开交易。 */
    private static ServerPlayer trader(Stage ctx, String type, String name, int dx) {
        ServerPlayer player = Owners.visitor(ctx, name, dx, 0, 0);
        if (type.equals(WITCH)) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD));
        }
        return player;
    }

    private static void secondPlayer(Stage ctx, String type) {
        Mob npc = npc(ctx, type);
        ServerPlayer a = trader(ctx, type, "TlmsTraderA", 2);
        ServerPlayer b = trader(ctx, type, "TlmsTraderB", -2);
        a.interactOn(npc, InteractionHand.MAIN_HAND);
        ctx.check(trading(a)).as("A 打开交易").isTrue();
        b.interactOn(npc, InteractionHand.MAIN_HAND);
        ctx.check(trading(b)).as("B 在 A 交易时打开交易").isFalse();
        ctx.check(((Merchant) npc).getTradingPlayer() == a).as("交易对象仍是 A").isTrue();
        ctx.check(a.containerMenu.stillValid(a)).as("A 的交易界面仍有效").isTrue();
    }

    private static void traderLeaves(Stage ctx, String type) {
        Trade trade = ctx.get(() -> {
            Mob npc = npc(ctx, type);
            ServerPlayer a = trader(ctx, type, "TlmsTraderA", 2);
            ServerPlayer b = trader(ctx, type, "TlmsTraderB", -2);
            a.interactOn(npc, InteractionHand.MAIN_HAND);
            ctx.check(trading(a)).as("A 打开交易").isTrue();
            AbstractContainerMenu opened = a.containerMenu;
            a.moveTo(a.getX() + 12, a.getY(), a.getZ(), a.getYRot(), a.getXRot());
            return new Trade(npc, a, b, opened);
        });
        ctx.await(20, "A 走远后交易被释放", () -> ((Merchant) trade.npc()).getTradingPlayer() == null);
        ctx.run(() -> {
            ctx.check(trade.opened().stillValid(trade.a())).as("走远后 A 的交易界面仍有效").isFalse();
            trade.b().interactOn(trade.npc(), InteractionHand.MAIN_HAND);
            ctx.check(trading(trade.b())).as("A 走远后 B 打开交易").isTrue();
        });
    }

    private static void npcGone(Stage ctx, String type) {
        Mob npc = npc(ctx, type);
        ServerPlayer a = trader(ctx, type, "TlmsTraderA", 2);
        a.interactOn(npc, InteractionHand.MAIN_HAND);
        ctx.check(trading(a)).as("A 打开交易").isTrue();
        if (type.equals(WITCH)) {
            // 星之魔女拒绝普通移除，走授权清除
            Actors.cleanupEntity(npc);
        } else {
            npc.discard();
        }
        ctx.check(a.containerMenu.stillValid(a)).as("NPC 消失后 A 的交易界面仍有效").isFalse();
    }

    private static void villager(Stage ctx) {
        Villager villager = Actors.spawn(ctx, "minecraft:villager", 0, 0, 0, true);
        villager.setVillagerData(new VillagerData(VillagerType.PLAINS, VillagerProfession.LIBRARIAN, 2));
        ServerPlayer a = Owners.visitor(ctx, "TlmsTraderA", 2, 0, 0);
        ServerPlayer b = Owners.visitor(ctx, "TlmsTraderB", -2, 0, 0);
        a.interactOn(villager, InteractionHand.MAIN_HAND);
        ctx.check(trading(a)).as("A 与村民交易").isTrue();
        a.closeContainer();
        b.interactOn(villager, InteractionHand.MAIN_HAND);
        ctx.check(trading(b)).as("A 关掉后 B 与村民交易").isTrue();
    }

    private static String id(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** 收购墨水、奥术精华和食物；出售星锚珍珠（16 绿宝石，每次补货 1 件）等；星陨石只卖一次；价格不随需求上涨。 */
    private static void astroOffers(Stage ctx) {
        Mob npc = npc(ctx, ASTRO);
        Merchant merchant = (Merchant) npc;
        List<MerchantOffer> offers = merchant.getOffers();
        Set<String> bought = new TreeSet<>();
        Map<String, String> sold = new TreeMap<>();
        for (MerchantOffer offer : offers) {
            if (id(offer.getResult()).equals("minecraft:emerald")) {
                bought.add(id(offer.getBaseCostA()));
            } else {
                sold.put(id(offer.getResult()), offer.getBaseCostA().getCount() + "×" + id(offer.getBaseCostA())
                        + (offer.getCostB().isEmpty() ? "" : " + " + offer.getCostB().getCount() + "×" + id(offer.getCostB()))
                        + " 限 " + offer.getMaxUses());
            }
        }
        ctx.record("sold", sold);
        for (String item : List.of("irons_spellbooks:common_ink", "irons_spellbooks:uncommon_ink", "irons_spellbooks:rare_ink",
                "irons_spellbooks:arcane_essence", "minecraft:bread", "minecraft:cooked_beef", "minecraft:cooked_porkchop",
                "minecraft:cooked_chicken", "minecraft:baked_potato", "minecraft:apple")) {
            ctx.check(bought.contains(item)).as("收购 " + item).isTrue();
        }
        ctx.check(sold.get(NS + "staranchor_pearl")).as("星锚珍珠报价").isEqualTo("16×minecraft:emerald 限 1");
        ctx.check(sold.get(NS + "star_meteorite")).as("星陨石报价").isEqualTo("64×minecraft:emerald + 1×minecraft:nether_star 限 1");
        for (String item : List.of("irons_spellbooks:epic_ink", "irons_spellbooks:ender_rune", "irons_spellbooks:mithril_scrap",
                "minecraft:ender_pearl", "irons_spellbooks:scroll")) {
            ctx.check(sold.containsKey(item)).as("出售 " + item).isTrue();
        }
        MerchantOffer first = offers.getFirst();
        int price = first.getCostA().getCount();
        for (int i = 0; i < 4; i++) {
            first.increaseUses();
        }
        first.updateDemand();
        ctx.check(first.getCostA().getCount()).as("卖了 4 次后同一条的价格").isEqualTo(price);
    }

    /** 她戴的法帽不加护甲和韧性。 */
    private static void witchHat(Stage ctx) {
        Mob witch = npc(ctx, WITCH);
        ctx.record("head", id(witch.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)));
        ctx.check(witch.getAttributeValue(Attributes.ARMOR)).as("护甲（与基础值对照）").isCloseTo(witch.getAttributeBaseValue(Attributes.ARMOR), 1e-6);
        ctx.check(witch.getAttributeValue(Attributes.ARMOR_TOUGHNESS)).as("韧性（与基础值对照）")
                .isCloseTo(witch.getAttributeBaseValue(Attributes.ARMOR_TOUGHNESS), 1e-6);
    }

    /** 她附近 8 格内的床：生存玩家白天晚上都提示不安全、重生点不变；创造模式不受她影响。 */
    private static void witchBed(Stage ctx) {
        npc(ctx, WITCH);
        ServerLevel level = ctx.level();
        BlockPos foot = ctx.rel(3, 0, 0);
        BlockPos head = foot.relative(Direction.EAST);
        level.setBlock(foot, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST).setValue(BedBlock.PART, BedPart.FOOT), Block.UPDATE_ALL);
        level.setBlock(head, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST).setValue(BedBlock.PART, BedPart.HEAD), Block.UPDATE_ALL);
        ServerPlayer player = Owners.visitor(ctx, "TlmsSleeper", 3, 0, 1);
        long time = level.getDayTime();
        ctx.cleanup(() -> level.setDayTime(time));
        Map<String, String> results = new TreeMap<>();
        for (long day : List.of(6000L, 18000L)) {
            level.setDayTime(day);
            Either<Player.BedSleepingProblem, Unit> result = player.startSleepInBed(head);
            results.put("生存 " + day, result.left().map(Enum::name).orElse("睡下"));
            player.stopSleeping();
        }
        ctx.check(player.getRespawnPosition()).as("重生点").isNull();
        player.setGameMode(GameType.CREATIVE);
        level.setDayTime(18000L);
        Either<Player.BedSleepingProblem, Unit> creative = player.startSleepInBed(head);
        results.put("创造 18000", creative.left().map(Enum::name).orElse("睡下"));
        player.stopSleeping();
        player.setGameMode(GameType.SURVIVAL);
        ctx.check(results).as("上床结果").isEqualTo(Map.of("生存 6000", "NOT_SAFE", "生存 18000", "NOT_SAFE", "创造 18000", "睡下"));
    }

    private static void checkFullHealth(Stage ctx, String label, Mob witch) {
        IAuthoritativeHealth health = (IAuthoritativeHealth) witch;
        ctx.record(label + ".health", health.maidspell$authoritativeHealth());
        ctx.record(label + ".maxHealth", health.maidspell$authoritativeMaxHealth());
        ctx.check(health.maidspell$authoritativeHealth()).as(label + " 生成的当前生命")
                .isCloseTo(health.maidspell$authoritativeMaxHealth(), 1e-3);
    }

    private static void otherMaxHealth(Stage ctx) {
        Mob zombie = Actors.spawn(ctx, "minecraft:zombie", 0, 0, 0, true);
        AttributeInstance attribute = zombie.getAttribute(Attributes.MAX_HEALTH);
        attribute.addTransientModifier(new AttributeModifier(MAX_HEALTH_PROBE, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        ctx.check(zombie.getMaxHealth()).as("僵尸 getMaxHealth 等于属性值").isCloseTo(attribute.getValue(), 1e-3);
        ctx.check(zombie.getMaxHealth()).as("僵尸加 50% 后的最大生命").isCloseTo(30, 1e-3);
    }

    private static void witchCommand(Stage ctx) {
        CommandNode<CommandSourceStack> root = ctx.server().getCommands().getDispatcher().getRoot().getChild("maidspell_winefox");
        if (root == null) {
            throw ctx.fail("没有 maidspell_winefox 命令");
        }
        CommandSourceStack op = ctx.server().createCommandSourceStack();
        ctx.check(root.canUse(op.withPermission(2))).as("OP 能用").isTrue();
        ctx.check(root.canUse(op.withPermission(0))).as("非 OP 能用").isFalse();
        Set<String> actions = new TreeSet<>();
        root.getChildren().forEach(target -> target.getChildren().forEach(action -> actions.add(action.getName())));
        ctx.check(actions).as("子命令").isEqualTo(new TreeSet<>(List.of("status", "return", "repair", "reward_confirm", "reward_reissue", "destroy", "palm_black_hole")));
    }

    private static void flowerCluster(Stage ctx) {
        ServerLevel level = ctx.level();
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(NS + "star_glow_flower_cluster"));
        BlockState full = block.defaultBlockState().setValue(PinkPetalsBlock.AMOUNT, 4);
        ctx.check(PinkPetalsBlock.AMOUNT.getPossibleValues().stream().max(Integer::compare).orElse(0)).as("最多朵数").isEqualTo(4);
        ctx.check(full.is(BlockTags.FLOWERS)).as("方块属于 #minecraft:flowers").isTrue();
        ctx.check(new ItemStack(block).is(ItemTags.FLOWERS)).as("物品属于 #minecraft:flowers").isTrue();
        BlockPos pos = ctx.rel(0, 0, 0);
        level.setBlock(pos, full, Block.UPDATE_ALL);
        level.destroyBlock(pos, true);
        int dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).stream()
                .filter(e -> e.getItem().is(block.asItem())).mapToInt(e -> e.getItem().getCount()).sum();
        level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).forEach(Entity::discard);
        ctx.check(dropped).as("4 朵破坏后的掉落").isEqualTo(4);
    }

    private static void altarRecipes(Stage ctx) {
        ItemStack core = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(NS + "nebula_core")));
        for (String id : List.of("altar/dream_cat_crystal_accessories", "altar/dream_cat_crystal_crossover")) {
            RecipeHolder<?> recipe = ctx.server().getRecipeManager().byKey(ResourceLocation.parse(NS + id)).orElse(null);
            if (recipe == null) {
                ctx.check(false).as("祭坛配方 " + id + " 存在").isTrue();
                continue;
            }
            ctx.check(recipe.value() instanceof AltarRecipe).as(id + " 是车万女仆祭坛配方").isTrue();
            ctx.check(recipe.value().getIngredients().stream().anyMatch(ing -> ing.test(core))).as(id + " 用星云核心").isTrue();
            if (id.endsWith("crossover") && ModList.get().isLoaded("goety")) {
                ItemStack blood = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("goety:unholy_blood")));
                ItemStack pureBlood = blood.copy();
                CompoundTag pure = new CompoundTag();
                pure.putBoolean("Pure", true);
                pureBlood.set(DataComponents.CUSTOM_DATA, CustomData.of(pure));
                ctx.check(recipe.value().getIngredients().stream().anyMatch(ing -> ing.test(pureBlood))).as(id + " 收纯净邪血").isTrue();
                ctx.check(recipe.value().getIngredients().stream().anyMatch(ing -> ing.test(blood))).as(id + " 不收普通邪血").isFalse();
            }
        }
    }
}
