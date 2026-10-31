package com.github.yimeng261.maidspell.stagewright.scenes;

import com.github.yimeng261.maidspell.stagewright.support.Stage;
import net.magicterra.stagewright.test.Game;
import net.magicterra.stagewright.test.Plot;
import net.magicterra.stagewright.test.Scene;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.yimeng261.maidspell.MaidSpellMod;
import com.github.yimeng261.maidspell.stagewright.support.Lang;
import com.github.yimeng261.maidspell.stagewright.support.Logs;
import com.github.yimeng261.maidspell.stagewright.support.StructureStage;
import com.github.yimeng261.maidspell.stagewright.support.WorldExtract;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

import static com.github.yimeng261.maidspell.stagewright.support.Checks.NS;

/**
 * 1.21 格式模板与结构内容：旗帜图案、没有写死的中文名、阴阳祭坛放置无日志报错、结构女仆步高；
 * 《万法皆通》手册分类；月铃兰在不会有精灵秘境的维度里不拖慢服务器。
 */
@Scene("tlms")
class TemplateTest {
    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");
    private static final String BOOK = "/assets/touhou_little_maid/patchouli_books/memorizable_gensokyo/en_us/";
    private static final ResourceLocation STEP_ID = ResourceLocation.fromNamespaceAndPath(MaidSpellMod.MOD_ID, "maid_step_height");

    /** 冰屋与热带草原村庄房屋模板里的旗帜都带图案。 */
    @Test
    void iglooAndSavannaBannersHavePatterns(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> banners(ctx));
    }

    /** 实体与物品上的自定义名字不是写死的中文（应是可翻译文本）。 */
    @Test
    void noHardcodedChineseNames(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> noChineseNames(ctx));
    }

    @Test
    void yinYangAltarPlacesWithoutLogErrors(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot,
                                            TestReporter reporter) {
        Stage ctx = Stage.wideFlat(game, plot, reporter);
        List<String> before = Logs.lines();
        int start = before == null ? 0 : before.size();
        ctx.run(() -> StructureStage.place(ctx, NS + "yin_yang_altar"));
        ctx.ticks(10);
        List<String> lines = Logs.lines();
        if (lines == null) {
            throw ctx.fail("读不到 " + Logs.path());
        }
        List<String> bad = lines.subList(Math.min(start, lines.size()), lines.size()).stream()
                .filter(line -> line.contains("Item must not be minecraft:air") || line.contains("invalid fluid")).toList();
        ctx.check(bad).as("放置阴阳祭坛后日志里的报错").isEmpty();
    }

    @Test
    void hiddenRetreatStepHeight(Game game, @Plot.Size(x = 176, y = 256, z = 176) @Plot.Centered Plot plot,
                                 TestReporter reporter) {
        stepHeight(Stage.wideFlat(game, plot, reporter), "hidden_retreat");
    }

    @Test
    void fallenSanctumStepHeight(Game game, @Plot.Size(x = 240, y = 256, z = 240) @Plot.Centered Plot plot,
                                 TestReporter reporter) {
        stepHeight(Stage.wideFlat(game, plot, reporter), "fallen_sanctum");
    }

    @Test
    void relicSanctumStepHeight(Game game, @Plot.Size(x = 144, y = 256, z = 144) @Plot.Centered Plot plot,
                                TestReporter reporter) {
        stepHeight(Stage.wideFlat(game, plot, reporter), "relic_sanctum");
    }

    @Test
    void elvenRealmStepHeight(Game game, @Plot.Size(x = 208, y = 256, z = 208) @Plot.Centered Plot plot,
                              TestReporter reporter) {
        stepHeight(Stage.wideFlat(game, plot, reporter), "elven_realm");
    }

    @Test
    void fairyMaidCafeStepHeight(Game game, @Plot.Size(x = 80, y = 128, z = 80) @Plot.Centered Plot plot,
                                 TestReporter reporter) {
        stepHeight(Stage.wideFlat(game, plot, reporter), "fairy_maid_cafe");
    }

    /** 分类图标是梦云水晶；三个条目的图标、配图存在；所有文本键两种语言都有。 */
    @Test
    void patchouliWanfaJietongCategory(Game game, Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        patchouli(ctx);
    }

    /** 下界里种 16 株月铃兰，之后 100 tick 服务器平均每 tick 耗时仍在 50 毫秒以内。 */
    @Test
    void yueLinglanNetherTickCost(Game game, @Plot.In("minecraft:the_nether") Plot plot, TestReporter reporter) {
        Stage ctx = Stage.sky(game, plot, reporter);
        ctx.run(() -> {
            ServerLevel level = ctx.level();
            Block flower = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(NS + "yue_linglan"));
            for (int i = 0; i < 16; i++) {
                BlockPos pos = ctx.rel((i % 4) * 3, 1, (i / 4) * 3);
                level.setBlock(pos.below(), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(pos, flower.defaultBlockState(), Block.UPDATE_ALL);
            }
        });
        ctx.ticks(100);
        long nanos = ctx.get(() -> ctx.server().getAverageTickTimeNanos());
        ctx.record("averageTickMillis", nanos / 1_000_000.0);
        ctx.check(nanos).as("服务器平均每 tick 耗时（纳秒）").isLessThan(50_000_000L);
    }

    /** 结构里的女仆步高 1.6、只有本模组一个步高修饰符；其他生物步高不为 0。 */
    private static void stepHeight(Stage ctx, String structure) {
        StructureStage.Placed placed = ctx.get(() -> StructureStage.place(ctx, NS + structure));
        List<Entity> entities = ctx.get(() -> WorldExtract.entities(ctx.level(), placed));
        ctx.ticks(5);
        ctx.run(() -> {
            List<String> wrong = new ArrayList<>();
            int maids = 0;
            for (Entity entity : entities) {
                if (!(entity instanceof net.minecraft.world.entity.Mob living) || !living.getAttributes().hasAttribute(Attributes.STEP_HEIGHT)) {
                    continue;
                }
                String where = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()) + "@" + entity.blockPosition().toShortString();
                AttributeInstance step = living.getAttribute(Attributes.STEP_HEIGHT);
                if (entity instanceof EntityMaid) {
                    maids++;
                    List<ResourceLocation> ids = step.getModifiers().stream().map(AttributeModifier::id).toList();
                    if (Math.abs(step.getValue() - 1.6) > 1e-6 || !ids.equals(List.of(STEP_ID))) {
                        wrong.add(where + " 步高 " + step.getValue() + " 修饰符 " + ids);
                    }
                } else if (step.getValue() <= 0) {
                    wrong.add(where + " 步高为 0");
                }
            }
            ctx.check(maids).as(structure + " 里的女仆数").isGreaterThan(0);
            ctx.check(wrong).as("步高不对的生物").isEmpty();
        });
    }

    /** 逐个本模组模板，把保存出的 NBT 交给 visitor。 */
    private static void eachTemplate(Stage ctx, BiConsumer<ResourceLocation, CompoundTag> visitor) {
        StructureTemplateManager manager = ctx.level().getStructureManager();
        manager.listTemplates().filter(id -> id.getNamespace().equals(MaidSpellMod.MOD_ID)).sorted().forEach(id -> {
            StructureTemplate template = manager.get(id).orElse(null);
            if (template != null) {
                visitor.accept(id, template.save(new CompoundTag()));
            }
        });
    }

    private static void walk(Tag tag, BiConsumer<String, Tag> visitor, String key) {
        visitor.accept(key, tag);
        if (tag instanceof CompoundTag compound) {
            compound.getAllKeys().forEach(k -> walk(compound.get(k), visitor, k));
        } else if (tag instanceof ListTag list) {
            list.forEach(child -> walk(child, visitor, key));
        }
    }

    private static void banners(Stage ctx) {
        List<String> plain = new ArrayList<>();
        int[] seen = {0};
        eachTemplate(ctx, (id, nbt) -> {
            if (!id.getPath().contains("igloo") && !id.getPath().contains("savanna")) {
                return;
            }
            walk(nbt, (key, tag) -> {
                if (tag instanceof CompoundTag c && c.getString("id").endsWith("banner") ) {
                    seen[0]++;
                    if (c.getList("patterns", Tag.TAG_COMPOUND).isEmpty()) {
                        plain.add(id + " @" + c.getInt("x") + "," + c.getInt("y") + "," + c.getInt("z"));
                    }
                }
            }, "");
        });
        ctx.check(seen[0]).as("冰屋与热带草原模板里的旗帜数").isGreaterThan(0);
        ctx.check(plain).as("没有图案的旗帜").isEmpty();
    }

    private static void noChineseNames(Stage ctx) {
        List<String> literal = new ArrayList<>();
        eachTemplate(ctx, (id, nbt) -> walk(nbt, (key, tag) -> {
            if ((key.equals("CustomName") || key.equals("minecraft:custom_name") || key.equals("minecraft:item_name"))
                    && tag instanceof StringTag string) {
                String text = string.getAsString();
                if (CJK.matcher(text).find() && !text.contains("\"translate\"")) {
                    literal.add(id + " " + key + " = " + text);
                }
            }
        }, ""));
        ctx.check(literal).as("写死中文的名字").isEmpty();
    }

    private static JsonObject json(String path) {
        try (InputStream in = TemplateTest.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 分类图标是梦云水晶；三个条目的图标、配图存在；所有文本键两种语言都有。 */
    private static void patchouli(Stage ctx) {
        Map<String, String> en = Lang.read("en_us");
        Map<String, String> zh = Lang.read("zh_cn");
        List<String> problems = new ArrayList<>();
        JsonObject category = json(BOOK + "categories/wanfa_jietong.json");
        if (category == null) {
            ctx.fail("找不到分类 wanfa_jietong");
            return;
        }
        ctx.check(category.get("icon").getAsString()).as("分类图标").isEqualTo(NS + "dream_cat_crystal");
        List<String> keys = new ArrayList<>(List.of(category.get("name").getAsString(), category.get("description").getAsString()));
        Map<String, String> icons = Map.of("work_modes", NS + "blue_note", "baubles", NS + "flow_core", "structures", NS + "hairpin");
        icons.forEach((entry, icon) -> {
            JsonObject json = json(BOOK + "entries/wanfa_jietong/" + entry + ".json");
            if (json == null) {
                problems.add("缺条目 " + entry);
                return;
            }
            if (!icon.equals(json.get("icon").getAsString())) {
                problems.add(entry + " 图标是 " + json.get("icon").getAsString());
            }
            keys.add(json.get("name").getAsString());
            for (JsonElement page : json.getAsJsonArray("pages")) {
                JsonObject p = page.getAsJsonObject();
                if (p.has("text")) {
                    keys.add(p.get("text").getAsString());
                }
                JsonArray images = p.has("images") ? p.getAsJsonArray("images") : new JsonArray();
                for (JsonElement image : images) {
                    ResourceLocation rl = ResourceLocation.parse(image.getAsString());
                    if (TemplateTest.class.getResource("/assets/" + rl.getNamespace() + "/" + rl.getPath()) == null) {
                        problems.add(entry + " 配图不存在：" + rl);
                    }
                }
            }
        });
        keys.stream().filter(k -> !en.containsKey(k) || !zh.containsKey(k)).forEach(k -> problems.add("缺翻译 " + k));
        ctx.check(problems).as("手册分类的问题").isEmpty();
    }
}
