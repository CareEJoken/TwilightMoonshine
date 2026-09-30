package twilightmoonshine.mixin;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import twilightforest.world.components.structures.mushroomtower.MushroomTowerMainComponent;
import twilightmoonshine.util.MushroomTowerDoors;

/**
 * 主塔版的门洞位移收尾 —— 见 {@link MushroomTowerWingComponentMixin} 里那段
 * {@code twilightmoonshine$applyPendingShifts} 的说明。
 * <p>
 * {@code MushroomTowerMainComponent} 覆写了 {@code addChildren} 且不调 super，
 * 基类那份 TAIL 注入对主塔不生效；而主塔自己也会用 {@code makeBridge} 伸出走廊
 * （{@code makeAscenderTower} 和四个方向的支塔），照样会排门洞位移，所以得补一份。
 */
@Mixin(value = MushroomTowerMainComponent.class, remap = false)
public abstract class MushroomTowerMainComponentMixin {

	@Inject(method = "addChildren", at = @At("TAIL"))
	private void twilightmoonshine$applyPendingShifts(StructurePiece parent, StructurePieceAccessor list, RandomSource rand, CallbackInfo ci) {
		((MushroomTowerDoors.SourceWingHolder) (Object) this).twilightmoonshine$flushOpeningShifts();
	}
}
