package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.compat.touhou_little_maid.HiddenRetreatMaidRegistry;
import com.github.yimeng261.maidspell.compat.touhou_little_maid.MaidOriginData;
import com.github.yimeng261.maidspell.compat.touhou_little_maid.MaidOriginPresetReloadListener;
import com.github.yimeng261.maidspell.dimension.HiddenRetreatMaidData;
import com.github.yimeng261.maidspell.dimension.TheRetreatDimension;
import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Reflect;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import com.github.yimeng261.maidspell.stagewright.support.Lang;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.lang.reflect.Constructor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 隐世女仆的归属记录与共享维度兼容：来源标记在转运中的保留与永久效果协议、认领与结构归属的落盘读回、
 * 独立与共享归隐之地的维度判定和任务匹配、共享模式下结构搜索的串行排队。
 */
@DisplayName("隐世女仆：归属记录与共享归隐之地兼容")
@Scene("tlms")
class HiddenRetreatMaidTest {
    private static final UUID FIRST = UUID.fromString("12345678-1234-5678-9abc-123456789abc");
    private static final UUID SECOND = UUID.fromString("87654321-4321-8765-cba9-cba987654321");
    private static final ResourceLocation SHARED = id("the_retreat");
    private static final ResourceLocation FIRST_PRIVATE = id("the_retreat_12345678_1234_5678_9abc_123456789abc");
    private static final ResourceLocation SECOND_PRIVATE = id("the_retreat_87654321_4321_8765_cba9_cba987654321");
    private static final String QUEUE = TheRetreatDimension.class.getPackageName() + ".SharedRetreatSearchQueue";

    /** 转运只覆盖自己的字段，不动持久数据里其他模组的内容，来源标记里将来新增的字段也原样带过去。 */
    @DisplayName("来源标记转运保留其他模组数据，新增字段原样带过去")
    @Test
    void transportKeepsOtherDataAndFutureFields(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> transportKeepsOtherData(ctx));
    }

    /** 只有根节点上标记的旧数据，经恢复后同样出现在实体加载会读取的持久数据里，并补上来源标记。 */
    @DisplayName("只有根节点标记的旧数据恢复后出现在持久数据里并补上来源标记")
    @Test
    void rootOnlyMarkersSurviveEntityLoad(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> rootOnlyMarkers(ctx));
    }

    /** 声明保留永久效果的女仆，胶片里只补回无限时长的效果；没声明的、魂符里已有效果的都不动。 */
    @DisplayName("声明保留永久效果的女仆，胶片只补回无限时长效果")
    @Test
    void filmRestoresOnlyInfiniteEffects(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> filmRestoresInfiniteEffects(ctx));
    }

    /** 光有来源标记不构成保留永久效果的许可，来源不是合法注册名也不行。 */
    @DisplayName("仅有来源标记或来源非法时不构成保留永久效果的许可")
    @Test
    void originAloneGrantsNoEffectPermission(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> originAloneGrantsNothing(ctx));
    }

    /** 模板实体生成时只快照初始的无限效果，保留效果名与等级，不留数值 id 与隐藏效果，之后改动活动效果不影响快照。 */
    @DisplayName("模板实体生成时只快照初始无限效果，之后改动活动效果不影响快照")
    @Test
    void presetSnapshotsOnlyInitialInfiniteEffects(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> presetSnapshotsInitialEffects(ctx));
    }

    /** 放置的是数据包覆盖过的模板时，快照取自这份模板的初始效果，而不是旧模板里嵌的快照。 */
    @DisplayName("放置数据包覆盖过的模板时，快照取自覆盖后的初始效果")
    @Test
    void presetFollowsPlacedTemplateData(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> presetFollowsPlacedTemplate(ctx));
    }

    /** 没有来源标记的普通女仆模板不会被补上标记，也不会进入效果维护。 */
    @DisplayName("无来源标记的普通女仆模板不会被补标记也不进入效果维护")
    @Test
    void unmarkedMaidAcquiresNoPreset(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> unmarkedMaidAcquiresNoPreset(ctx));
    }

    /** 没有快照的旧标记按模板预设升级一次；已有快照（含显式的空快照）不会被覆盖。 */
    @DisplayName("无快照的旧标记按模板预设只升级一次，已有快照不被覆盖")
    @Test
    void legacyMarkerMigratesFromTemplateOnce(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> legacyMarkerMigratesOnce(ctx));
    }

    /** 转运数据里的效果被清掉后，预设里的无限效果补回来；无关的效果原样保留，重复补不会叠加。 */
    @DisplayName("转运数据里被清掉的预设无限效果会补回，无关效果保留且重复补不叠加")
    @Test
    void transportRestoresMissingPresets(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> transportRestoresPresets(ctx));
    }

    /** 更强的有限效果过期后能回落到初始的无限效果，但更强的等级不会因此变成永久。 */
    @DisplayName("更强的有限效果过期后回落到初始无限效果，但不会变成永久")
    @Test
    void strongerFiniteEffectFallsBackToPreset(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> strongerFiniteFallsBack(ctx));
    }

    /** 效果模组缺失时按名字查不到，不会读成别的效果。 */
    @DisplayName("效果模组缺失时按名字查不到，不会读成别的效果")
    @Test
    void absentEffectIsNotLoaded(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> absentEffectNotLoaded(ctx));
    }

    /** 同一来源下有多份初始效果不同的模板女仆时，旧数据迁移不采用任何一份。 */
    @DisplayName("同一来源下多份初始效果不同的模板女仆，旧数据迁移不采用任何一份")
    @Test
    void sharedSourceWithDifferentEffectsIsAmbiguous(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> sharedSourceAmbiguous(ctx));
    }

    /** 认领落盘后重启仍在、互不影响别的玩家，管理员重置后又能重新领取。 */
    @DisplayName("认领落盘后重启仍在且互不影响别的玩家，重置后可重新领取")
    @Test
    void surviveRestartAndStayPerPlayer(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> claimsSurviveRestart(ctx));
    }

    /** 归属玩家未认领时，每座结构的模板女仆都生成（含并行装饰的多座）；认领之后不再生成。 */
    @DisplayName("玩家未认领时每座结构的模板女仆都生成，认领后不再生成")
    @Test
    void templateMaidFollowsClaim(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> templateMaidFollowsClaim(ctx));
    }

    /** 共享维度里结构的归属落盘后不变，后来的搜索改派不了，范围之外和别的维度都没有归属。 */
    @DisplayName("共享维度里结构的归属落盘后不变，后来的搜索改派不了")
    @Test
    void sharedStructureOwnerCannotBeReassigned(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> structureOwnership(ctx));
    }

    @DisplayName("共享归隐之地的任务条件匹配共享维度和任意玩家的独立归隐之地")
    @Test
    void sharedConditionAcceptsAnyPrivateRetreat(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> sharedConditionAcceptsPrivate(ctx));
    }

    @DisplayName("指定某玩家独立归隐之地的任务条件只匹配该维度")
    @Test
    void explicitConditionStaysExact(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> explicitConditionExact(ctx));
    }

    @DisplayName("名字相近的维度（后缀不合法、别的模组命名空间）不被判为归隐之地")
    @Test
    void similarNamesAreNotRetreats(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> similarNames(ctx));
    }

    @DisplayName("普通维度的任务条件只精确匹配自己，不匹配其他维度和独立归隐之地")
    @Test
    void ordinaryConditionsStayExact(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> ordinaryConditions(ctx));
    }

    /** 排队中的搜索被取消后，不能让它后面的搜索抢在正在进行的搜索完成之前开始。 */
    @DisplayName("排队中的搜索被取消后，后继搜索不会抢在进行中的搜索完成前开始")
    @Test
    void cancelledSearchDoesNotReleaseSuccessor(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> cancelledSearchHoldsQueue(ctx));
    }

    /** 启动失败的搜索不堵后面的搜索，不同维度各自排队。 */
    @DisplayName("启动失败的搜索不堵后面的搜索，不同维度各自排队")
    @Test
    void failureDoesNotBlockAndDimensionsIndependent(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> failureDoesNotBlock(ctx));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, path);
    }

    // ---- 来源标记 ----

    private static CompoundTag persistent(boolean preserve) {
        CompoundTag tag = new CompoundTag();
        tag.put(MaidOriginData.ORIGIN, MaidOriginData.createOrigin("touhou_little_maid_spell:fallen_sanctum", preserve));
        return tag;
    }

    private static void copyToTransport(CompoundTag persistent, CompoundTag data) {
        Reflect.call(null, MaidOriginData.class, "copyToTransport",
                new Class<?>[]{CompoundTag.class, CompoundTag.class}, persistent, data);
    }

    private static void restoreTransport(CompoundTag data, CompoundTag persistent) {
        Reflect.call(null, MaidOriginData.class, "restoreTransportData",
                new Class<?>[]{CompoundTag.class, CompoundTag.class}, data, persistent);
    }

    private static void restorePermanentEffects(CompoundTag persistent, ListTag effects, CompoundTag data) {
        Reflect.call(null, MaidOriginData.class, "restorePermanentEffects",
                new Class<?>[]{CompoundTag.class, ListTag.class, CompoundTag.class}, persistent, effects, data);
    }

    private static void transportKeepsOtherData(Stage ctx) {
        CompoundTag persistent = persistent(true);
        persistent.getCompound(MaidOriginData.ORIGIN).putString("FutureField", "retained");
        CompoundTag data = new CompoundTag();
        CompoundTag persistentData = new CompoundTag();
        persistentData.putString("OtherMod", "keep");
        data.put("NeoForgeData", persistentData);
        copyToTransport(persistent, data);
        ctx.check(data.getCompound("NeoForgeData").getString("OtherMod")).as("其他模组的数据").isEqualTo("keep");
        ctx.check(MaidOriginData.getOrigin(data).getString("FutureField")).as("新增字段随转运带过去").isEqualTo("retained");
        persistent.getCompound(MaidOriginData.ORIGIN).putString("FutureField", "changed");
        ctx.check(MaidOriginData.getOrigin(data).getString("FutureField")).as("转运数据是副本").isEqualTo("retained");
        CompoundTag restored = new CompoundTag();
        restoreTransport(data, restored);
        ctx.check(MaidOriginData.preservesPermanentEffects(restored)).as("恢复后仍声明保留永久效果").isTrue();
        ctx.check(restored.getCompound(MaidOriginData.ORIGIN).equals(data.getCompound(MaidOriginData.ORIGIN)))
                .as("恢复出的来源标记与转运数据一致").isTrue();
    }

    private static void rootOnlyMarkers(Stage ctx) {
        CompoundTag data = new CompoundTag();
        data.putBoolean(MaidOriginData.HIDDEN_RETREAT_MAID, true);
        data.putBoolean(MaidOriginData.STAR_WITCH_VICTORY_TAMED, true);
        restoreTransport(data, new CompoundTag());
        CompoundTag loadedPersistent = data.getCompound("NeoForgeData");
        ctx.check(loadedPersistent.getBoolean(MaidOriginData.HIDDEN_RETREAT_MAID)).as("隐世女仆标记").isTrue();
        ctx.check(loadedPersistent.getBoolean(MaidOriginData.STAR_WITCH_VICTORY_TAMED)).as("星之魔女标记").isTrue();
        ctx.check(MaidOriginData.isHiddenRetreatMaid(loadedPersistent)).as("按持久数据判定为隐世女仆").isTrue();
        ctx.check(MaidOriginData.getOrigin(data).getString("Source")).as("补上的来源")
                .isEqualTo(MaidOriginData.HIDDEN_RETREAT_SOURCE);
        ctx.check(MaidOriginData.preservesPermanentEffects(data)).as("旧数据不声明保留永久效果").isFalse();
    }

    private static void filmRestoresInfiniteEffects(Stage ctx) {
        CompoundTag permanent = new CompoundTag();
        permanent.putInt("duration", -1);
        permanent.putString("id", "future_mod:effect");
        permanent.putByte("amplifier", (byte) 4);
        CompoundTag finite = new CompoundTag();
        finite.putInt("duration", 1200);
        ListTag effects = new ListTag();
        effects.add(permanent);
        effects.add(finite);

        CompoundTag film = new CompoundTag();
        restorePermanentEffects(persistent(true), effects, film);
        ListTag restored = film.getList("active_effects", Tag.TAG_COMPOUND);
        ctx.check(restored.size()).as("胶片补回的效果数").isEqualTo(1);
        ctx.check(restored.getCompound(0).equals(permanent)).as("补回的是无限时长那条").isTrue();

        CompoundTag ordinaryFilm = new CompoundTag();
        restorePermanentEffects(new CompoundTag(), effects, ordinaryFilm);
        ctx.check(ordinaryFilm.contains("active_effects")).as("未声明的女仆不补效果").isFalse();

        CompoundTag charm = new CompoundTag();
        charm.put("active_effects", effects.copy());
        restorePermanentEffects(persistent(true), effects, charm);
        ctx.check(effects.equals(charm.get("active_effects"))).as("魂符里已有的效果原样保留").isTrue();
    }

    private static void originAloneGrantsNothing(Stage ctx) {
        ctx.check(MaidOriginData.preservesPermanentEffects(persistent(false))).as("未声明保留").isFalse();
        ctx.check(MaidOriginData.isHiddenRetreatMaid(persistent(true))).as("别的来源不是隐世女仆").isFalse();
        CompoundTag invalid = persistent(true);
        invalid.getCompound(MaidOriginData.ORIGIN).putString("Source", "invalid source");
        ctx.check(MaidOriginData.preservesPermanentEffects(invalid)).as("来源不是合法注册名").isFalse();
    }

    // ---- 预设效果 ----

    private static final String ELVEN_REALM = "touhou_little_maid_spell:elven_realm";

    private static CompoundTag effect(String id, int duration, int amplifier) {
        CompoundTag effect = new CompoundTag();
        effect.putString("id", id);
        effect.putInt("duration", duration);
        effect.putByte("amplifier", (byte) amplifier);
        effect.putBoolean("show_particles", false);
        return effect;
    }

    private static CompoundTag templateMaid() {
        CompoundTag data = new CompoundTag();
        data.putString("id", "touhou_little_maid:maid");
        CompoundTag persistent = new CompoundTag();
        persistent.put(MaidOriginData.ORIGIN, MaidOriginData.createOrigin(ELVEN_REALM, true));
        data.put("NeoForgeData", persistent);
        return data;
    }

    private static ListTag presetOf(CompoundTag data) {
        return MaidOriginData.getOrigin(data).getList(MaidOriginData.PRESET_EFFECTS, Tag.TAG_COMPOUND);
    }

    private static void initializeLegacyPresets(CompoundTag persistent, ListTag templatePresets) {
        Reflect.call(null, MaidOriginData.class, "initializeLegacyPresets",
                new Class<?>[]{CompoundTag.class, ListTag.class}, persistent, templatePresets);
    }

    private static void mergePresetEffects(CompoundTag persistent, CompoundTag data) {
        Reflect.call(null, MaidOriginData.class, "mergePresetEffects",
                new Class<?>[]{CompoundTag.class, CompoundTag.class}, persistent, data);
    }

    private static boolean needsPresetEffect(MobEffectInstance current, MobEffectInstance expected) {
        return Reflect.<Boolean>call(null, MaidOriginData.class, "needsPresetEffect",
                new Class<?>[]{MobEffectInstance.class, MobEffectInstance.class}, current, expected);
    }

    private static void presetSnapshotsInitialEffects(Stage ctx) {
        CompoundTag template = templateMaid();
        ListTag active = new ListTag();
        CompoundTag permanent = effect("minecraft:regeneration", -1, 1);
        permanent.put("hidden_effect", effect("minecraft:regeneration", 400, 0));
        active.add(permanent);
        active.add(effect("minecraft:speed", 200, 0));
        active.add(effect("absent_mod:blessing", -1, 3));
        template.put("active_effects", active);

        MaidOriginData.captureTemplatePresetEffects(template);
        ListTag preset = presetOf(template);
        ctx.check(preset.size()).as("快照里的效果数").isEqualTo(2);
        ctx.check(preset.getCompound(0).getString("id")).as("第一条").isEqualTo("minecraft:regeneration");
        ctx.check((int) preset.getCompound(0).getByte("amplifier")).as("第一条等级").isEqualTo(1);
        ctx.check(preset.getCompound(0).contains("hidden_effect")).as("隐藏效果不进快照").isFalse();
        ctx.check(preset.getCompound(1).getString("id")).as("缺失模组的效果也保留名字").isEqualTo("absent_mod:blessing");

        permanent.putByte("amplifier", (byte) 8);
        active.add(effect("minecraft:strength", -1, 5));
        CompoundTag restored = new CompoundTag();
        restoreTransport(template, restored);
        ctx.check(presetOf(restored).equals(preset)).as("转运恢复后快照不随活动效果变化").isTrue();
        ctx.check(presetOf(restored).size()).as("恢复后的效果数").isEqualTo(2);
    }

    private static void presetFollowsPlacedTemplate(Stage ctx) {
        CompoundTag template = templateMaid();
        ListTag original = new ListTag();
        original.add(effect("minecraft:regeneration", -1, 1));
        template.getCompound("NeoForgeData").getCompound(MaidOriginData.ORIGIN).put(MaidOriginData.PRESET_EFFECTS, original);
        ListTag replacement = new ListTag();
        replacement.add(effect("minecraft:strength", -1, 2));
        template.put("active_effects", replacement);
        MaidOriginData.captureTemplatePresetEffects(template);
        ctx.check(presetOf(template).equals(replacement)).as("快照取自覆盖后的模板").isTrue();
    }

    private static void unmarkedMaidAcquiresNoPreset(Stage ctx) {
        CompoundTag ordinary = new CompoundTag();
        ordinary.putString("id", "touhou_little_maid:maid");
        ListTag active = new ListTag();
        active.add(effect("minecraft:strength", -1, 5));
        ordinary.put("active_effects", active);
        CompoundTag before = ordinary.copy();
        MaidOriginData.captureTemplatePresetEffects(ordinary);
        ctx.check(ordinary.equals(before)).as("普通女仆模板数据不变").isTrue();
    }

    private static void legacyMarkerMigratesOnce(Stage ctx) {
        CompoundTag persistent = new CompoundTag();
        CompoundTag origin = MaidOriginData.createOrigin(ELVEN_REALM, true);
        origin.remove(MaidOriginData.PRESET_EFFECTS);
        persistent.put(MaidOriginData.ORIGIN, origin);
        ListTag templateEffects = new ListTag();
        templateEffects.add(effect("minecraft:regeneration", -1, 1));
        initializeLegacyPresets(persistent, null);
        ctx.check(origin.contains(MaidOriginData.PRESET_EFFECTS)).as("没有模板预设时不升级").isFalse();
        initializeLegacyPresets(persistent, templateEffects);
        templateEffects.add(effect("minecraft:strength", -1, 5));
        initializeLegacyPresets(persistent, templateEffects);
        ctx.check(origin.getList(MaidOriginData.PRESET_EFFECTS, Tag.TAG_COMPOUND).size()).as("只升级一次").isEqualTo(1);
        origin.put(MaidOriginData.PRESET_EFFECTS, new ListTag());
        initializeLegacyPresets(persistent, templateEffects);
        ctx.check(origin.getList(MaidOriginData.PRESET_EFFECTS, Tag.TAG_COMPOUND).isEmpty()).as("显式空快照不被覆盖").isTrue();
    }

    private static void transportRestoresPresets(Stage ctx) {
        CompoundTag persistent = new CompoundTag();
        ListTag preset = new ListTag();
        preset.add(effect("minecraft:regeneration", -1, 1));
        preset.add(effect("absent_mod:blessing", -1, 3));
        persistent.put(MaidOriginData.ORIGIN, MaidOriginData.createOrigin(ELVEN_REALM, true, preset));
        CompoundTag data = new CompoundTag();
        ListTag current = new ListTag();
        current.add(effect("minecraft:strength", 100, 5));
        data.put("active_effects", current);
        mergePresetEffects(persistent, data);
        ListTag merged = data.getList("active_effects", Tag.TAG_COMPOUND);
        ctx.check(merged.size()).as("补回后的效果数").isEqualTo(3);
        ctx.check(merged.getCompound(0).equals(current.getCompound(0))).as("无关效果原样保留").isTrue();
        mergePresetEffects(persistent, data);
        ctx.check(data.getList("active_effects", Tag.TAG_COMPOUND).size()).as("重复补不叠加").isEqualTo(3);
        ctx.check(presetOf(persistent).size()).as("预设本身不变").isEqualTo(2);
    }

    private static void strongerFiniteFallsBack(Stage ctx) {
        MobEffectInstance expected = new MobEffectInstance(MobEffects.REGENERATION, -1, 1);
        MobEffectInstance current = new MobEffectInstance(MobEffects.REGENERATION, 200, 4);
        ctx.check(needsPresetEffect(null, expected)).as("没有该效果").isTrue();
        ctx.check(needsPresetEffect(current, expected)).as("只有有限时长的更强效果").isTrue();
        current.update(expected);
        ctx.check(current.getAmplifier()).as("合并后仍是更强等级").isEqualTo(4);
        ctx.check(current.getDuration()).as("合并后仍是有限时长").isEqualTo(200);
        CompoundTag saved = (CompoundTag) current.save();
        ctx.check(saved.getCompound("hidden_effect").getInt("duration")).as("垫底的初始效果时长").isEqualTo(-1);
        ctx.check((int) saved.getCompound("hidden_effect").getByte("amplifier")).as("垫底的初始效果等级").isEqualTo(1);
        ctx.check(needsPresetEffect(expected, expected)).as("已有同样的无限效果").isFalse();
        ctx.check(needsPresetEffect(new MobEffectInstance(MobEffects.REGENERATION, -1, 4), expected))
                .as("已有更强的无限效果").isFalse();
        ctx.check(needsPresetEffect(new MobEffectInstance(MobEffects.REGENERATION, -1, 0), expected))
                .as("已有较弱的无限效果").isTrue();
    }

    private static void absentEffectNotLoaded(Stage ctx) {
        MobEffectInstance absent = Reflect.call(null, MaidOriginData.class, "loadPresetEffect",
                new Class<?>[]{CompoundTag.class}, effect("absent_mod:blessing", -1, 3));
        ctx.check(absent == null).as("缺失的效果").isTrue();
        MobEffectInstance present = Reflect.call(null, MaidOriginData.class, "loadPresetEffect",
                new Class<?>[]{CompoundTag.class}, effect("minecraft:regeneration", -1, 1));
        ctx.check(present != null).as("存在的效果").isTrue();
        ctx.check(present.getAmplifier()).as("读回的等级").isEqualTo(1);
        ctx.check(present.isInfiniteDuration()).as("读回的是无限时长").isTrue();
    }

    private static void sharedSourceAmbiguous(Stage ctx) {
        var sources = new HashMap<String, ListTag>();
        ListTag first = new ListTag();
        first.add(effect("minecraft:regeneration", -1, 1));
        ListTag second = new ListTag();
        second.add(effect("minecraft:strength", -1, 1));
        ctx.check(mergeSource(sources, "mod:source", first)).as("第一份").isTrue();
        ctx.check(mergeSource(sources, "mod:source", first.copy())).as("相同的一份").isTrue();
        ctx.check(mergeSource(sources, "mod:source", second)).as("不同的一份").isFalse();
    }

    private static boolean mergeSource(HashMap<String, ListTag> sources, String source, ListTag effects) {
        return Reflect.<Boolean>call(null, MaidOriginPresetReloadListener.class, "mergeSource",
                new Class<?>[]{java.util.Map.class, String.class, ListTag.class}, sources, source, effects);
    }

    // ---- 认领与结构归属 ----

    private static HiddenRetreatMaidData reload(Stage ctx, HiddenRetreatMaidData data) {
        HolderLookup.Provider registries = ctx.server().registryAccess();
        return HiddenRetreatMaidData.load(data.save(new CompoundTag(), registries), registries);
    }

    private static void claimsSurviveRestart(Stage ctx) {
        HiddenRetreatMaidData data = new HiddenRetreatMaidData();
        ctx.check(data.claim(FIRST)).as("第一次认领").isTrue();
        ctx.check(data.claim(FIRST)).as("重复认领").isFalse();
        HiddenRetreatMaidData loaded = reload(ctx, data);
        ctx.check(loaded.hasClaim(FIRST)).as("重启后保留认领").isTrue();
        ctx.check(loaded.hasClaim(SECOND)).as("别的玩家没有认领").isFalse();
        ctx.check(loaded.reset(FIRST)).as("重置认领").isTrue();
        ctx.check(loaded.hasClaim(FIRST)).as("重置后没有认领").isFalse();
    }

    private static void templateMaidFollowsClaim(Stage ctx) {
        HiddenRetreatMaidData data = HiddenRetreatMaidRegistry.get(ctx.server());
        UUID player = UUID.randomUUID();
        BlockPos first = ctx.origin();
        BlockPos second = ctx.origin().offset(32, 0, 0);
        ResourceLocation dimension = ctx.level().dimension().location();
        for (BlockPos pos : List.of(first, second)) {
            data.bindStructure(new HiddenRetreatMaidData.StructureOwner(dimension, pos.getX() - 4, pos.getY() - 4,
                    pos.getZ() - 4, pos.getX() + 4, pos.getY() + 4, pos.getZ() + 4, player));
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("id", Actors.MAID);
        ctx.check(HiddenRetreatMaidRegistry.createTemplateMaid(ctx.level(), tag.copy(), first).isPresent()).as("第一座结构").isTrue();
        ctx.check(HiddenRetreatMaidRegistry.createTemplateMaid(ctx.level(), tag.copy(), second).isPresent()).as("第二座结构").isTrue();
        data.claim(player);
        ctx.check(HiddenRetreatMaidRegistry.createTemplateMaid(ctx.level(), tag.copy(), first).isPresent()).as("认领之后").isFalse();
        data.reset(player);
    }

    private static HiddenRetreatMaidData.StructureOwner owner(UUID player) {
        return new HiddenRetreatMaidData.StructureOwner(SHARED, 0, 60, 0, 10, 80, 10, player);
    }

    private static void structureOwnership(Stage ctx) {
        HiddenRetreatMaidData data = new HiddenRetreatMaidData();
        data.bindStructure(owner(FIRST));
        data.bindStructure(owner(SECOND));
        HiddenRetreatMaidData loaded = reload(ctx, data);
        ctx.check(FIRST.equals(loaded.ownerAt(SHARED, new BlockPos(5, 70, 5)))).as("范围内属于最先绑定的玩家").isTrue();
        ctx.check(loaded.ownerAt(SHARED, new BlockPos(11, 70, 5))).as("范围外没有归属").isNull();
        ctx.check(loaded.ownerAt(ResourceLocation.fromNamespaceAndPath("other", "the_retreat"), new BlockPos(5, 70, 5)))
                .as("别的维度没有归属").isNull();
    }

    // ---- 维度判定 ----

    private static void sharedConditionAcceptsPrivate(Stage ctx) {
        ctx.check(TheRetreatDimension.matchesTaskDimension(SHARED, SHARED)).as("共享匹配共享").isTrue();
        ctx.check(TheRetreatDimension.matchesTaskDimension(SHARED, FIRST_PRIVATE)).as("共享匹配甲的独立维度").isTrue();
        ctx.check(TheRetreatDimension.matchesTaskDimension(SHARED, SECOND_PRIVATE)).as("共享匹配乙的独立维度").isTrue();
        ctx.check(TheRetreatDimension.isPrivateRetreatDimension(SHARED)).as("共享不是独立维度").isFalse();
        ctx.check(TheRetreatDimension.isPrivateRetreatDimension(FIRST_PRIVATE)).as("甲的维度是独立维度").isTrue();
    }

    private static void explicitConditionExact(Stage ctx) {
        ctx.check(TheRetreatDimension.matchesTaskDimension(FIRST_PRIVATE, FIRST_PRIVATE)).as("指定玩家匹配自己").isTrue();
        ctx.check(TheRetreatDimension.matchesTaskDimension(FIRST_PRIVATE, SECOND_PRIVATE)).as("指定玩家不匹配别人").isFalse();
        ctx.check(TheRetreatDimension.matchesTaskDimension(FIRST_PRIVATE, SHARED)).as("指定玩家不匹配共享").isFalse();
    }

    private static void similarNames(Stage ctx) {
        ResourceLocation[] locations = {
                id("the_retreat_"), id("the_retreat_backup"), id("the_retreat_old"),
                id("the_retreat_1_2_3_4_5"), id("the_retreat_12345678-1234-5678-9abc-123456789abc"),
                ResourceLocation.fromNamespaceAndPath("other_mod", "the_retreat"),
                ResourceLocation.fromNamespaceAndPath("other_mod", FIRST_PRIVATE.getPath()),
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld")};
        for (ResourceLocation location : locations) {
            ctx.check(TheRetreatDimension.isRetreatDimension(location)).as(location + " 是归隐之地").isFalse();
            ctx.check(TheRetreatDimension.matchesTaskDimension(SHARED, location)).as(location + " 匹配共享条件").isFalse();
        }
    }

    private static void ordinaryConditions(Stage ctx) {
        ResourceLocation overworld = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
        ResourceLocation nether = ResourceLocation.fromNamespaceAndPath("minecraft", "the_nether");
        ctx.check(TheRetreatDimension.matchesTaskDimension(overworld, overworld)).as("主世界匹配自己").isTrue();
        ctx.check(TheRetreatDimension.matchesTaskDimension(overworld, nether)).as("主世界不匹配下界").isFalse();
        ctx.check(TheRetreatDimension.matchesTaskDimension(overworld, FIRST_PRIVATE)).as("主世界不匹配独立维度").isFalse();
    }

    // ---- 搜索队列 ----

    /** 同一线程上手动推进的执行器。 */
    private static final class QueuedExecutor implements Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        void drain() {
            while (!tasks.isEmpty()) {
                tasks.remove().run();
            }
        }
    }

    private static Object newQueue() {
        try {
            Constructor<?> constructor = Class.forName(QUEUE).getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法创建共享归隐之地搜索队列", e);
        }
    }

    private static void enqueue(Object queue, ResourceKey<Level> dimension, Executor server,
                                CompletableFuture<?> result, Runnable start) {
        try {
            Reflect.call(queue, Class.forName(QUEUE), "enqueue",
                    new Class<?>[]{ResourceKey.class, Executor.class, CompletableFuture.class, Runnable.class},
                    dimension, server, result, start);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("找不到共享归隐之地搜索队列", e);
        }
    }

    private static void cancelledSearchHoldsQueue(Stage ctx) {
        Object queue = newQueue();
        QueuedExecutor server = new QueuedExecutor();
        List<Integer> started = new ArrayList<>();
        CompletableFuture<Void> first = new CompletableFuture<>();
        CompletableFuture<Void> second = new CompletableFuture<>();
        CompletableFuture<Void> third = new CompletableFuture<>();
        enqueue(queue, Level.OVERWORLD, server, first, () -> started.add(1));
        enqueue(queue, Level.OVERWORLD, server, second, () -> started.add(2));
        enqueue(queue, Level.OVERWORLD, server, third, () -> started.add(3));
        server.drain();
        ctx.check(started).as("只有第一个开始").isEqualTo(List.of(1));
        second.cancel(false);
        server.drain();
        ctx.check(started).as("取消第二个后第三个仍在等第一个").isEqualTo(List.of(1));
        first.complete(null);
        server.drain();
        ctx.check(started).as("第一个完成后跳过已取消的，第三个开始").isEqualTo(List.of(1, 3));
        third.complete(null);
        server.drain();
    }

    private static void failureDoesNotBlock(Stage ctx) {
        Object queue = newQueue();
        QueuedExecutor server = new QueuedExecutor();
        CompletableFuture<Void> failed = new CompletableFuture<>();
        CompletableFuture<Void> next = new CompletableFuture<>();
        CompletableFuture<Void> otherDimension = new CompletableFuture<>();
        List<String> started = new ArrayList<>();
        enqueue(queue, Level.OVERWORLD, server, failed, () -> {
            throw new IllegalStateException("failed start");
        });
        enqueue(queue, Level.OVERWORLD, server, next, () -> started.add("next"));
        enqueue(queue, Level.NETHER, server, otherDimension, () -> started.add("other"));
        server.drain();
        ctx.check(failed.isCompletedExceptionally()).as("启动失败的搜索以异常结束").isTrue();
        ctx.check(started.containsAll(List.of("next", "other"))).as("后面的搜索和别的维度的搜索都开始了").isTrue();
    }

    /** 管理命令：只有 OP 能用；status 报认领记录，reset 清掉记录并报原值；三种语言都有文案。 */
    @DisplayName("管理命令 status 报认领记录、reset 清掉后再查为 false，非 OP 不能用，文案三种语言齐全")
    @Test
    void claimCommand(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> {
            String root = "maidspell_retreat_maid";
            CommandNode<CommandSourceStack> node = ctx.server().getCommands().getDispatcher().getRoot().getChild(root);
            if (node == null) {
                throw ctx.fail("没有 " + root + " 命令");
            }
            CommandSourceStack base = ctx.server().createCommandSourceStack();
            ctx.check(node.canUse(base.withPermission(2))).as("OP 能用").isTrue();
            ctx.check(node.canUse(base.withPermission(0))).as("非 OP 能用").isFalse();
            UUID player = UUID.randomUUID();
            HiddenRetreatMaidData data = HiddenRetreatMaidRegistry.get(ctx.server());
            data.claim(player);
            List<Component> replies = new ArrayList<>();
            CommandSourceStack op = new CommandSourceStack(new CommandSource() {
                @Override
                public void sendSystemMessage(Component message) {
                    replies.add(message);
                }

                @Override
                public boolean acceptsSuccess() {
                    return true;
                }

                @Override
                public boolean acceptsFailure() {
                    return true;
                }

                @Override
                public boolean shouldInformAdmins() {
                    return false;
                }
            }, Vec3.ZERO, Vec2.ZERO, ctx.server().overworld(), 2, "TlmsClaimAdmin",
                    Component.literal("TlmsClaimAdmin"), ctx.server(), null);
            for (String action : List.of("status", "reset", "status")) {
                ctx.server().getCommands().performPrefixedCommand(op, root + " " + action + " " + player);
            }
            ctx.check(replies.stream().map(HiddenRetreatMaidTest::describe).toList()).as("三条命令的回复")
                    .isEqualTo(List.of("status " + player + " true", "reset " + player + " true", "status " + player + " false"));
            ctx.check(data.hasClaim(player)).as("reset 后的认领记录").isFalse();
            for (String code : List.of("zh_cn", "en_us", "ja_jp")) {
                Map<String, String> lang = Lang.read(code);
                for (String action : List.of("status", "reset")) {
                    ctx.check(lang.containsKey("commands.touhou_little_maid_spell.retreat_maid." + action))
                            .as(code + " 有 " + action + " 文案").isTrue();
                }
            }
        });
    }

    /** 回复的翻译键末段与参数，形如 "status &lt;uuid&gt; true"。 */
    private static String describe(Component message) {
        if (!(message.getContents() instanceof TranslatableContents contents)) {
            return message.getString();
        }
        String key = contents.getKey();
        StringBuilder out = new StringBuilder(key.substring(key.lastIndexOf('.') + 1));
        for (Object arg : contents.getArgs()) {
            out.append(' ').append(arg instanceof Component component ? component.getString() : String.valueOf(arg));
        }
        return out.toString();
    }
}
