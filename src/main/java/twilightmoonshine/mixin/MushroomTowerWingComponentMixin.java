package twilightmoonshine.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import twilightforest.world.components.structures.mushroomtower.MushroomTowerWingComponent;
import twilightmoonshine.util.MushroomTowerDoors;

/**
 * 给蘑菇塔的内腔加一条贯通的梯子，顺手把沿途的楼板打穿。
 * <p>
 * TF 原版的蘑菇塔只靠楼板一层层堆起来（{@code makeFloorsForTower} 每 4 格铺一层实心圆盘），
 * 房间里既没有楼梯也没有梯子，玩家只能徒手挖穿丛林木板的"上行者"楼层才能往上走。
 * 这里在 {@code postProcess} 收尾时补一条贴墙的梯子列，它顶掉沿途的楼板格，
 * 于是每层楼板都留出一个由梯子填着的 1x1 口子 —— 梯子本身没有碰撞箱，
 * 站在旁边看就是一个洞，贴着它就能从底层一路爬到顶层。
 * <p>
 * 只在塔身生效：{@link MushroomTowerWingComponent#postProcess} 被子类
 * {@code MushroomTowerBridgeComponent} 覆写（桥有自己的走道），但
 * {@code MushroomTowerMainComponent} 没有覆写，所以主塔和普通支塔都会走到这里。
 */
@Mixin(value = MushroomTowerWingComponent.class, remap = false)
public abstract class MushroomTowerWingComponentMixin {

	/**
	 * 塔的直径/高度用的 {@code size}/{@code height} 和摆方块的 {@code placeBlock}
	 * 都**不是**本类声明的成员（分别在 {@code TowerWingComponent} 和
	 * {@code TFStructureComponentOld} 里），而 Mixin 的 {@code @Shadow} 只解析
	 * 目标类自己声明的成员 —— 写 {@code @Shadow} 会直接在启动时抛
	 * "was not located in the target class"。所以统一走声明类上的接口 mixin：
	 * {@link TowerWingComponentAccessor}（size/height）和
	 * {@link TFStructureComponentOldAccessor}（placeBlock，顺带保留 TF 的
	 * 包围盒裁剪 + 按 rotation 旋转方块状态的语义 —— 传进去的朝向要写
	 * "结构局部坐标系"里的值，详见下面的推导）。
	 */
	private int twilightmoonshine$size() {
		return ((TowerWingComponentAccessor) (Object) this).twilightmoonshine$getSize();
	}

	private int twilightmoonshine$height() {
		return ((TowerWingComponentAccessor) (Object) this).twilightmoonshine$getHeight();
	}

	private void twilightmoonshine$placeBlock(WorldGenLevel world, BlockState state, int x, int y, int z, BoundingBox sbb) {
		((TFStructureComponentOldAccessor) (Object) this).twilightmoonshine$placeBlock(world, state, x, y, z, sbb);
	}

	@Inject(method = "postProcess", at = @At("TAIL"))
	private void twilightmoonshine$carveLadderShaft(WorldGenLevel world, StructureManager manager, ChunkGenerator generator,
												   RandomSource rand, BoundingBox sbb, ChunkPos chunkPosIn, BlockPos blockPos,
												   CallbackInfo ci) {
		int size = this.twilightmoonshine$size();
		int height = this.twilightmoonshine$height();
		if (size < 3 || height < 2) return;

		// makeTrunk/placeFloor 用的那套"类八边形"距离场：外墙是 dist > hollow 的那一圈
		int diameter = size / 2;
		int hollow = (int) (diameter * 0.8);

		// 沿 +z 让开 2 格，避开所有门洞所在的墙带（getValidOpening 的门开在
		// ±x 墙的 z∈[(size-3)/2, (size+1)/2] 段上：size15→z∈[6,8]，size11→[4,6]，
		// size7→[2,3]，size3→{1}）。size 3 的塔太小，只能退到中轴线上（zOff=0）。
		int zOff = Math.min(2, diameter - 1);
		int ladderZ = diameter + zOff;

		// 从中心往外扫，取这一行里最靠外墙的"空心"格 —— 它 +x 方向的邻居必定是外墙，
		// 正好给梯子当背板。（尺寸 15/11/7/3 分别落在 x=12/9/5/1）
		int ladderX = -1;
		for (int d = diameter; d >= 0; d--) {
			int dist = (int) (Math.max(d, zOff) + Math.min(d, zOff) * 0.4);
			if (dist <= hollow) {
				ladderX = diameter + d;
				break;
			}
		}
		if (ladderX < 0 || ladderX + 1 > size - 1) return;

		// 背板在局部坐标的 +x 侧。TF 的 placeBlock 会拿 this.rotation 再转一次状态，
		// 四个朝向（SOUTH/WEST/NORTH/EAST）逐一验算下来，写"背板所在的局部方向"即
		// EAST 就能得到正确的世界朝向，跟暗黑塔里 placeBlock(LADDER, FACING=WEST) +
		// 背板在 -x 的写法是同一个约定。
		BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);

		// 从基座上表面一直铺到天花板下面；中间撞上楼板的格子会被梯子顶掉，自然形成开洞。
		for (int dy = 1; dy < height; dy++) {
			this.twilightmoonshine$placeBlock(world, ladder, ladderX, dy, ladderZ, sbb);
		}
	}

	/**
	 * 修正"上行者塔"（isAscender）顶层回连新主塔的走廊开口高度 —— 也就是那种"有时候门是歪的"。
	 * <p>
	 * {@code addChildren} 把开口写死成 {@code dest[1] = this.height - 3}。但楼板圆盘铺在
	 * 0、4、8…（{@code makeFloorsForTower}），顶层圆盘是 {@code 4*(height/4 - 1)}，
	 * 站立面比它高一格 —— 所以只有塔高 ≡ 0 (mod 4) 时 {@code height - 3} 才正好是顶层地面。
	 * 出生主塔高度是 8/12/16（≡0 mod 4），它造出的上行者塔高度也 ≡0，写死的值恰好正确；
	 * 但经回连走廊生成的新主塔高度是 {@code (2..6)*4+1}（≡1 mod 4），
	 * 它再造上行者塔时高度也 ≡1 mod 4，顶层站立面是 {@code height-4}，
	 * 门洞却开在 {@code height-3}：门洞、走廊、新主塔整体比顶层地面高一格，
	 * 玩家爬到顶层会发现门口悬在头顶，得跳一格才进得了走廊。
	 * <p>
	 * 这里把传给 {@code makeMainBridge} 的 y 换成真正的顶层站立面。4k 高度下算出的值
	 * 与原来的 {@code height-3} 完全相同，所以只会改变原本错位的那批门。
	 * FLOOR_HEIGHT 固定是 4，直接写 4。
	 */
	@ModifyArg(
		method = "addChildren",
		at = @At(value = "INVOKE", target = "Ltwilightforest/world/components/structures/mushroomtower/MushroomTowerWingComponent;makeMainBridge(Lnet/minecraft/world/level/levelgen/structure/StructurePieceAccessor;Lnet/minecraft/util/RandomSource;IIIIILnet/minecraft/world/level/block/Rotation;)Z"),
		index = 4)
	private int twilightmoonshine$alignAscenderTopDoor(int y) {
		int height = this.twilightmoonshine$height();
		// 自检：只有拿到 TF 写的 height-3 才重算，其余情况原样返回（x 恒为 0，不会撞上这个值）
		if (y != height - 3) return y;
		return (height / 4 - 1) * 4 + 1;
	}

	/**
	 * 走廊尽头的门对齐（横向错位）—— 逻辑见 {@link MushroomTowerDoors#alignEntryDoor}。
	 * <p>
	 * 注入点选在 {@code makeTowerWing} 末尾那次 {@code addOpening}（桥自己朝目标塔开的口）之后，
	 * 此时 {@code wing.addChildren} 已经跑完、目标塔的入口门已经写进 {@code openings}。
	 * 基类这份 {@code makeTowerWing} 全 TF 只有 {@code MushroomTowerBridgeComponent#addChildren} 会调，
	 * 所以 {@code this} 一定是桥，它的包围盒就是走廊的包围盒；主塔回连走廊走的是
	 * {@code MushroomTowerMainBridgeComponent} 自己覆写的那份，由
	 * {@link MushroomTowerMainBridgeComponentMixin} 单独处理。
	 */
	@Inject(method = "makeTowerWing", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
		target = "Ltwilightforest/world/components/structures/mushroomtower/MushroomTowerWingComponent;addOpening(IIILnet/minecraft/world/level/block/Rotation;)V"),
		locals = LocalCapture.CAPTURE_FAILHARD)
	private void twilightmoonshine$alignEntryDoor(StructurePieceAccessor list, RandomSource rand, int index, int x, int y, int z,
												  int wingSize, int wingHeight, Rotation rotation, CallbackInfoReturnable<Boolean> cir,
												  Direction direction, int[] dx, MushroomTowerWingComponent wing) {
		MushroomTowerDoors.alignEntryDoor(((StructurePiece) (Object) this).getBoundingBox(), wing, direction);
	}
}
