package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.yimeng261.maidspell.item.MaidSpellItems;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import com.github.yimeng261.maidspell.utils.AnchorCoreProtection;
import com.github.yimeng261.maidspell.utils.MaidHardRemovalProtection;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.LevelCallback;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 锚定女仆的存盘区段保护（专用服）：用独立的实体存档与区段管理器，验证删区段、改底层列表都删不掉登记的女仆，
 * 存盘前修复缺失的区段登记，关闭后能从磁盘读回；普通的区块卸载照常摘下并存盘。
 */
@Scene("tlms")
@DisplayName("锚定核心：存盘区段保护（专用服）")
class AnchorPersistenceTest {
    @DisplayName("锚定女仆挺过区段删除与底层列表改写，存盘前修复区段登记，读回后普通卸载照常存盘")
    @Test
    void anchoredMaidSurvivesSectionDeletionAndReload(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            try {
                run(ctx);
            } catch (IOException | ReflectiveOperationException e) {
                throw new IllegalStateException("锚定存盘测试的夹具出错", e);
            }
        });
    }

    private static void run(Stage ctx) throws IOException, ReflectiveOperationException {
        var level = ctx.level();
        EntityMaid maid = InitEntities.MAID.get().create(level);
        if (maid == null) throw ctx.fail("无法创建女仆");
        UUID uuid = maid.getUUID();
        UUID owner = UUID.randomUUID();
        maid.setOwnerUUID(owner);
        maid.setTame(true, false);
        maid.getMaidBauble().setStackInSlot(0, new ItemStack(MaidSpellItems.ANCHOR_CORE.get()));
        BlockPos original = ctx.rel(0, 0, 0);
        maid.setPos(original.getX() + 0.5, original.getY(), original.getZ() + 0.5);
        var chunk = new ChunkPos(original);
        var folder = Files.createTempDirectory("maidspell-anchor-persistence-");
        try {
            try (var first = manager(storage(level, folder))) {
                first.updateChunkStatus(chunk, Visibility.TICKING);
                Entity pig = EntityType.PIG.create(level);
                if (pig == null) throw ctx.fail("无法创建猪");
                pig.setPos(maid.getX(), maid.getY(), maid.getZ());
                ctx.check(first.addNewEntityWithoutEvent(pig)).as("普通实体先加入区段管理器").isTrue();

                @SuppressWarnings("unchecked")
                var sections = (EntitySectionStorage<Entity>) AnchorIndexTest.handle(PersistentEntitySectionManager.class,
                        EntitySectionStorage.class, "sectionStorage").get(first);
                long key = SectionPos.asLong(maid.blockPosition());
                var section = sections.getSection(key);
                @SuppressWarnings("unchecked")
                var entries = (ClassInstanceMultiMap<Entity>) AnchorIndexTest.handle(EntitySection.class,
                        ClassInstanceMultiMap.class, "storage").get(section);
                var listField = AnchorIndexTest.handle(ClassInstanceMultiMap.class, List.class, "allInstances");
                @SuppressWarnings("unchecked")
                var oldAlias = (List<Entity>) listField.get(entries);
                ctx.check(first.addNewEntityWithoutEvent(maid)).as("女仆加入区段管理器").isTrue();
                @SuppressWarnings("unchecked")
                var rawList = (List<Entity>) listField.get(entries);
                ctx.check(rawList != oldAlias && rawList.contains(pig)).as("区段换成私有底层列表且保留原有条目").isTrue();
                oldAlias.clear();
                ctx.check(rawList.contains(pig) && rawList.contains(maid)).as("清空旧列表引用后区段条目").isTrue();
                ctx.check(rawList.remove(maid)).as("登记前从底层列表删除戴锚定核心的女仆").isFalse();
                MaidHardRemovalProtection.rememberProtected(maid);
                maid.setRemoved(Entity.RemovalReason.KILLED);
                ctx.check(maid.isRemoved()).as("硬移除后女仆被标为移除").isFalse();
                var sectionIds = (LongSortedSet) AnchorIndexTest.handle(EntitySectionStorage.class,
                        LongSortedSet.class, "sectionIds").get(sections);
                var callback = (EntityInLevelCallback) AnchorIndexTest.handle(Entity.class,
                        EntityInLevelCallback.class, "levelCallback").get(maid);

                callback.onRemove(Entity.RemovalReason.KILLED);
                ctx.check(section.remove(maid)).as("直接从区段删除女仆").isFalse();
                ctx.check(entries.remove(maid)).as("直接从按类索引删除女仆").isFalse();
                ctx.check(rawList.remove(maid)).as("直接从底层列表删除女仆").isFalse();
                listField.set(entries, new ArrayList<>(rawList));
                ctx.check(listField.get(entries)).as("换成等价新列表后的底层列表").isSameAs(rawList);
                ctx.check(rawList.removeIf(entry -> true)).as("成批删除普通实体").isTrue();
                ctx.check(rawList.size() == 1 && rawList.get(0) == maid).as("成批删除后只剩同一只女仆").isTrue();
                rawList.subList(0, 1).clear();
                ctx.check(rawList.size()).as("经子列表清空后的条目数").isEqualTo(1);
                var iterator = rawList.listIterator();
                iterator.next();
                try {
                    iterator.remove();
                    throw ctx.fail("迭代器删除受保护条目应当被拒绝");
                } catch (UnsupportedOperationException expected) {
                    // 列表迭代器无法用返回 false 表示删除失败，只能抛异常。
                }
                try {
                    Collections.replaceAll(rawList, maid, pig);
                    throw ctx.fail("JDK 工具方法替换受保护条目应当被拒绝");
                } catch (UnsupportedOperationException expected) {
                    // 拒绝发生在列表自身，JDK 内部发起的调用同样被拦下。
                }
                ctx.check(rawList.size() == 1 && rawList.get(0) == maid).as("被拒绝的写入没有改动权威列表").isTrue();
                sections.remove(key);
                sectionIds.remove(key);
                ctx.check(first.getEntityGetter().get(uuid) == maid
                        && sections.getExistingSectionsInChunk(chunk.toLong())
                        .flatMap(EntitySection::getEntities).anyMatch(entry -> entry == maid))
                        .as("UUID 查询与存盘遍历都保留女仆").isTrue();

                maid.setPos(maid.getX(), maid.getY() + 16, maid.getZ());
                ctx.check(section.getEntities().noneMatch(entry -> entry == maid)).as("移动后旧区段摘下女仆").isTrue();
                var movedSection = sections.getSection(SectionPos.asLong(maid.blockPosition()));
                ctx.check(movedSection != null && movedSection.getEntities().anyMatch(entry -> entry == maid))
                        .as("移动后新区段登记同一只女仆").isTrue();
                // 模拟存档索引被单独破坏：UUID 与编号索引仍在，区段里却没有女仆。
                MaidHardRemovalProtection.runAuthorizedRemoval(maid, () -> movedSection.remove(maid));
                ctx.check(movedSection.getEntities().noneMatch(entry -> entry == maid)
                        && first.getEntityGetter().get(uuid) == maid).as("夹具复现区段里缺失的活女仆").isTrue();
                sectionIds.remove(SectionPos.asLong(maid.blockPosition()));
                // close() 必须修复真实的存盘路径并写到磁盘。
            } finally {
                MaidHardRemovalProtection.forgetRegistration(maid);
            }

            var disk = storage(level, folder);
            try (var reopened = manager(disk)) {
                reopened.updateChunkStatus(chunk, Visibility.TICKING);
                for (int i = 0; i < 10 && !reopened.areEntitiesLoaded(chunk.toLong()); i++) {
                    disk.flush(false);
                    reopened.tick();
                }
                ctx.check(reopened.areEntitiesLoaded(chunk.toLong())).as("重开的管理器读完区块实体").isTrue();
                var restored = reopened.getEntityGetter().get(uuid);
                if (!(restored instanceof EntityMaid restoredMaid) || restored == maid) {
                    throw ctx.fail("重开的管理器没从磁盘读回女仆：" + restored);
                }
                ctx.check(owner.equals(restoredMaid.getOwnerUUID()) && AnchorCoreProtection.hasAnchorCore(restoredMaid)
                        && restoredMaid.blockPosition().equals(maid.blockPosition()))
                        .as("读回的女仆保留主人、锚定核心与新位置").isTrue();
                MaidHardRemovalProtection.rememberProtected(restoredMaid);
                try {
                    reopened.updateChunkStatus(chunk, Visibility.HIDDEN);
                    reopened.tick();
                    ctx.check(restoredMaid.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK
                            && reopened.getEntityGetter().get(uuid) == null).as("普通区块卸载摘下受保护的女仆").isTrue();
                } finally {
                    MaidHardRemovalProtection.forgetRegistration(restoredMaid);
                }
                reopened.updateChunkStatus(chunk, Visibility.TICKING);
                for (int i = 0; i < 10 && !reopened.areEntitiesLoaded(chunk.toLong()); i++) {
                    disk.flush(false);
                    reopened.tick();
                }
                var reloaded = reopened.getEntityGetter().get(uuid);
                if (!(reloaded instanceof EntityMaid reloadedMaid) || reloaded == restoredMaid) {
                    throw ctx.fail("普通卸载后没存下能再读回的女仆：" + reloaded);
                }
                MaidHardRemovalProtection.runAuthorizedRemoval(reloadedMaid, reloadedMaid::discard);
                MaidHardRemovalProtection.forgetRegistration(reloadedMaid);
                ctx.check(reopened.getEntityGetter().get(uuid)).as("授权移除后的女仆").isNull();
            }
        } finally {
            try (var files = Files.walk(folder)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            }
        }
    }

    private static EntityStorage storage(ServerLevel level, Path folder) {
        var region = new SimpleRegionStorage(new RegionStorageInfo("stagewright", level.dimension(), "entities"),
                folder, level.getServer().getFixerUpper(), true, DataFixTypes.ENTITY_CHUNK);
        return new EntityStorage(region, level, Runnable::run);
    }

    private static PersistentEntitySectionManager<Entity> manager(EntityStorage storage) {
        return new PersistentEntitySectionManager<>(Entity.class, new LevelCallback<>() {
            @Override public void onCreated(Entity entity) {}
            @Override public void onDestroyed(Entity entity) {}
            @Override public void onTickingStart(Entity entity) {}
            @Override public void onTickingEnd(Entity entity) {}
            @Override public void onTrackingStart(Entity entity) {}
            @Override public void onTrackingEnd(Entity entity) {}
            @Override public void onSectionChange(Entity entity) {}
        }, storage);
    }
}
