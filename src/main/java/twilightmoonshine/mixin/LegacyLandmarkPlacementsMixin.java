package twilightmoonshine.mixin;

import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import twilightforest.init.TFBiomes;
import twilightforest.init.TFStructures;
import twilightforest.util.landmarks.LegacyLandmarkPlacements;

import java.util.Map;

@Mixin(value = LegacyLandmarkPlacements.class, remap = false)
public class LegacyLandmarkPlacementsMixin {

    @Shadow @Mutable
    private static Map<ResourceKey<Biome>, ResourceKey<Structure>> BIOME_2_STRUCTURES;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void addMushroomTower(CallbackInfo ci) {
        if (BIOME_2_STRUCTURES.containsKey(TFBiomes.DENSE_MUSHROOM_FOREST)) return;
        BIOME_2_STRUCTURES = ImmutableMap.<ResourceKey<Biome>, ResourceKey<Structure>>builder()
            .putAll(BIOME_2_STRUCTURES)
            .put(TFBiomes.DENSE_MUSHROOM_FOREST, TFStructures.MUSHROOM_TOWER)
            .buildOrThrow();
    }
}
