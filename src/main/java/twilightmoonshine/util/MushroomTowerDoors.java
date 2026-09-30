package twilightmoonshine.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import twilightforest.world.components.structures.mushroomtower.MushroomTowerWingComponent;
import twilightmoonshine.TwilightMoonshine;
import twilightmoonshine.mixin.TFStructureComponentOldAccessor;
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
 * 整体横移 δ，于是门和走廊错开 —— 玩家看到的"门是歪的"。
 * <p>
 * 正对走廊的那面平墙宽 5 格（塔的横截面是 {@code makeTrunk} 的距离场，"类八边形"
 * 的正面正好是墙心 ±2），门落在最外侧那格（墙心 ±2）时贴着墙角，很难看。
 * <p>
 * 所以这里分两步：
 * <ol>
 *   <li>错位正好是 ±2 时，把**整条桥**连同**两端的门洞**横移一格（塔一动不动）：
 *       目标塔的门从墙心 ±2 回到 ±1，桥依旧笔直；</li>
 *   <li>剩下的零头（±1，或者没法挪时的兜底）只挪门洞，门夹在墙心 ±2 以内。</li>
 * </ol>
 * 门洞条目会经 {@code doorInts} 序列化 → 建期改动会持久化，存档重载不回退。
 */
public final class MushroomTowerDoors {

	/** {@link #laneLocalZ} 认不出朝向时的返回值（四个水平方向之外，实际不会出现）。 */
	private static final int INVALID_LANE = Integer.MIN_VALUE;

	/**
	 * 「桥已经横移一格了，源塔那侧的门洞也得跟着挪」的待办。
	 * <p>
	 * 下这份待办时源塔还没把这条走廊的门洞写进 {@code openings}
	 * （{@code makeBridge} 里是 {@code bridge.addChildren(...)} 先跑、{@code addOpening(...)} 后加），
	 * 所以只能先记下"它将会在哪"，等源塔 {@code addChildren} 收尾时再改。
	 *
	 * @param openX/openY/openZ 源塔门洞（移动前）的世界坐标，用来在 openings 里认人
	 * @param localX/localZ     源塔局部坐标下要挪的量
	 */
	public record PendingShift(int openX, int openY, int openZ, int localX, int localZ) {}

	/**
	 * 由 wing mixin 实现在 {@code MushroomTowerWingComponent} 上 —— 塔、主塔、桥都是它的子类，
	 * 运行时都带这份字段/方法。桥用 set 记住"自己是从哪座塔伸出来的"，塔在 {@code addChildren}
	 * 收尾时 flush 掉排队中的门洞位移。
	 */
	public interface SourceWingHolder {
		MushroomTowerWingComponent twilightmoonshine$getSourceWing();

		void twilightmoonshine$setSourceWing(MushroomTowerWingComponent source);

		void twilightmoonshine$queueOpeningShift(PendingShift shift);

		void twilightmoonshine$flushOpeningShifts();
	}

	/**
	 * @param corridor    走廊（桥）piece
	 * @param sourceWing  走廊的源塔（桥的 parent），可能为 null（理论上不会）
	 * @param wing        刚建好、已吸附完毕的目标塔
	 * @param label       塔/桥的朝向标签（结构局部 +x 指向的世界方向）
	 */
	public static void alignEntryDoor(StructurePiece corridor, MushroomTowerWingComponent sourceWing, MushroomTowerWingComponent wing, Direction label) {
		int center = wing.size / 2;
		int localZ = laneLocalZ(corridor.getBoundingBox(), wing.getBoundingBox(), label);
		if (localZ == INVALID_LANE) return;

		int delta = localZ - center;
		if (delta == 0) return; // 没被吸附过（或刚好对中），不用动

		// 门会落在平墙最外侧那格（墙心 ±2）：与其把门贴到墙角，不如整条桥连同两端门洞挪一格
		if (Math.abs(delta) == 2 && shiftCorridor(corridor, sourceWing, delta, label)) {
			localZ = laneLocalZ(corridor.getBoundingBox(), wing.getBoundingBox(), label);
		}

		int targetZ = Mth.clamp(localZ, center - 2, center + 2);
		ArrayList<BlockPos> openings = ((TowerWingComponentAccessor) wing).twilightmoonshine$getOpenings();
		for (int i = 0; i < openings.size(); i++) {
			BlockPos door = openings.get(i);
			if (door.getX() == 0 && door.getY() == 1) { // 入口门是 (0, 1, size/2)，其余门洞都在 y>=5
				openings.set(i, new BlockPos(0, 1, targetZ));
				TwilightMoonshine.LOGGER.info("Mushroom tower entry door aligned to corridor: size {}, wall center {}, door local z {}, corridor lane {}",
					wing.size, center, targetZ, localZ);
				return;
			}
		}
	}

	/**
	 * 把整条走廊横向挪一格（朝墙心），源塔侧的门洞也一起挪。
	 * <p>
	 * 只动桥的包围盒：桥的走道、栏杆都在 postProcess 里按局部坐标现铺，盒子一挪方块自然跟着挪，
	 * 两端相对位置不变；目标塔已经建好且吸附完毕，不受影响。源塔那侧的洞口没法当场改
	 * （那条门洞还没进 openings），记成 {@link PendingShift} 交给源塔收尾时处理。
	 *
	 * @return 是否挪成功；false 时调用方退回"只挪门、贴边"的老办法
	 */
	private static boolean shiftCorridor(StructurePiece corridor, MushroomTowerWingComponent sourceWing, int delta, Direction label) {
		int[] run = runVector(label);
		int[] lateral = lateralVector(label);
		if (run == null || lateral == null) return false;

		// 门在墙心 +2 就往 -z 挪、-2 就往 +z 挪：都是朝墙心挪一格
		int step = delta > 0 ? -1 : 1;
		int shiftX = lateral[0] * step;
		int shiftZ = lateral[1] * step;

		// 桥局部 (0, 1, 1) 是贴着源塔门洞外侧的那格走道（四个朝向都是），门洞在它的 -run 方向
		TFStructureComponentOldAccessor corridorAccess = (TFStructureComponentOldAccessor) corridor;
		int nearX = corridorAccess.twilightmoonshine$getWorldX(0, 1);
		int nearZ = corridorAccess.twilightmoonshine$getWorldZ(0, 1);
		int openY = corridor.getBoundingBox().minY() + 1;
		int openX = nearX - run[0];
		int openZ = nearZ - run[1];

		int[] local = sourceWing == null ? null : localShift(sourceWing.getOrientation(), shiftX, shiftZ);
		if (local == null) {
			TwilightMoonshine.LOGGER.warn("Mushroom tower corridor: source tower unknown, keeping the bridge in place ({} lane {})", label, delta);
			return false;
		}

		BoundingBox before = corridor.getBoundingBox();
		corridor.move(shiftX, 0, shiftZ);
		((SourceWingHolder) (Object) sourceWing).twilightmoonshine$queueOpeningShift(new PendingShift(openX, openY, openZ, local[0], local[1]));
		TwilightMoonshine.LOGGER.info("Mushroom tower corridor shifted one cell towards the wall center: {} {} -> {}, source opening ({}, {}, {}) local shift ({}, {})",
			label, before, corridor.getBoundingBox(), openX, openY, openZ, local[0], local[1]);
		return true;
	}

	/**
	 * 源塔 {@code addChildren} 收尾时调用：把这条塔上所有"桥挪了、门也得挪"的门洞改掉。
	 * <p>
	 * 此时源塔的 {@code openings} 已经写全（含每条走廊的 {@code addOpening}），按世界坐标认人即可。
	 */
	public static void applyPendingShifts(StructurePiece sourceWing, ArrayList<PendingShift> shifts) {
		ArrayList<BlockPos> openings = ((TowerWingComponentAccessor) sourceWing).twilightmoonshine$getOpenings();
		TFStructureComponentOldAccessor access = (TFStructureComponentOldAccessor) sourceWing;
		int minY = sourceWing.getBoundingBox().minY();

		for (PendingShift shift : shifts) {
			boolean moved = false;
			for (int i = 0; i < openings.size(); i++) {
				BlockPos door = openings.get(i);
				if (minY + door.getY() != shift.openY()) continue;
				if (access.twilightmoonshine$getWorldX(door.getX(), door.getZ()) != shift.openX()) continue;
				if (access.twilightmoonshine$getWorldZ(door.getX(), door.getZ()) != shift.openZ()) continue;

				openings.set(i, door.offset(shift.localX(), 0, shift.localZ()));
				moved = true;
				break;
			}
			if (!moved) {
				TwilightMoonshine.LOGGER.warn("Mushroom tower corridor: source door at ({}, {}, {}) not found; bridge was shifted but that door was not",
					shift.openX(), shift.openY(), shift.openZ());
			}
		}
		shifts.clear();
	}

	/**
	 * 走廊所在的那一行（桥局部 z=1 的走道）换算成目标塔的局部 z。
	 * <p>
	 * 四个朝向都是拿"走道的世界坐标"减塔盒子的近端：SOUTH/EAST 塔局部轴与 +x/+z 同向，
	 * NORTH/WEST 反向（见 {@code TFStructureComponentOld#getWorldX/getWorldZ}）。
	 */
	private static int laneLocalZ(BoundingBox corridorBox, BoundingBox wingBox, Direction label) {
		return switch (label) {
			case SOUTH -> corridorBox.minZ() + 1 - wingBox.minZ();
			case NORTH -> wingBox.maxZ() - corridorBox.maxZ() + 1;
			case WEST -> wingBox.maxX() - corridorBox.maxX() + 1;
			case EAST -> corridorBox.minX() + 1 - wingBox.minX();
			default -> INVALID_LANE;
		};
	}

	/** 走廊走向（源塔 → 目标塔）的世界单位向量；不是水平方向时为 null。 */
	private static int[] runVector(Direction label) {
		return switch (label) {
			case SOUTH -> new int[]{1, 0};
			case WEST -> new int[]{0, 1};
			case NORTH -> new int[]{-1, 0};
			case EAST -> new int[]{0, -1};
			default -> null;
		};
	}

	/** 走廊局部 +z 对应的世界单位向量；不是水平方向时为 null。 */
	private static int[] lateralVector(Direction label) {
		return switch (label) {
			case SOUTH -> new int[]{0, 1};
			case NORTH -> new int[]{0, -1};
			case WEST -> new int[]{-1, 0};
			case EAST -> new int[]{1, 0};
			default -> null;
		};
	}

	/** 把世界横移换算成源塔的局部坐标（反解 {@code getWorldX/getWorldZ}）。 */
	private static int[] localShift(Direction orientation, int dxw, int dzw) {
		return switch (orientation) {
			case SOUTH -> new int[]{dxw, dzw};
			case NORTH -> new int[]{-dxw, -dzw};
			case WEST -> new int[]{dzw, -dxw};
			case EAST -> new int[]{-dzw, dxw};
			default -> null;
		};
	}

	private MushroomTowerDoors() {}
}
