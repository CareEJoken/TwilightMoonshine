package twilightmoonshine.mixin;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import twilightforest.world.components.structures.mushroomtower.MushroomTowerMainBridgeComponent;
import twilightforest.world.components.structures.mushroomtower.MushroomTowerMainComponent;
import twilightmoonshine.util.MushroomTowerDoors;

/**
 * 主塔回连走廊那条链的门对齐 —— "上行者塔"顶层再连回一座新主塔时走的是
 * {@code MushroomTowerMainBridgeComponent} 自己覆写的 {@code makeTowerWing}，
 * {@link MushroomTowerWingComponentMixin} 里注入基类那份管不到它，所以单开一份。
 * 注入点和算法与那边完全一致，详见 {@link MushroomTowerDoors#alignEntryDoor}。
 */
@Mixin(value = MushroomTowerMainBridgeComponent.class, remap = false)
public abstract class MushroomTowerMainBridgeComponentMixin {

	@Inject(method = "makeTowerWing", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
		target = "Ltwilightforest/world/components/structures/mushroomtower/MushroomTowerMainBridgeComponent;addOpening(IIILnet/minecraft/world/level/block/Rotation;)V"),
		locals = LocalCapture.CAPTURE_FAILHARD)
	private void twilightmoonshine$alignEntryDoor(StructurePieceAccessor list, RandomSource rand, int index, int x, int y, int z,
												  int wingSize, int wingHeight, Rotation rotation, CallbackInfoReturnable<Boolean> cir,
												  Direction direction, int[] dx, MushroomTowerMainComponent wing) {
		MushroomTowerDoors.alignEntryDoor(((StructurePiece) (Object) this).getBoundingBox(), wing, direction);
	}
}
