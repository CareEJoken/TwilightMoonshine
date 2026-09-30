package twilightmoonshine.mixin;

import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import twilightforest.world.components.structures.TFStructureComponentOld;

/**
 * 暴露 {@link TFStructureComponentOld} 的 protected {@code placeBlock}。
 * <p>
 * 这里必须用「以声明类为目标」的接口 mixin，不能写成 {@code @Shadow}：
 * Mixin 的 {@code TargetClassContext.findAliasedMethod} 只扫目标类自己声明的
 * {@code classNode.methods}（父类一概不查，接口也算），所以在蘑菇塔那个 mixin 里
 * {@code @Shadow placeBlock} 永远解析不到 —— 启动即崩。
 * 又以 {@code TFStructureComponentOld} 为目标时 {@code placeBlock} 就在类里，稳。
 * <p>
 * 另一个不复刻一段等价代码的理由：TF 的 {@code placeBlock} 会做
 * 包围盒裁剪 + 按 {@code this.rotation}/{@code mirror} 旋转方块状态
 * （见 {@code TFStructureComponent.placeBlock}），照抄容易抄错朝向。
 */
@Mixin(value = TFStructureComponentOld.class, remap = false)
public interface TFStructureComponentOldAccessor {

	@Invoker("placeBlock")
	void twilightmoonshine$placeBlock(WorldGenLevel world, BlockState state, int x, int y, int z, BoundingBox sbb);

	// 结构局部坐标 → 世界坐标（protected，在 TFStructureComponentOld 里按 this.rotation/朝向解算）
	@Invoker("getWorldX")
	int twilightmoonshine$getWorldX(int x, int z);

	@Invoker("getWorldZ")
	int twilightmoonshine$getWorldZ(int x, int z);
}
