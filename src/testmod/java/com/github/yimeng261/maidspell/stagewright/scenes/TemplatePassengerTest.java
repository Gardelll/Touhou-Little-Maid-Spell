package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.stagewright.support.Actors;
import com.github.yimeng261.maidspell.stagewright.support.Stage;
import com.github.yimeng261.maidspell.worldgen.accessor.StructureTemplateAccessor;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.util.List;

/**
 * 模板里的乘客（专用服）：原版放置模板只读顶层实体，Passengers 里的乘客会丢失；本模组的模板逐个创建乘客并骑上载具，
 * 别的命名空间的模板保持原版行为。模板在内存里拼：一只猪载着一只僵尸，是否按本模组模板处理由加载时的标记决定。
 */
@Scene("tlms")
@DisplayName("结构模板：乘客只在本模组的模板里生成")
class TemplatePassengerTest {
    @DisplayName("本模组的模板放置时，载具上的乘客一起生成并骑在载具上")
    @Test
    void ownTemplateSpawnsPassengers(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            Placed placed = place(ctx, true);
            ctx.check(placed.pigs()).as("生成的猪").hasSize(1);
            ctx.check(placed.zombies()).as("生成的僵尸").hasSize(1);
            if (placed.pigs().size() == 1 && placed.zombies().size() == 1) {
                ctx.check(placed.zombies().getFirst().getVehicle()).as("僵尸骑着的载具").isSameAs(placed.pigs().getFirst());
            }
        });
    }

    @DisplayName("别的命名空间的模板与原版一致，乘客不生成")
    @Test
    void foreignTemplateDropsPassengers(Game game, @Plot.Size(32) @Plot.Centered Plot plot, TestReporter reporter) {
        Stage ctx = Stage.flat(game, plot, reporter);
        ctx.run(() -> {
            Placed placed = place(ctx, false);
            ctx.check(placed.pigs()).as("生成的猪").hasSize(1);
            ctx.check(placed.zombies()).as("生成的僵尸").isEmpty();
        });
    }

    private record Placed(List<Pig> pigs, List<Zombie> zombies) {
    }

    /** 在原点放一个只有一格空气、一只载着僵尸的猪的模板；服务端线程。 */
    private static Placed place(Stage ctx, boolean own) {
        StructureTemplate template = new StructureTemplate();
        template.load(ctx.level().holderLookup(Registries.BLOCK), templateTag());
        ((StructureTemplateAccessor) template).maidspell$setOwnTemplate(own);
        BlockPos origin = ctx.rel(0, 0, 0);
        template.placeInWorld(ctx.level(), origin, origin, new StructurePlaceSettings(), ctx.level().random, Block.UPDATE_CLIENTS);
        AABB area = new AABB(origin).inflate(4);
        List<Pig> pigs = ctx.level().getEntitiesOfClass(Pig.class, area);
        List<Zombie> zombies = ctx.level().getEntitiesOfClass(Zombie.class, area);
        pigs.forEach(pig -> ctx.cleanup(() -> Actors.cleanupEntity(pig)));
        zombies.forEach(zombie -> ctx.cleanup(() -> Actors.cleanupEntity(zombie)));
        return new Placed(pigs, zombies);
    }

    private static CompoundTag templateTag() {
        CompoundTag zombie = new CompoundTag();
        zombie.putString("id", "minecraft:zombie");
        zombie.putBoolean("NoAI", true);
        zombie.putBoolean("PersistenceRequired", true);
        ListTag passengers = new ListTag();
        passengers.add(zombie);
        CompoundTag pig = new CompoundTag();
        pig.putString("id", "minecraft:pig");
        pig.putBoolean("NoAI", true);
        pig.putBoolean("PersistenceRequired", true);
        pig.put("Passengers", passengers);
        CompoundTag entity = new CompoundTag();
        entity.put("pos", doubles(0.5, 0.0, 0.5));
        entity.put("blockPos", ints(0, 0, 0));
        entity.put("nbt", pig);
        ListTag entities = new ListTag();
        entities.add(entity);

        CompoundTag air = new CompoundTag();
        air.putString("Name", "minecraft:air");
        ListTag palette = new ListTag();
        palette.add(air);
        CompoundTag block = new CompoundTag();
        block.put("pos", ints(0, 0, 0));
        block.putInt("state", 0);
        ListTag blocks = new ListTag();
        blocks.add(block);

        CompoundTag tag = new CompoundTag();
        tag.put("size", ints(1, 1, 1));
        tag.put("palette", palette);
        tag.put("blocks", blocks);
        tag.put("entities", entities);
        tag.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        return tag;
    }

    private static ListTag doubles(double... values) {
        ListTag list = new ListTag();
        for (double value : values) {
            list.add(DoubleTag.valueOf(value));
        }
        return list;
    }

    private static ListTag ints(int... values) {
        ListTag list = new ListTag();
        for (int value : values) {
            list.add(IntTag.valueOf(value));
        }
        return list;
    }
}
