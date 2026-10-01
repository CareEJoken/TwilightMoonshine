package twilightmoonshine.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import twilightforest.world.components.structures.mushroomtower.MushroomTowerBridgeComponent;
import twilightforest.world.components.structures.mushroomtower.MushroomTowerWingComponent;
import twilightmoonshine.util.MushroomTowerDecor;
import twilightmoonshine.util.MushroomTowerDoors;

/**
 * 让走廊记住自己是从哪座塔伸出来的。
 * <p>
 * 桥的 {@code addChildren} 第一个参数 {@code parent} 就是源塔（TF 里
 * {@code makeBridge}/{@code makeMainBridge} 都是 {@code bridge.addChildren(this, ...)}），
 * 而桥的 {@code makeTowerWing} 里那个门对齐注入点拿不到它 ——
 * {@code adjustCoordinates} 的 LocalCapture 只有 direction/dx/wing。
 * 所以在这儿先存进 {@link MushroomTowerDoors.SourceWingHolder}，
 * 等门对齐真要用时（{@link MushroomTowerWingComponentMixin} /
 * {@link MushroomTowerMainBridgeComponentMixin}）再取。
 * <p>
 * 主桥 {@code MushroomTowerMainBridgeComponent} 没有覆写 {@code addChildren}，
 * 直接继承这份（已被本 mixin 改造过的）实现，一起生效。
 */
@Mixin(value = MushroomTowerBridgeComponent.class, remap = false)
public abstract class MushroomTowerBridgeComponentMixin {

	@Inject(method = "addChildren", at = @At("HEAD"))
	private void twilightmoonshine$captureSourceWing(StructurePiece parent, StructurePieceAccessor list, RandomSource rand, CallbackInfo ci) {
		if (parent instanceof MushroomTowerWingComponent source) {
			((MushroomTowerDoors.SourceWingHolder) (Object) this).twilightmoonshine$setSourceWing(source);
		}
	}

	/**
	 * 走廊路灯：两端各一盏（两座塔的门两侧）+ 中间每 3 格的栏杆柱头一盏萤火虫罐 ——
	 * 逻辑见 {@link MushroomTowerDecor#decorateBridge}。
	 * <p>
	 * 桥覆写了 {@code postProcess}（走道和栏杆由它自己铺），wing mixin 那边的房间装饰对桥
	 * 不生效，路灯只能在这边补。主桥 {@code MushroomTowerMainBridgeComponent} 没有覆写
	 * 这个方法的实现，直接继承这份（已被本 mixin 改造过的）一起生效。
	 */
	@Inject(method = "postProcess", at = @At("TAIL"))
	private void twilightmoonshine$decorateBridge(WorldGenLevel world, StructureManager manager, ChunkGenerator generator,
												  RandomSource rand, BoundingBox sbb, ChunkPos chunkPosIn, BlockPos blockPos,
												  CallbackInfo ci) {
		MushroomTowerDecor.decorateBridge((StructurePiece) (Object) this, world, sbb);
	}
}
