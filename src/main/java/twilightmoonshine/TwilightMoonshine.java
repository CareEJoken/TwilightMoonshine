package twilightmoonshine;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import twilightmoonshine.entity.MoonRabbit;
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

    // --- Entity types ---
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(Registries.ENTITY_TYPE, MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<MoonRabbit>> MOON_RABBIT =
        ENTITY_TYPES.register("moon_rabbit", () -> EntityType.Builder
            .of(MoonRabbit::new, MobCategory.CREATURE)
            .sized(0.8F, 1.0F)
            .clientTrackingRange(8)
            .build("moon_rabbit"));

    // --- Spawn eggs ---
    public static final DeferredRegister<Item> SPAWN_EGGS =
        DeferredRegister.create(Registries.ITEM, MODID);

    public static final DeferredHolder<Item, SpawnEggItem> MOON_RABBIT_SPAWN_EGG =
        SPAWN_EGGS.register("moon_rabbit_spawn_egg", () -> new SpawnEggItem(
            MOON_RABBIT.value(), 0xFFFFFF, 0x43364E, new Item.Properties()));

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

    public TwilightMoonshine(IEventBus modEventBus) {
        MAP_DECORATIONS.register(modEventBus);
        STRUCTURE_TYPES.register(modEventBus);
        STRUCTURE_PIECE_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        SPAWN_EGGS.register(modEventBus);
        SOUNDS.register(modEventBus);
    }
}
