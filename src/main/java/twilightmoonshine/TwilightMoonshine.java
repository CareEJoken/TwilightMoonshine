package twilightmoonshine;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import twilightmoonshine.block.MoonRabbitTrophyBlock;
import twilightmoonshine.block.MoonRabbitTrophyWallBlock;
import twilightmoonshine.block.entity.MoonRabbitTrophyBlockEntity;
import twilightmoonshine.config.Config;
import twilightmoonshine.effect.TwilightEmbraceEffect;
import twilightmoonshine.entity.MoonRabbit;
import twilightmoonshine.item.MoonBellItem;
import twilightmoonshine.item.recipe.PotionArrowRecipe;
import twilightmoonshine.structure.ProceduralQuestIslandPiece;
import twilightmoonshine.structure.QuestIslandPiece;
import twilightmoonshine.structure.QuestIslandStructure;

@Mod(TwilightMoonshine.MODID)
public class TwilightMoonshine {

    public static final String MODID = "twilightmoonshine";
    public static final Logger LOGGER = LogUtils.getLogger();

    // --- Map decorations ---
    public static final DeferredRegister<MapDecorationType> MAP_DECORATIONS =
        DeferredRegister.create(BuiltInRegistries.MAP_DECORATION_TYPE, MODID);

    public static final DeferredHolder<MapDecorationType, MapDecorationType> MUSHROOM_TOWER =
        MAP_DECORATIONS.register("mushroom_tower", () -> new MapDecorationType(
            ResourceLocation.fromNamespaceAndPath(MODID, "map/decorations/mushroom_tower"),
            true, -1, false, true
        ));

    // --- Structure types ---
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
        DeferredRegister.create(Registries.STRUCTURE_TYPE, MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<QuestIslandStructure>> QUEST_ISLAND_TYPE =
        STRUCTURE_TYPES.register("quest_island", () -> () -> QuestIslandStructure.CODEC);

    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECE_TYPES =
        DeferredRegister.create(Registries.STRUCTURE_PIECE, MODID);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> QUEST_ISLAND_PIECE =
        STRUCTURE_PIECE_TYPES.register("quest_island_piece", () -> ProceduralQuestIslandPiece::new);

    // --- Entity types ---
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(Registries.ENTITY_TYPE, MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<MoonRabbit>> MOON_RABBIT =
        ENTITY_TYPES.register("moon_rabbit", () -> EntityType.Builder
            .of(MoonRabbit::new, MobCategory.CREATURE)
            .sized(0.8F, 1.0F)
            .clientTrackingRange(8)
            .build("moon_rabbit"));

    // --- Blocks ---
    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(Registries.BLOCK, MODID);

    public static final DeferredHolder<Block, MoonRabbitTrophyBlock> MOON_RABBIT_TROPHY_BLOCK =
        BLOCKS.register("moon_rabbit_trophy", () -> new MoonRabbitTrophyBlock(BlockBehaviour.Properties.of().instabreak()));

    public static final DeferredHolder<Block, MoonRabbitTrophyWallBlock> MOON_RABBIT_WALL_TROPHY_BLOCK =
        BLOCKS.register("moon_rabbit_wall_trophy", () -> new MoonRabbitTrophyWallBlock(BlockBehaviour.Properties.of().instabreak()));

    // --- Moon stone family（月石碎片熔合的建筑方块，材质为占位图，后续替换）---
    public static final DeferredHolder<Block, Block> MOON_STONE =
        BLOCKS.register("moon_stone", () -> new Block(BlockBehaviour.Properties.of()
            .strength(1.5F, 6.0F)
            .requiresCorrectToolForDrops()));

    public static final DeferredHolder<Block, StairBlock> MOON_STONE_STAIRS =
        BLOCKS.register("moon_stone_stairs", () -> new StairBlock(MOON_STONE.get().defaultBlockState(),
            BlockBehaviour.Properties.of().strength(1.5F, 6.0F).requiresCorrectToolForDrops()));

    public static final DeferredHolder<Block, SlabBlock> MOON_STONE_SLAB =
        BLOCKS.register("moon_stone_slab", () -> new SlabBlock(BlockBehaviour.Properties.of()
            .strength(1.5F, 6.0F).requiresCorrectToolForDrops()));

    public static final DeferredHolder<Block, WallBlock> MOON_STONE_WALL =
        BLOCKS.register("moon_stone_wall", () -> new WallBlock(BlockBehaviour.Properties.of()
            .strength(1.5F, 6.0F).requiresCorrectToolForDrops()));

    // --- Block entities ---
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MoonRabbitTrophyBlockEntity>> MOON_RABBIT_TROPHY_BLOCK_ENTITY =
        BLOCK_ENTITIES.register("moon_rabbit_trophy", () -> BlockEntityType.Builder
            .of(MoonRabbitTrophyBlockEntity::new, MOON_RABBIT_TROPHY_BLOCK.get(), MOON_RABBIT_WALL_TROPHY_BLOCK.get())
            .build(null));

    // --- Items ---
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(Registries.ITEM, MODID);

    public static final DeferredHolder<Item, Item> MOON_STONE_SHARD =
        ITEMS.register("moon_stone_shard", () -> new Item(new Item.Properties()));

    public static final DeferredHolder<Item, Item> GLOW_ESSENCE =
        ITEMS.register("glow_essence", () -> new Item(new Item.Properties()));

    public static final DeferredHolder<Item, Item> TWILIGHT_PLANT_EXTRACT =
        ITEMS.register("twilight_plant_extract", () -> new Item(new Item.Properties()));

    // 月之铃：右键切换暮色森林"月光"（月亮出现 + 天光提升）
    public static final DeferredHolder<Item, Item> MOON_BELL =
        ITEMS.register("moon_bell", () -> new MoonBellItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, StandingAndWallBlockItem> MOON_RABBIT_TROPHY =
        ITEMS.register("moon_rabbit_trophy", () -> new StandingAndWallBlockItem(
            MOON_RABBIT_TROPHY_BLOCK.get(), MOON_RABBIT_WALL_TROPHY_BLOCK.get(),
            new Item.Properties(), Direction.DOWN));

    // --- Moon stone family items ---
    public static final DeferredHolder<Item, BlockItem> MOON_STONE_ITEM =
        ITEMS.register("moon_stone", () -> new BlockItem(MOON_STONE.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> MOON_STONE_STAIRS_ITEM =
        ITEMS.register("moon_stone_stairs", () -> new BlockItem(MOON_STONE_STAIRS.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> MOON_STONE_SLAB_ITEM =
        ITEMS.register("moon_stone_slab", () -> new BlockItem(MOON_STONE_SLAB.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> MOON_STONE_WALL_ITEM =
        ITEMS.register("moon_stone_wall", () -> new BlockItem(MOON_STONE_WALL.get(), new Item.Properties()));

    // --- Spawn eggs ---
    public static final DeferredRegister<Item> SPAWN_EGGS =
        DeferredRegister.create(Registries.ITEM, MODID);

    public static final DeferredHolder<Item, SpawnEggItem> MOON_RABBIT_SPAWN_EGG =
        SPAWN_EGGS.register("moon_rabbit_spawn_egg", () -> new SpawnEggItem(
            MOON_RABBIT.value(), 0xFFFFFF, 0x43364E, new Item.Properties()));

    // --- Mob effects ---
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
        DeferredRegister.create(Registries.MOB_EFFECT, MODID);

    // 暮色之拥：期间暮色森林友好生物跟随、敌对生物友好（行为见 TwilightEmbraceEvents）
    public static final DeferredHolder<MobEffect, MobEffect> TWILIGHT_EMBRACE =
        MOB_EFFECTS.register("twilight_embrace", () -> new TwilightEmbraceEffect(MobEffectCategory.BENEFICIAL, 0x7BBF4C));

    // --- Potions ---
    public static final DeferredRegister<Potion> POTIONS =
        DeferredRegister.create(Registries.POTION, MODID);

    // 抗性药水：酿造配方已移除，物品保留
    public static final DeferredHolder<Potion, Potion> RESISTANCE_POTION =
        POTIONS.register("resistance", () -> new Potion(
            new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 3600)));

    public static final DeferredHolder<Potion, Potion> LONG_RESISTANCE_POTION =
        POTIONS.register("long_resistance", () -> new Potion(
            new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 9600)));

    public static final DeferredHolder<Potion, Potion> STRONG_RESISTANCE_POTION =
        POTIONS.register("strong_resistance", () -> new Potion(
            new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 1800, 1)));

    // 微微发亮的药水 — 无效果基底，类似平凡的药水
    public static final DeferredHolder<Potion, Potion> FAINTLY_GLOWING_POTION =
        POTIONS.register("faintly_glowing", () -> new Potion());

    // 荧光药水 — 发光效果（无加强版）
    public static final DeferredHolder<Potion, Potion> GLOW_POTION =
        POTIONS.register("glow", () -> new Potion(
            new MobEffectInstance(MobEffects.GLOWING, 3600)));

    public static final DeferredHolder<Potion, Potion> LONG_GLOW_POTION =
        POTIONS.register("long_glow", () -> new Potion(
            new MobEffectInstance(MobEffects.GLOWING, 9600)));

    // 暮色药水 — 暮色之拥效果，只有一个等级（无加强版）
    public static final DeferredHolder<Potion, Potion> TWILIGHT_POTION =
        POTIONS.register("twilight", () -> new Potion(
            new MobEffectInstance(TWILIGHT_EMBRACE, 3600)));

    public static final DeferredHolder<Potion, Potion> LONG_TWILIGHT_POTION =
        POTIONS.register("long_twilight", () -> new Potion(
            new MobEffectInstance(TWILIGHT_EMBRACE, 9600)));

    // --- Recipe serializers ---
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
        DeferredRegister.create(Registries.RECIPE_SERIALIZER, MODID);

    // 药水 + 8 箭 → 8 特殊箭（光灵箭 / 暮色之箭）
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<PotionArrowRecipe>> POTION_ARROW_SERIALIZER =
        RECIPE_SERIALIZERS.register("potion_arrow", PotionArrowRecipe.Serializer::new);

    // --- Creative tab ---
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MOONSHINE_TAB =
        CREATIVE_TABS.register("moonshine", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.twilightmoonshine"))
            .icon(() -> new ItemStack(MOON_RABBIT_TROPHY.get()))
            .build());

    // --- Sounds（字幕显示"月兔：XXX"）---
    public static final DeferredRegister<SoundEvent> SOUNDS =
        DeferredRegister.create(Registries.SOUND_EVENT, MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> MOON_RABBIT_AMBIENT =
        SOUNDS.register("moon_rabbit.ambient", () -> SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(MODID, "moon_rabbit.ambient")));

    public static final DeferredHolder<SoundEvent, SoundEvent> MOON_RABBIT_HURT =
        SOUNDS.register("moon_rabbit.hurt", () -> SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(MODID, "moon_rabbit.hurt")));

    public static final DeferredHolder<SoundEvent, SoundEvent> MOON_RABBIT_DEATH =
        SOUNDS.register("moon_rabbit.death", () -> SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(MODID, "moon_rabbit.death")));

    public static final DeferredHolder<SoundEvent, SoundEvent> MOON_RABBIT_JUMP =
        SOUNDS.register("moon_rabbit.jump", () -> SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(MODID, "moon_rabbit.jump")));

    // 喷嚏前兆：复用原版熊猫吸气音频（准备打喷嚏阶段播放）
    public static final DeferredHolder<SoundEvent, SoundEvent> MOON_RABBIT_PRE_SNEEZE =
        SOUNDS.register("moon_rabbit.pre_sneeze", () -> SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(MODID, "moon_rabbit.pre_sneeze")));

    // 打喷嚏：复用原版熊猫打喷嚏音频，仅在喷出物品时播放，字幕显示"月兔：打喷嚏"
    public static final DeferredHolder<SoundEvent, SoundEvent> MOON_RABBIT_SNEEZE =
        SOUNDS.register("moon_rabbit.sneeze", () -> SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(MODID, "moon_rabbit.sneeze")));

    public TwilightMoonshine(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        MAP_DECORATIONS.register(modEventBus);
        STRUCTURE_TYPES.register(modEventBus);
        STRUCTURE_PIECE_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        ITEMS.register(modEventBus);
        SPAWN_EGGS.register(modEventBus);
        POTIONS.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        SOUNDS.register(modEventBus);
    }
}
