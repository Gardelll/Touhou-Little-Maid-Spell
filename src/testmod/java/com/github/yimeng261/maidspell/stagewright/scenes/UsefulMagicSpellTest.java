package com.github.yimeng261.maidspell.stagewright.scenes;

import cn.coostack.usefulmagic.extend.EntityExtendKt;
import cn.coostack.usefulmagic.items.weapon.magic.MagicItem;
import cn.coostack.usefulmagic.items.weapon.wands.MagicWand;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.api.IMaidSpellData;
import com.github.yimeng261.maidspell.api.ISpellBookProvider;
import com.github.yimeng261.maidspell.spell.manager.SpellBookManager;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Checks;
import com.github.yimeng261.maidspell.stagewright.support.Owners;
import com.github.yimeng261.maidspell.stagewright.support.Players;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Requires;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * UsefulMagic 的全部法术球（集成拓扑，专用服不装 UsefulMagic）：每颗球各一个动态测试，
 * 女仆带一个只装这颗球的法术袋和一把能装载它的法杖（首选下界合金法杖），打一个满血的无 AI 目标；
 * 验证能开始施法、施法能自己结束、冷却写入、提供者装进法杖的球被卸回、女仆蓄力态被清掉，
 * 并记录目标掉血量与新生成实体数。其余攻击法术还要求目标掉血或场上出现新实体。
 * 另有一个测试核对这里列的法术与注册表里的法术球完全一致，上游新增法术时会在这里提醒补测试。
 */
@Scene("tlms.integrated")
@Requires(mods = "usefulmagic")
@ResourceLock("stagewright.player:TlmsSpellOwner")
class UsefulMagicSpellTest {
    private static final String MOD = "usefulmagic";
    /** 首选的法杖；装不下的高阶球再换别的法杖。 */
    private static final String PREFERRED_WAND = MOD + ":netherite_wand";
    /** 注册表里全部的法术球（MagicItem）。 */
    private static final List<String> SPELLS = List.of(
            "anti_entity_domain_magic", "barrage_magic", "bloom_magic", "digging_magic", "explosion_magic",
            "golden_magic", "health_magic", "laser_magic", "light_beam_magic", "lightning_magic",
            "meteorite_magic", "push_magic", "starry_magic", "sword_formation_magic", "sword_qi_magic");
    /**
     * 不要求有可观察效果的法术：生命法术单独断言回血，风吹推人、盛开催熟、挖掘破坏方块不一定伤人；
     * 剑气和剑阵是弹幕，当前集成环境里没观察到命中，只记录掉血量。
     */
    private static final Set<String> NO_DAMAGE = Set.of("health_magic", "push_magic", "bloom_magic", "digging_magic",
            "sword_formation_magic", "sword_qi_magic");
    /** 开始施法的等待上限。 */
    private static final int START_WITHIN = 60;
    /** 施法自己结束的等待上限（蓄力最长的法术也远小于它）。 */
    private static final int FINISH_WITHIN = 600;
    /** 施法结束后再等一会儿，让陨石、剑气这类弹幕和动画类法术落地。 */
    private static final int SETTLE = 200;

    /** 本类列的法术与注册表里的法术球完全一致。 */
    @Test
    void allSpellsListed(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> {
            Set<String> registered = BuiltInRegistries.ITEM.entrySet().stream()
                    .filter(entry -> entry.getKey().location().getNamespace().equals(MOD) && entry.getValue() instanceof MagicItem)
                    .map(entry -> entry.getKey().location().getPath())
                    .collect(Collectors.toCollection(TreeSet::new));
            ctx.record("registeredSpells", registered.size());
            Checks.sameSet(ctx, "UsefulMagic 法术球", SPELLS, registered);
        });
    }

    /** 逐颗法术球施法，每颗球一个动态测试；各项共用同一个主人，每项结束后清掉自己的女仆与目标。 */
    @TestFactory
    List<DynamicTest> cast(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ISpellBookProvider<?, ?> provider = SpellBookManager.getProvider(MOD);
        if (provider == null) {
            throw ctx.fail(MOD + " 的法术提供者未注册");
        }
        ServerPlayer owner = ctx.get(() -> Owners.visitor(ctx, "TlmsSpellOwner", 0, 0, -3));
        return SPELLS.stream()
                .map(spell -> DynamicTest.dynamicTest(spell, () -> cast(ctx, provider, owner, spell)))
                .toList();
    }

    private record Rig(EntityMaid maid, Mob target, ItemStack wand, float maidStart, float targetStart,
                       double targetStartX, double targetStartZ, Set<Integer> before) { }

    private static void cast(Stage ctx, ISpellBookProvider<?, ?> provider, ServerPlayer owner, String spell) {
        String spellId = MOD + ":" + spell;
        ItemStack ball = Actors.stack(spellId);
        ItemStack wand = ctx.get(() -> loadableWand(ball));
        ctx.check(wand != null).as("有法杖能装载 " + spell).isTrue();
        if (wand == null) {
            return;
        }
        ctx.record(spell + ".wand", BuiltInRegistries.ITEM.getKey(wand.getItem()).getPath());
        Rig rig = ctx.get(() -> {
            EntityMaid maid = Actors.sittingMaid(ctx, 0, 0, 0);
            ctx.check(Players.tame(owner, maid)).as(spell + " 驯服女仆").isTrue();
            ItemStack bag = Actors.stack(MOD + ":spell_bag");
            bag.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(ball)));
            maid.setItemSlot(EquipmentSlot.MAINHAND, bag);
            maid.setItemSlot(EquipmentSlot.OFFHAND, wand);

            float startHealth = maid.getMaxHealth();
            if (spell.equals("health_magic")) {
                // 生命法术只在女仆或主人受伤时才会被选中
                startHealth = maid.getMaxHealth() / 2;
                Actors.setMaidHealth(maid, startHealth);
            }

            Mob target = EntityType.HUSK.create(ctx.level());
            if (target == null) {
                throw new IllegalStateException("无法创建施法目标");
            }
            target.setNoAi(true);
            target.setPersistenceRequired();
            target.setNoGravity(true);
            // 满血 1000，法术不会把目标一下打死而让施法半路被判无效
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
            target.setHealth(1000);
            target.moveTo(maid.getX(), maid.getY(), maid.getZ() + 4, 180, 0);
            ctx.level().addFreshEntity(target);
            ctx.cleanup(() -> Actors.cleanupEntity(target));
            return new Rig(maid, target, wand, startHealth, target.getHealth(), target.getX(), target.getZ(), nearbyIds(maid));
        });
        EntityMaid maid = rig.maid();
        Mob target = rig.target();
        try {
            ctx.await(20, spell + " 的法术数据登记法术书", () -> {
                IMaidSpellData data = SpellLifecycleTest.data(provider, maid.getUUID());
                return data != null && !data.getSpellBooks().isEmpty();
            });
            ctx.await(START_WITHIN, spell + " 开始施法", () -> {
                provider.setTarget(maid, target);
                provider.castSpell(maid);
                return provider.isCasting(maid);
            });
            int startedAt = ctx.tick();
            ctx.await(FINISH_WITHIN, spell + " 施法结束", () -> !provider.isCasting(maid));
            ctx.record(spell + ".castTicks", ctx.tick() - startedAt);
            ctx.run(() -> {
                IMaidSpellData data = SpellLifecycleTest.data(provider, maid.getUUID());
                ctx.check(data != null && data.getSpellCooldown(spellId) > 0).as("施法结束后 " + spell + " 进入冷却").isTrue();
                ItemStack loaded = ((MagicWand) rig.wand().getItem()).getLoadedMagic(rig.wand());
                ctx.check(loaded.isEmpty()).as(spell + " 施法结束后法杖里的球已卸回").isTrue();
                ctx.check(EntityExtendKt.getCharging(maid)).as(spell + " 施法结束后女仆蓄力态已清除").isFalse();
            });
            ctx.ticks(SETTLE);
            ctx.run(() -> settled(ctx, spell, rig));
        } finally {
            ctx.run(() -> {
                Actors.cleanupEntity(maid);
                Actors.cleanupEntity(target);
            });
        }
    }

    private static void settled(Stage ctx, String spell, Rig rig) {
        EntityMaid maid = rig.maid();
        Mob target = rig.target();
        List<Entity> spawned = ctx.level().getEntities(maid, searchBox(maid),
                entity -> !rig.before().contains(entity.getId()) && entity != target && entity != maid && !entity.isRemoved());
        spawned.forEach(entity -> ctx.cleanup(() -> Actors.cleanupEntity(entity)));
        float damage = rig.targetStart() - target.getHealth();
        ctx.record(spell + ".targetDamage", damage);
        ctx.record(spell + ".spawnedEntities", spawned.size());
        String spawnedTypes = spawned.stream().map(e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString())
                .distinct().sorted().collect(Collectors.joining(","));
        ctx.record(spell + ".spawnedTypes", spawnedTypes.isEmpty() ? "(无)" : spawnedTypes);
        ctx.record(spell + ".targetMoved", Math.hypot(target.getX() - rig.targetStartX(), target.getZ() - rig.targetStartZ()));
        ctx.record(spell + ".maidHealthGain", maid.getHealth() - rig.maidStart());
        ctx.check(maid.isAlive()).as(spell + " 施法后女仆存活").isTrue();
        if (spell.equals("health_magic")) {
            ctx.check(maid.getHealth()).as("生命法术让女仆回血").isGreaterThan(rig.maidStart());
        } else if (!NO_DAMAGE.contains(spell)) {
            ctx.check(damage > 0 || !spawned.isEmpty()).as(spell + " 让目标掉血或场上出现新实体").isTrue();
        }
    }

    /** 首选下界合金法杖，装不下就换注册表里别的能装这颗球的法杖。 */
    private static ItemStack loadableWand(ItemStack ball) {
        List<ItemStack> wands = BuiltInRegistries.ITEM.entrySet().stream()
                .filter(entry -> entry.getKey().location().getNamespace().equals(MOD) && entry.getValue() instanceof MagicWand)
                .map(entry -> new ItemStack(entry.getValue()))
                .sorted(Comparator.comparing((ItemStack stack) -> !BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(PREFERRED_WAND))
                        .thenComparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()))
                .toList();
        return wands.stream().filter(wand -> ((MagicWand) wand.getItem()).canLoadMagic(wand, ball)).findFirst().orElse(null);
    }

    private static Set<Integer> nearbyIds(LivingEntity center) {
        return center.level().getEntities(center, searchBox(center), entity -> true).stream()
                .map(Entity::getId).collect(Collectors.toSet());
    }

    private static AABB searchBox(LivingEntity center) {
        return center.getBoundingBox().inflate(48, 24, 48);
    }
}
