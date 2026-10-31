package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.github.yimeng261.maidspell.api.IMaidSpellData;
import com.github.yimeng261.maidspell.api.ISpellBookProvider;
import com.github.yimeng261.maidspell.spell.manager.SpellBookManager;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Maids;
import com.github.yimeng261.maidspell.stagewright.support.Owners;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import com.github.yimeng261.maidspell.stagewright.support.Reflect;
import com.github.yimeng261.maidspell.stagewright.support.SpellFixtures;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import com.github.yimeng261.maidspell.task.SpellCombatFarTask;
import com.github.yimeng261.maidspell.task.SpellCombatMeleeTask;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 女仆法术数据的生命周期（专用服，主人为模拟玩家）：每个联动模组各验证一遍
 * 收进魂符再放出、区块卸载后 5 秒内回来与超过 5 秒、换维度、死亡、被复活饰品救下时法术书与冷却的去留；
 * 铁魔法真实施法中收进魂符、打开背包时停止施法；法术战斗任务的女仆被设成 NoAI 后自动恢复。
 * 专用服拓扑不装 UsefulMagic，它的那一组在 {@link SpellLifecycleIntegratedTest} 里跑。
 */
/** 各测试共用一个主人玩家，按玩家名上锁串行。 */
@Scene("tlms")
@ResourceLock("stagewright.player:TlmsSpellOwner")
class SpellLifecycleTest {
    /** 各联动模组的法术提供者 ID。 */
    static final List<String> MODS = List.of("irons_spellbooks", "ars_nouveau", "psi", "goety", "usefulmagic", "slashblade");
    /** 离开世界后法术数据保留的 tick 数。 */
    static final int RETENTION = 100;
    private static final String PROBE_COOLDOWN = NS + "lifecycle_probe";
    private static final String CONE_OF_COLD_BOOK = "irons_spellbooks:gold_spell_book[irons_spellbooks:spell_container="
            + "{maxSpells:1,mustEquip:false,spellWheel:true,data:[{id:\"irons_spellbooks:cone_of_cold\",index:0,level:5,locked:false}]}]";

    @Test
    void ironsSpellbooksSlabRoundTrip(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        slabRoundTrip(ctx, "irons_spellbooks");
    }

    @Test
    void ironsSpellbooksUnloadBriefly(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        unload(ctx, "irons_spellbooks", RETENTION / 2);
    }

    @Test
    void ironsSpellbooksUnloadLong(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        unload(ctx, "irons_spellbooks", RETENTION + 20);
    }

    @Test
    void ironsSpellbooksDimensionChange(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        dimensionChange(ctx, "irons_spellbooks");
    }

    @Test
    void ironsSpellbooksDeath(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        death(ctx, "irons_spellbooks", false);
    }

    @Test
    void ironsSpellbooksRevived(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        death(ctx, "irons_spellbooks", true);
    }

    @Test
    void arsNouveauSlabRoundTrip(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        slabRoundTrip(ctx, "ars_nouveau");
    }

    @Test
    void arsNouveauUnloadBriefly(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        unload(ctx, "ars_nouveau", RETENTION / 2);
    }

    @Test
    void arsNouveauUnloadLong(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        unload(ctx, "ars_nouveau", RETENTION + 20);
    }

    @Test
    void arsNouveauDimensionChange(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        dimensionChange(ctx, "ars_nouveau");
    }

    @Test
    void arsNouveauDeath(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        death(ctx, "ars_nouveau", false);
    }

    @Test
    void arsNouveauRevived(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        death(ctx, "ars_nouveau", true);
    }

    @Test
    void psiSlabRoundTrip(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        slabRoundTrip(ctx, "psi");
    }

    @Test
    void psiUnloadBriefly(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        unload(ctx, "psi", RETENTION / 2);
    }

    @Test
    void psiUnloadLong(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        unload(ctx, "psi", RETENTION + 20);
    }

    @Test
    void psiDimensionChange(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        dimensionChange(ctx, "psi");
    }

    @Test
    void psiDeath(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        death(ctx, "psi", false);
    }

    @Test
    void psiRevived(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        death(ctx, "psi", true);
    }

    @Test
    void goetySlabRoundTrip(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        slabRoundTrip(ctx, "goety");
    }

    @Test
    void goetyUnloadBriefly(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        unload(ctx, "goety", RETENTION / 2);
    }

    @Test
    void goetyUnloadLong(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        unload(ctx, "goety", RETENTION + 20);
    }

    @Test
    void goetyDimensionChange(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        dimensionChange(ctx, "goety");
    }

    @Test
    void goetyDeath(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        death(ctx, "goety", false);
    }

    @Test
    void goetyRevived(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        death(ctx, "goety", true);
    }

    @Test
    void slashbladeSlabRoundTrip(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        slabRoundTrip(ctx, "slashblade");
    }

    @Test
    void slashbladeUnloadBriefly(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        unload(ctx, "slashblade", RETENTION / 2);
    }

    @Test
    void slashbladeUnloadLong(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        unload(ctx, "slashblade", RETENTION + 20);
    }

    @Test
    void slashbladeDimensionChange(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        dimensionChange(ctx, "slashblade");
    }

    @Test
    void slashbladeDeath(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        death(ctx, "slashblade", false);
    }

    @Test
    void slashbladeRevived(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        death(ctx, "slashblade", true);
    }

    /** 施法中收进魂符再放出：不崩溃、不在施法、法术书仍在，可以再次施法。 */
    @Test
    void slabWhileCasting(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ISpellBookProvider<?, ?> iss = provider(ctx, "irons_spellbooks");
        ServerPlayer owner = ctx.get(() -> Owners.visitor(ctx, "TlmsSpellOwner", 0, 0, -3));
        Mob target = ctx.get(() -> Actors.spawn(ctx, "minecraft:zombie", 0, 0, 5, true));
        EntityMaid maid = ctx.get(() -> castingMaid(ctx, owner, target));
        UUID id = maid.getUUID();
        ctx.await(40, "女仆开始施法", () -> iss.isCasting(maid));
        ItemStack slab = ctx.get(() -> Players.storeInSlab(owner, maid));
        Players.releaseSlab(ctx, owner, slab, ctx.rel(0, -1, 2));
        ctx.ticks(2);
        Entity released = ctx.get(() -> Maids.find(ctx.level(), id));
        ctx.check(released instanceof EntityMaid).as("魂符放出了同一只女仆").isTrue();
        if (!(released instanceof EntityMaid again)) {
            return;
        }
        ctx.cleanup(() -> Actors.cleanupEntity(again));
        ctx.run(() -> {
            ctx.check(iss.isCasting(again)).as("放出后仍在施法").isFalse();
            IMaidSpellData data = data(iss, id);
            ctx.check(data != null && !data.getSpellBooks().isEmpty()).as("放出后法术书仍登记").isTrue();
        });
        castAfterCooldown(ctx, iss, again, target, "放出后能再次施法");
    }

    /** 打开女仆背包时停止施法；关闭后能恢复施法。 */
    @Test
    void backpackStopsCasting(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ISpellBookProvider<?, ?> iss = provider(ctx, "irons_spellbooks");
        ServerPlayer owner = ctx.get(() -> Owners.visitor(ctx, "TlmsSpellOwner", 0, 0, -3));
        Mob target = ctx.get(() -> Actors.spawn(ctx, "minecraft:zombie", 0, 0, 5, true));
        EntityMaid maid = ctx.get(() -> castingMaid(ctx, owner, target));
        ctx.await(40, "女仆开始施法", () -> iss.isCasting(maid));
        ctx.run(() -> maid.openMaidGui(owner));
        ctx.await(10, "打开背包后停止施法", () -> !iss.isCasting(maid));
        ctx.run(owner::closeContainer);
        ctx.ticks(2);
        castAfterCooldown(ctx, iss, maid, target, "关闭背包后能再次施法");
    }

    /** 法术近战、法术远程任务的女仆被设成 NoAI 后约 1 秒内自动恢复；其他任务的女仆保持 NoAI。 */
    @Test
    void noAiRestoredForSpellTasks(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        EntityMaid melee = ctx.get(() -> Actors.sittingMaid(ctx, 2, 0, 0));
        EntityMaid far = ctx.get(() -> Actors.sittingMaid(ctx, -2, 0, 0));
        EntityMaid idle = ctx.get(() -> Actors.sittingMaid(ctx, 0, 0, 2));
        ctx.run(() -> {
            melee.setTask(TaskManager.findTask(SpellCombatMeleeTask.UID).orElseThrow());
            far.setTask(TaskManager.findTask(SpellCombatFarTask.UID).orElseThrow());
            for (EntityMaid maid : List.of(melee, far, idle)) {
                maid.setNoAi(true);
            }
        });
        ctx.ticks(45);
        ctx.run(() -> {
            ctx.check(melee.isNoAi()).as("法术近战女仆 45 tick 后仍 NoAI").isFalse();
            ctx.check(far.isNoAi()).as("法术远程女仆 45 tick 后仍 NoAI").isFalse();
            ctx.check(idle.isNoAi()).as("其他任务的女仆 45 tick 后仍 NoAI").isTrue();
        });
    }

    // ---- 工具 ----

    static ISpellBookProvider<?, ?> provider(Stage ctx, String mod) {
        ISpellBookProvider<?, ?> provider = SpellBookManager.getProvider(mod);
        if (provider == null) {
            throw ctx.skip(mod + " 的法术提供者未注册");
        }
        return provider;
    }

    static IMaidSpellData data(ISpellBookProvider<?, ?> provider, UUID maid) {
        return Reflect.call(provider, ISpellBookProvider.class, "getExistingData", new Class<?>[]{UUID.class}, maid);
    }

    /** 主人为模拟玩家、主手拿着该模组法术书的女仆（测试线程）。 */
    private static Setup setup(Stage ctx, String mod) {
        ISpellBookProvider<?, ?> provider = provider(ctx, mod);
        return ctx.get(() -> {
            ServerPlayer owner = Owners.visitor(ctx, "TlmsSpellOwner", 0, 0, -3);
            EntityMaid maid = Actors.sittingMaid(ctx, 0, 0, 0);
            ctx.check(Players.tame(owner, maid)).as("驯服女仆").isTrue();
            SpellFixtures.equip(ctx, mod, provider, owner, maid);
            return new Setup(provider, owner, maid, maid.getUUID());
        });
    }

    private record Setup(ISpellBookProvider<?, ?> provider, ServerPlayer owner, EntityMaid maid, UUID id) {
        /** 等法术书登记进数据后，记一条 400 tick 的探测冷却（测试线程）。 */
        void ready(Stage ctx) {
            ctx.await(20, "法术书登记进法术数据", () -> {
                IMaidSpellData data = data(provider, id);
                return data != null && !data.getSpellBooks().isEmpty();
            });
            ctx.run(() -> data(provider, id).setSpellCooldown(PROBE_COOLDOWN, 400, maid));
        }

        /** 法术书仍登记着、探测冷却仍在、不在施法（服务端线程）。 */
        void expectKept(Stage ctx, String label) {
            expectKept(ctx, label, true);
        }

        void expectKept(Stage ctx, String label, boolean keepCooldown) {
            IMaidSpellData data = data(provider, id);
            ctx.check(data).as(label + " 的法术数据").isNotNull();
            if (data == null) {
                return;
            }
            ctx.check(data.getSpellBooks().size()).as(label + " 登记的法术书数").isEqualTo(1);
            if (keepCooldown) {
                ctx.check(data.getSpellCooldown(PROBE_COOLDOWN)).as(label + " 的探测冷却").isGreaterThan(0);
            } else {
                ctx.check(data.getSpellCooldown(PROBE_COOLDOWN)).as(label + " 水晶清零探测冷却").isEqualTo(0);
            }
            ctx.check(data.isCasting()).as(label + " 仍在施法").isFalse();
        }
    }

    // ---- 各模组 ----

    /** 收进魂符再立即放出：法术书、冷却都在，不在施法；放出后能实际施法。 */
    static void slabRoundTrip(Stage ctx, String mod) {
        Setup s = setup(ctx, mod);
        s.ready(ctx);
        ItemStack slab = ctx.get(() -> {
            ItemStack stored = Players.storeInSlab(s.owner(), s.maid());
            ctx.check(s.maid().isRemoved()).as("女仆已收进魂符").isTrue();
            return stored;
        });
        Players.releaseSlab(ctx, s.owner(), slab, ctx.rel(0, -1, 2));
        ctx.ticks(2);
        Entity released = ctx.get(() -> Maids.find(ctx.level(), s.id()));
        ctx.check(released instanceof EntityMaid).as("魂符放出了同一只女仆").isTrue();
        if (!(released instanceof EntityMaid maid)) {
            return;
        }
        ctx.cleanup(() -> Actors.cleanupEntity(maid));
        ctx.run(() -> s.expectKept(ctx, "放出后"));
        expectCast(ctx, s.provider(), maid);
    }

    /** 随区块卸载离开世界：away tick 内回来数据仍在；超过保留时间后数据被删除。 */
    static void unload(Stage ctx, String mod, int away) {
        Setup s = setup(ctx, mod);
        s.ready(ctx);
        Maids.reload(ctx, s.maid(), away);
        ctx.run(() -> {
            if (away < RETENTION) {
                s.expectKept(ctx, "卸载 " + away + " tick 后回来");
            } else {
                IMaidSpellData data = data(s.provider(), s.id());
                ctx.check(data == null || data.getSpellCooldown(PROBE_COOLDOWN) == 0)
                        .as("卸载超过保留时间后回来，旧冷却已随数据删除").isTrue();
                ctx.check(data != null && data.getSpellBooks().size() == 1).as("回来后按背包重新登记法术书").isTrue();
            }
        });
    }

    /** 跟随主人换维度（传送到下界）：法术书与冷却随女仆过去，能开始施法。 */
    static void dimensionChange(Stage ctx, String mod) {
        Setup s = setup(ctx, mod);
        s.ready(ctx);
        Entity moved = ctx.get(() -> {
            ServerLevel nether = ctx.server().getLevel(Level.NETHER);
            boolean forced = nether.getForcedChunks().contains(ChunkPos.asLong(0, 0));
            if (!forced) {
                nether.setChunkForced(0, 0, true);
                ctx.cleanup(() -> nether.setChunkForced(0, 0, false));
            }
            nether.getChunk(0, 0);
            return Maids.copyToDimension(ctx, s.maid(), nether, new Vec3(0.5, 64, 0.5));
        });
        ctx.check(moved instanceof EntityMaid).as("换维度后的女仆").isTrue();
        if (!(moved instanceof EntityMaid maid)) {
            return;
        }
        ctx.ticks(2);
        ctx.run(() -> s.expectKept(ctx, "换维度后"));
        expectCast(ctx, s.provider(), maid);
    }

    /** 真正死亡时法术数据立即清掉；梦云水晶救下时法术书保留、冷却清零。 */
    static void death(Stage ctx, String mod, boolean revived) {
        Setup s = setup(ctx, mod);
        if (revived) {
            ctx.run(() -> Actors.equipBauble(s.maid(), NS + "dream_cat_crystal"));
        }
        s.ready(ctx);
        ctx.run(() -> {
            Maids.kill(s.maid());
            ctx.check(s.maid().isAlive()).as("致死后女仆存活").isEqualTo(revived);
            if (revived) {
                ctx.check(s.maid().getHealth()).as("水晶复活恢复满血").isCloseTo(s.maid().getMaxHealth(), 1e-3f);
                s.expectKept(ctx, "被救下后", false);
            } else {
                ctx.check(data(s.provider(), s.id())).as("死亡后的法术数据").isNull();
            }
        });
        if (revived) {
            expectCast(ctx, s.provider(), s.maid());
        }
    }

    /** 目标与施法者处于同一维度，启动施法、发射弹体或造成伤害才算恢复成功。 */
    private static void expectCast(Stage ctx, ISpellBookProvider<?, ?> provider, EntityMaid maid) {
        record Probe(Mob target, float health, Set<Integer> existingProjectiles) { }
        Probe probe = ctx.get(() -> {
            Mob target = EntityType.HUSK.create(maid.level());
            if (target == null) throw new IllegalStateException("无法创建施法目标");
            target.setNoAi(true);
            target.setPersistenceRequired();
            target.setNoGravity(true);
            target.noPhysics = true;
            target.moveTo(maid.getX(), maid.getY(), maid.getZ() + 2, 0, 0);
            maid.level().addFreshEntity(target);
            ctx.cleanup(() -> Actors.cleanupEntity(target));
            Set<Integer> existing = maid.level().getEntities(maid, maid.getBoundingBox().inflate(8),
                    entity -> entity instanceof Projectile).stream().map(Entity::getId).collect(Collectors.toSet());
            return new Probe(target, target.getHealth(), existing);
        });
        ctx.await(40, "生命周期恢复后实际施法、发射弹体或命中目标", () -> {
            provider.setTarget(maid, probe.target());
            provider.castSpell(maid);
            List<Entity> firedProjectiles = maid.level().getEntities(maid, maid.getBoundingBox().inflate(8),
                    entity -> entity instanceof Projectile projectile
                            && projectile.getOwner() == maid && !probe.existingProjectiles().contains(entity.getId()));
            firedProjectiles.forEach(projectile -> ctx.cleanup(() -> Actors.cleanupEntity(projectile)));
            boolean fired = !firedProjectiles.isEmpty();
            ctx.record("castTargetHealth", probe.target().getHealth());
            ctx.record("castMaidAlive", maid.isAlive());
            ctx.record("castProjectileCreated", fired);
            return provider.isCasting(maid) || probe.target().getHealth() < probe.health() || fired;
        });
        ctx.record("note", "生命周期恢复后已实际施法、发射弹体或命中目标");
    }

    // ---- 铁魔法真实施法 ----

    /** 开始施放持续法术（冰锥术）的铁魔法女仆（服务端线程）。 */
    private static EntityMaid castingMaid(Stage ctx, ServerPlayer owner, Mob target) {
        EntityMaid maid = Actors.sittingMaid(ctx, 0, 0, 0);
        ctx.check(Players.tame(owner, maid)).as("驯服女仆").isTrue();
        ItemStack book = Actors.parse(ctx, CONE_OF_COLD_BOOK);
        maid.setItemSlot(EquipmentSlot.OFFHAND, book);
        SpellBookManager manager = SpellBookManager.getOrCreateManager(maid);
        manager.addSpellItem(maid, book);
        SpellBookManager.getProvider("irons_spellbooks").setTarget(maid, target);
        manager.castSpell(maid);
        return maid;
    }

    /** 中断施法会进入正常冷却，冷却自然结束后重新发起施法（测试线程）。 */
    private static void castAfterCooldown(Stage ctx, ISpellBookProvider<?, ?> iss,
                                          EntityMaid maid, Mob target, String note) {
        IMaidSpellData data = ctx.get(() -> data(iss, maid.getUUID()));
        String spell = "irons_spellbooks:cone_of_cold";
        ctx.record("interruptedSpellCooldown", ctx.get(() -> data.getSpellCooldown(spell)));
        ctx.await(1400, "中断施法的冷却结束", () -> data.getSpellCooldown(spell) == 0);
        ctx.run(() -> {
            iss.setTarget(maid, target);
            SpellBookManager.getOrCreateManager(maid).castSpell(maid);
        });
        ctx.await(40, note, () -> iss.isCasting(maid));
        ctx.record("note", note);
    }
}
