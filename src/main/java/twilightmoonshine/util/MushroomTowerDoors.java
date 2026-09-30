package twilightmoonshine.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import twilightforest.world.components.structures.mushroomtower.MushroomTowerWingComponent;
import twilightmoonshine.TwilightMoonshine;
import twilightmoonshine.mixin.TowerWingComponentAccessor;

import java.util.ArrayList;

/**
 * 把"走廊尽头的塔门"挪到走廊中线上。
 * <p>
 * TF 的生成顺序是先按源塔门洞的位置铺一条走廊（走廊永远正对源塔的门洞，
 * 见 {@code TFStructureComponentOld#offsetTowerCoords}），目标塔则在走廊之后才建，
 * 而且会被 {@code MushroomTowerWingComponent#adjustCoordinates} 吸附到附近一座
 * 同尺寸塔的脚印上（塔摞塔）。目标塔自己的入口门是写死在墙面正中的
 * （{@code addChildren} 里的 {@code addOpening(0, 1, size / 2, ...)}），一被吸附就
 * 整体横移，于是门和走廊错开 —— 玩家看到的"门是歪的"。
 * <p>
 * 这里在目标塔建好（吸附已完成）时把入口门挪到走廊所在的那一格上。塔的横截面是
 * "类八边形"（{@code makeTrunk} 的距离场），正对走廊的平墙只有正中 ±2 格，
 * 所以错位超过 2 格时只挪到边缘并打日志，再远就不是挪门能救的了。
 */
public final class MushroomTowerDoors {

	/**
	 * @param corridorBox 走廊（桥）piece 的包围盒
	 * @param wing        刚建好、已吸附完毕的目标塔
	 * @param label       塔/桥的朝向标签（结构局部 +x 指向的世界方向）
	 */
	public static void alignEntryDoor(BoundingBox corridorBox, MushroomTowerWingComponent wing, Direction label) {
		BoundingBox wingBox = wing.getBoundingBox();

		// 走道铺在桥局部 z=1 那一行：先取它的世界坐标，再换算成目标塔的局部 z
		int localZ;
		switch (label) {
			case SOUTH -> localZ = corridorBox.minZ() + 1 - wingBox.minZ();
			case NORTH -> localZ = wingBox.maxZ() - corridorBox.maxZ() + 1;
			case WEST -> localZ = wingBox.maxX() - corridorBox.maxX() + 1;
			case EAST -> localZ = corridorBox.minX() + 1 - wingBox.minX();
			default -> { return; }
		}

		int center = wing.size / 2;
		int targetZ = Mth.clamp(localZ, center - 2, center + 2);
		if (targetZ == center) return; // 没被吸附过（或刚好对中），不用动

		ArrayList<BlockPos> openings = ((TowerWingComponentAccessor) wing).twilightmoonshine$getOpenings();
		for (int i = 0; i < openings.size(); i++) {
			BlockPos door = openings.get(i);
			if (door.getX() == 0 && door.getY() == 1) { // 入口门是 (0, 1, size/2)，其余门洞都在 y>=5
				openings.set(i, new BlockPos(0, 1, targetZ));
				TwilightMoonshine.LOGGER.info("Mushroom tower entry door aligned to corridor: size {}, local z {} -> {} (corridor lane {})",
					wing.size, center, targetZ, localZ);
				return;
			}
		}
	}

	private MushroomTowerDoors() {}
}
