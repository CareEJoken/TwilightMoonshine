package twilightmoonshine.mixin;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import twilightforest.world.components.structures.mushroomtower.MushroomTowerBridgeComponent;
import twilightforest.world.components.structures.mushroomtower.MushroomTowerWingComponent;
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
}
