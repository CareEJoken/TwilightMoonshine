package twilightmoonshine.mixin;

import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import twilightforest.world.components.structures.lichtower.TowerWingComponent;

import java.util.ArrayList;

/**
 * 暴露 {@link TowerWingComponent} 的成员（protected 或没有 getter 的），
 * 给蘑菇塔的走廊门对齐 / 梯子逻辑用。
 * <p>
 * 为什么是 accessor 而不是在别处 {@code @Shadow}：Mixin 的
 * {@code TargetClassContext.findAliasedField/Method} 只扫目标类自己声明的
 * fields/methods，父类一概不查 —— 所以 `size`/`height`/`openings` 这类
 * 声明在本类里的成员，必须在「以本类为 mixin 目标」的接口里才取得到。
 * 蘑菇塔的 wing 继承自本类，运行时天然实现这个接口。
 */
@Mixin(value = TowerWingComponent.class, remap = false)
public interface TowerWingComponentAccessor {

	// TowerWingComponent#openings 是 protected ArrayList<BlockPos>
	@Accessor("openings")
	ArrayList<BlockPos> twilightmoonshine$getOpenings();

	// 塔的直径、高度（public int size / protected int height）
	@Accessor("size")
	int twilightmoonshine$getSize();

	@Accessor("height")
	int twilightmoonshine$getHeight();
}
