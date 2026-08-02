package twilightmoonshine;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.common.SoundActions;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import twilightmoonshine.fluid.MoonSpringFluid;
import twilightmoonshine.structure.QuestIslandPiece;
import twilightmoonshine.structure.QuestIslandStructure;

@Mod(TwilightMoonshine.MODID)
public class TwilightMoonshine {

    public static final String MODID = "twilightmoonshine";

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
        STRUCTURE_PIECE_TYPES.register("quest_island_piece", () -> QuestIslandPiece::new);

    // --- Fluid type ---
    public static final DeferredRegister<FluidType> FLUID_TYPES =
        DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, MODID);

    public static final DeferredHolder<FluidType, FluidType> MOON_SPRING_TYPE =
        FLUID_TYPES.register("moon_spring", () -> new FluidType(FluidType.Properties.create()
            .descriptionId("block.twilightmoonshine.moon_spring")
            .fallDistanceModifier(0F)
            .canExtinguish(true)
            .canHydrate(true)
            .canConvertToSource(true)
            .supportsBoating(true)
            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)
            .sound(SoundActions.FLUID_VAPORIZE, SoundEvents.FIRE_EXTINGUISH)));

    // --- Fluids ---
    public static final DeferredRegister<Fluid> FLUIDS =
        DeferredRegister.create(Registries.FLUID, MODID);

    public static final DeferredHolder<Fluid, BaseFlowingFluid> MOON_SPRING_SOURCE =
        FLUIDS.register("moon_spring", () -> new BaseFlowingFluid.Source(MoonSpringFluid.createProperties()));

    public static final DeferredHolder<Fluid, BaseFlowingFluid> MOON_SPRING_FLOWING =
        FLUIDS.register("flowing_moon_spring", () -> new BaseFlowingFluid.Flowing(MoonSpringFluid.createProperties()));

    // --- Blocks ---
    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(Registries.BLOCK, MODID);

    public static final DeferredHolder<Block, LiquidBlock> MOON_SPRING_BLOCK =
        BLOCKS.register("moon_spring", () -> new LiquidBlock(MOON_SPRING_SOURCE.value(),
            BlockBehaviour.Properties.ofFullCopy(Blocks.WATER)));

    // --- Items ---
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(Registries.ITEM, MODID);

    public static final DeferredHolder<Item, BucketItem> MOON_SPRING_BUCKET =
        ITEMS.register("moon_spring_bucket", () -> new BucketItem(MOON_SPRING_SOURCE.value(),
            new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    public TwilightMoonshine(IEventBus modEventBus) {
        MAP_DECORATIONS.register(modEventBus);
        STRUCTURE_TYPES.register(modEventBus);
        STRUCTURE_PIECE_TYPES.register(modEventBus);
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}
