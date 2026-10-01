package twilightmoonshine.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import twilightforest.init.TFBlocks;
import twilightforest.init.TFEntities;
import twilightforest.loot.TFLootTables;
import twilightforest.world.components.structures.mushroomtower.MushroomTowerMainComponent;
import twilightmoonshine.mixin.TFStructureComponentOldAccessor;
import twilightmoonshine.mixin.TowerWingComponentAccessor;

/**
 * 蘑菇塔（城堡）的装饰：房间里的家具 / 灯光 / 战利品，塔基裙边（补成实心），
 * 走廊栏杆上的路灯，以及主塔正门的门廊。
 * <p>
 * TF 给蘑菇塔留的装饰钩子 {@code decorateFloor} 是空的（只剩一行注释掉的
 * {@code decorateWraparoundWallSteps}），而且塔的 {@code postProcess} 根本不调它 ——
 * 巫妖塔那套家具、箱子、吊灯（{@code decorateFurniture} 等）在蘑菇塔上一次都不会执行，
 * 塔里塔外也没有任何光源。这里由 wing / bridge 两个 mixin 在 {@code postProcess}
 * 收尾时补上。
 * <p>
 * <b>确定性</b>：{@code postProcess} 会在结构覆盖到的每个区块各跑一遍（{@code sbb} 是
 * "结构盒 ∩ 当前区块"），而传进来的 {@code rand} 是逐区块的 —— 拿它做随机决策会让同一件
 * 家具在不同区块的调用里算出不同结果，跨区块正面就是"半个桌子"。所以这里**不看**传入的
 * rand，改用"结构盒坐标"派生的固定种子自己造 {@link RandomSource}：每次调用都跑同一套
 * 完整循环、做同一批决策，{@code placeBlock} 负责把不属于当前区块的方块裁掉。方块实体
 * （箱子 / 刷怪笼）另有 {@code sbb.isInside} 把关，只会被"拥有"它的那个区块写一次。）
 * <p>
 * 同理，**读世界方块**的判据不能带进"隔壁区块先写好的那半" —— 先跑完的区块可能已经把
 * 方块摆进世界了，后跑的那遍再据此做决策就会前后不一致；这类判据要跳过自己铺的方块，
 * 详见 {@link #terrainUnder}。
 */
public final class MushroomTowerDecor {

	/** 与 TF 的 {@code TowerWingComponent#FLOOR_HEIGHT} 一致。 */
	private static final int FLOOR_HEIGHT = 4;

	/** size 3 的塔内部只有 1 格宽（hollow = 0），不摆家具。 */
	private static final int MIN_DECOR_SIZE = 7;

	/** 正门门廊从门面往外铺的最大深度（格）。 */
	private static final int PORCH_DEPTH = 4;

	/** 台阶段数上限：平台面比周围平坦地面高 5 格，一块台阶抬 1 格；埋进地里的级不铺。 */
	private static final int STAIR_STEPS = 5;

	/** 门外最多能写的列数（平台 + 台阶），为什么是有限的见 {@link #decorateMainEntrance}。 */
	private static final int MAX_REACH = PORCH_DEPTH + STAIR_STEPS;

	/** 塔基裙边补实的深度：塔基面到平地面正好 4 格（与门廊 5 级台阶同一段高差）。 */
	private static final int BASE_SKIRT = 4;

	/** {@link #groundUnder} 没找到地面时的返回值（世界里不可能有这么低的方块）。 */
	private static final int NO_GROUND = Integer.MIN_VALUE;

	/** 菌柄柱：和塔身建材同一份状态（巨蘑块上下面不封口）。 */
	private static final BlockState STEM = Blocks.MUSHROOM_STEM.defaultBlockState()
		.setValue(HugeMushroomBlock.UP, false)
		.setValue(HugeMushroomBlock.DOWN, false);

	/** 每层随机挑一种布置风格，见 {@link #decorateTower}。 */
	private static final int STYLE_DINING = 0;
	private static final int STYLE_GROVE = 1;
	private static final int STYLE_PILLAR = 2;
	private static final int STYLE_BARE = 3;
	private static final int STYLES = 4;

	/** 四面墙在局部坐标系里的单位方向：0=+x，1=-x，2=+z，3=-z。 */
	private static final int[][] WALLS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	/**
	 * 给一座塔（含主塔）的每个房间摆家具、挂灯、放箱子和刷怪笼。
	 * <p>
	 * 布局按"类八边形"距离场取格子（{@code dist = (int)(max(ax,az) + 0.4*min(ax,az)) <= hollow}
	 * 才是房间内部），所有点位都收在以中心为心、{@code k = hollow - 1} 为半径的
	 * 边中点 / 对角点上，size 7/11/15 都已验证落在房间里。梯子列（{@code (ladderX, ladderZ)}，
	 * 公式与 wing mixin 的梯子注入一致）与所有这些点位都不重合，所以不再逐个判断。
	 * <p>
	 * 每层按种子随机挑一种布置，灯柱、家具贴的墙也跟着换，让房间花样多一点：
	 * <ul>
	 *   <li>{@link #STYLE_DINING} 餐桌：菌柄腿 + 红蘑菇块桌面 + 桌顶小蘑菇灯，配两把菌柄凳；</li>
	 *   <li>{@link #STYLE_GROVE} 蘑菇丛：中间一棵红伞巨菇、两边各一棵棕伞巨菇；</li>
	 *   <li>{@link #STYLE_PILLAR} 中央光柱：房间正中一根发光蘑菇柱（摆刷怪笼那层会退化成餐桌）；</li>
	 *   <li>{@link #STYLE_BARE} 空房：只留对角两盏萤火虫罐 + 正中一盏（和墙边的灯柱）。</li>
	 * </ul>
	 */
	public static void decorateTower(StructurePiece piece, WorldGenLevel world, BoundingBox sbb) {
		int size = ((TowerWingComponentAccessor) piece).twilightmoonshine$getSize();
		int height = ((TowerWingComponentAccessor) piece).twilightmoonshine$getHeight();
		if (size < MIN_DECOR_SIZE || height < FLOOR_HEIGHT * 2) return;

		// 与 makeTrunk / placeFloor 同一套距离场
		int diameter = size / 2;
		int hollow = (int) (diameter * 0.8);
		int c = diameter;
		int k = hollow - 1;
		if (k < 1) return;

		TFStructureComponentOldAccessor access = (TFStructureComponentOldAccessor) piece;
		BoundingBox box = piece.getBoundingBox();
		RandomSource rand = RandomSource.create(seedOf(piece, size));

		int floors = height / FLOOR_HEIGHT;
		for (int i = 0; i < floors; i++) {
			int floor = i * FLOOR_HEIGHT; // 楼板层；站立面 = floor + 1，房间净空到 floor + 3

			int style = rand.nextInt(STYLES);
			int lampSide = rand.nextInt(WALLS.length);
			// 中央光柱和刷怪笼抢房间正中，中间层退化成餐桌
			if (style == STYLE_PILLAR && i == floors / 2) style = STYLE_DINING;

			BlockState jar = TFBlocks.FIREFLY_JAR.value().defaultBlockState();
			if (style == STYLE_BARE) {
				// 空房：只留对角两盏 + 正中一盏
				int d = rand.nextBoolean() ? k : -k;
				access.twilightmoonshine$placeBlock(world, jar, c + d, floor + 3, c + d, sbb);
				access.twilightmoonshine$placeBlock(world, jar, c - d, floor + 3, c - d, sbb);
				access.twilightmoonshine$placeBlock(world, jar, c, floor + 3, c, sbb);
			} else {
				// 天花四角：萤火虫罐（挂在上层楼板底下），房间的主照明
				for (int sx = -1; sx <= 1; sx += 2) {
					for (int sz = -1; sz <= 1; sz += 2) {
						access.twilightmoonshine$placeBlock(world, jar, c + sx * k, floor + 3, c + sz * k, sbb);
					}
				}
			}

			// 墙边蘑菇灯柱：菌柄 + 发光蘑菇伞（每层换一面墙），氛围光
			int[] lampWall = WALLS[lampSide];
			access.twilightmoonshine$placeBlock(world, TFBlocks.HUGE_MUSHGLOOM_STEM.value().defaultBlockState(), c + lampWall[0] * k, floor + 1, c + lampWall[1] * k, sbb);
			access.twilightmoonshine$placeBlock(world, TFBlocks.HUGE_MUSHGLOOM.value().defaultBlockState(), c + lampWall[0] * k, floor + 2, c + lampWall[1] * k, sbb);

			if (style == STYLE_DINING || style == STYLE_GROVE) {
				int[] wall = WALLS[decorSide(lampSide, k, rand)];
				int ax = c + wall[0] * k; // 家具贴墙的点
				int az = c + wall[1] * k;
				int px = -wall[1]; // 沿墙方向的单位偏移
				int pz = wall[0];

				if (style == STYLE_DINING) {
					// 桌凳：菌柄腿 + 红蘑菇块桌面，配两把菌柄凳；桌上一朵小蘑菇灯
					access.twilightmoonshine$placeBlock(world, Blocks.MUSHROOM_STEM.defaultBlockState(), ax, floor + 1, az, sbb);
					access.twilightmoonshine$placeBlock(world, Blocks.RED_MUSHROOM_BLOCK.defaultBlockState(), ax, floor + 2, az, sbb);
					access.twilightmoonshine$placeBlock(world, TFBlocks.MUSHGLOOM.value().defaultBlockState(), ax, floor + 3, az, sbb);
					access.twilightmoonshine$placeBlock(world, Blocks.MUSHROOM_STEM.defaultBlockState(), ax + px, floor + 1, az + pz, sbb);
					access.twilightmoonshine$placeBlock(world, Blocks.MUSHROOM_STEM.defaultBlockState(), ax - px, floor + 1, az - pz, sbb);
				} else {
					// 蘑菇丛：中间一棵红伞巨菇、两边各一棵棕伞巨菇
					access.twilightmoonshine$placeBlock(world, Blocks.MUSHROOM_STEM.defaultBlockState(), ax, floor + 1, az, sbb);
					access.twilightmoonshine$placeBlock(world, Blocks.RED_MUSHROOM_BLOCK.defaultBlockState(), ax, floor + 2, az, sbb);
					access.twilightmoonshine$placeBlock(world, Blocks.MUSHROOM_STEM.defaultBlockState(), ax + px, floor + 1, az + pz, sbb);
					access.twilightmoonshine$placeBlock(world, Blocks.BROWN_MUSHROOM_BLOCK.defaultBlockState(), ax + px, floor + 2, az + pz, sbb);
					access.twilightmoonshine$placeBlock(world, Blocks.MUSHROOM_STEM.defaultBlockState(), ax - px, floor + 1, az - pz, sbb);
					access.twilightmoonshine$placeBlock(world, Blocks.BROWN_MUSHROOM_BLOCK.defaultBlockState(), ax - px, floor + 2, az - pz, sbb);
				}
			} else if (style == STYLE_PILLAR) {
				// 中央光柱
				access.twilightmoonshine$placeBlock(world, TFBlocks.HUGE_MUSHGLOOM_STEM.value().defaultBlockState(), c, floor + 1, c, sbb);
				access.twilightmoonshine$placeBlock(world, TFBlocks.HUGE_MUSHGLOOM.value().defaultBlockState(), c, floor + 2, c, sbb);
			}
			// STYLE_BARE 不放家具

			// 中间楼层一个蜘蛛刷怪笼（照巫妖塔的配方：洞穴蛛 / 蜂群蛛 / 树篱蛛 / 普通蛛）
			if (i == floors / 2) {
				EntityType<?> spider = switch (rand.nextInt(4)) {
					case 3 -> EntityType.CAVE_SPIDER;
					case 2 -> TFEntities.SWARM_SPIDER.get();
					case 1 -> TFEntities.HEDGE_SPIDER.get();
					default -> EntityType.SPIDER;
				};
				spawner(access, world, sbb, box, c, floor + 2, c, spider);
			}

			// 战利品箱：贴墙、面朝房间中央；每层 0~2 个
			if (rand.nextFloat() < 0.5F) {
				chest(access, world, sbb, box, c + k, floor + 1, c + k, Direction.WEST);
			}
			if (size >= 11 && rand.nextFloat() < 0.35F) {
				chest(access, world, sbb, box, c - k, floor + 1, c - k, Direction.EAST);
			}
		}
	}

	/**
	 * 给餐桌 / 蘑菇丛挑一面墙（0..3）：避开灯柱占的那面；size 7（k = 1）时还要绕开
	 * +x / +z 两面 —— 这两面摆开的凳子 / 菌菇格在 k = 1 时正好落在 (c+k, c+k)，
	 * 和大箱子的角落位置撞车。
	 */
	private static int decorSide(int lampSide, int k, RandomSource rand) {
		int[] candidates = new int[4];
		int count = 0;
		for (int side = 0; side < WALLS.length; side++) {
			if (side == lampSide) continue;
			if (k == 1 && (side == 0 || side == 2)) continue;
			candidates[count++] = side;
		}
		return candidates[rand.nextInt(count)];
	}

	/**
	 * 塔基裙边：把基座圆盘（与 makeTrunk 同一套距离场）local y = -1..-{@link #BASE_SKIRT}
	 * 无条件铺成菌柄，把顶到墙根的地形方块盖成塔基材料。
	 * <p>
	 * TF 立塔不整平地面（TerrainAdjustment.NONE），落点高度只由塔**外**的一个采样点
	 * （区块内 (7,7)，离塔心 15 格、在塔脚轮廓外）决定 —— 蘑菇塔走 DecorationClearance
	 * 默认的 {@code clamp(地表高度, 海平面+1, 海平面+7)}，TF 海平面是 0。塔基裙边则是
	 * makeTrunk 对每列 {@code fillColumnDown}：原版只替换空气 / 液体，碰到第一个实心
	 * 方块就停在各列自己的地表上。于是某一列地形比周围高 1~2 格时，那截裙边就短 1~2 格，
	 * 地形的草方块直接顶到墙根、和裙边底沿齐平 —— 看着就像"塔基的一两个方块被草替换了"。
	 * 把"塔基面到平地面"的这一条带（正好 4 格）整盘铺实后，探头的地形方块被盖掉，
	 * 裙边底沿成为完整圆盘（地形更低的地方本来就被 TF 灌了菌柄，不受影响）。
	 * <p>
	 * 条件 {@code size > 3} 与 TF 自己立裙边的 {@code hasBase} 完全一致（桥不跑这个方法，
	 * 见 {@link twilightmoonshine.mixin.MushroomTowerWingComponentMixin}）。纯写不读世界、
	 * 每次调用跑同一套完整循环，{@code placeBlock} 按区块裁剪 —— 跨区块安全。
	 */
	public static void sealBase(StructurePiece piece, WorldGenLevel world, BoundingBox sbb) {
		int size = ((TowerWingComponentAccessor) piece).twilightmoonshine$getSize();
		if (size <= 3) return;

		TFStructureComponentOldAccessor access = (TFStructureComponentOldAccessor) piece;
		int diameter = size / 2;

		for (int dx = -diameter; dx <= diameter; dx++) {
			for (int dz = -diameter; dz <= diameter; dz++) {
				int ax = Math.abs(dx);
				int az = Math.abs(dz);
				int dist = (int) (Math.max(ax, az) + (Math.min(ax, az) * 0.4));
				if (dist > diameter) continue;

				for (int dy = -1; dy >= -BASE_SKIRT; dy--) {
					access.twilightmoonshine$placeBlock(world, STEM, dx + diameter, dy, dz + diameter, sbb);
				}
			}
		}
	}

	/**
	 * 给走廊的栏杆挂路灯：只在两端各一盏（正好在两座塔的门口），中间不放 —— 一路排过去太密。
	 * <p>
	 * 桥的横截面：走道在局部 z = 1，两侧栏杆在 z = 0 / z = 2、高 1 格（y = 1），
	 * 灯就放在 z = 0 那侧栏杆顶上（y = 2）。桥是露天的（postProcess 只铺栏杆和走道），
	 * 头顶没有东西；包围盒沿走道方向是 0..size-1，x = 0 和 x = size-1 就是两道门。
	 */
	public static void decorateBridge(StructurePiece piece, WorldGenLevel world, BoundingBox sbb) {
		int size = ((TowerWingComponentAccessor) piece).twilightmoonshine$getSize();
		if (size < 3) return;

		TFStructureComponentOldAccessor access = (TFStructureComponentOldAccessor) piece;
		BlockState jar = TFBlocks.FIREFLY_JAR.value().defaultBlockState();

		access.twilightmoonshine$placeBlock(world, jar, 0, 2, 0, sbb);
		access.twilightmoonshine$placeBlock(world, jar, size - 1, 2, 0, sbb);
	}

	/**
	 * 出生主塔一层正门的门廊。正门固定在局部 -x 墙上（门洞永远是 (0, 1..2, size/2)），
	 * 但门外什么也没有：整根塔身上下一般粗，门槛的站立面比周围平坦地面高 5 格，
	 * 没有路也没有灯，从外面完全看不出那是正门，想进去还得自己垫方块。这里补一整套：
	 * 橡木平台（每列都填菌柄基座到地面）+ 两侧橡木栏杆 + 门楣上方一条红伞块"匾额"
	 * 和门两旁两盏萤火虫罐壁灯 + 一排出挑的红伞块雨棚（贴墙一排 7 格宽，往上收成
	 * 5 格、3 格两层共三级叠檐；平台够深时下面还有外排和两根菌柄支撑柱），
	 * 平台最外一排也是台阶（接住从台阶上平台的半格高差，不用跳），再往外接云杉台阶：
	 * 每级降 1 格、最多 5 级，凡是会嵌进地里的级都不铺（平地上正好裁掉最外那级，
	 * 底下同样填满菌柄），实际铺到的最外一级两侧各立一盏菌柄 + 发光蘑菇伞的路灯。
	 * <p>
	 * 门檐高度会先查一遍这道门所在墙（局部 x=0）上的 openings：高于大门的开口就是
	 * 这条墙上的走廊口，檐顶压到开口下一格（走廊面那一层）以下，免得被后铺的走廊截断。
	 * <p>
	 * <b>只做出生主塔那一个门</b>：一座蘑菇塔里不止一个主塔 —— 从出生主塔顶上的回连走廊
	 * 会再串出 2~3 座"上行者主塔"（{@code MushroomTowerMainBridgeComponent} 造的），
	 * 它们各自也有一扇同样的 (0, 1..2, size/2) 门，但开在半空、朝着走廊，不算正门
	 * （出生主塔由 {@code MushroomTowerStructure} 以 index 0 生成，其余主塔的 genDepth ≥ 1）。
	 * <p>
	 * <b>门外能铺多远是按区块算的</b>：{@code postProcess} 只对"包围盒与当前区块相交"的
	 * 区块各调一次（{@code StructureStart#placeInChunk}），传进来的 {@code sbb} 就是当前
	 * 区块的整列可写区、{@code placeBlock} 按它裁剪 —— 所以只有"所在区块与包围盒相交"
	 * 的门前列才写得进去。出发主塔的落点固定（区块居中），门面在区块边界往外 6 列，
	 * 门外还剩 9 列，正好够一整套平台 + 台阶；这里仍逐列往外试到写不动为止，按结果决定
	 * 平台和台阶的长度（连 5 列都够不着时就只留门楣和壁灯）。朝向一律写结构局部坐标系里的值，
	 * {@code placeBlock} 会按 {@code this.rotation} 再转一次（台阶的 WEST 四个朝向都验算过）。
	 */
	public static void decorateMainEntrance(StructurePiece piece, WorldGenLevel world, BoundingBox sbb) {
		if (!(piece instanceof MushroomTowerMainComponent)) return;
		// 不是出生主塔就让开 —— 上行者主塔的门在半空
		if (piece.getGenDepth() != 0) return;

		int size = ((TowerWingComponentAccessor) piece).twilightmoonshine$getSize();
		if (size < MIN_DECOR_SIZE) return;

		int doorZ = size / 2;
		int z0 = doorZ - 2; // 门廊两侧的栏杆行
		int z1 = doorZ + 2;

		TFStructureComponentOldAccessor access = (TFStructureComponentOldAccessor) piece;
		BoundingBox box = piece.getBoundingBox();

		// 门朝局部 -x，逐列往外试，直到目标列所在的区块不再与包围盒相交（那列不会被任何
		// 一次 postProcess 写到）。
		int reach = 0;
		while (reach < MAX_REACH) {
			BlockPos p = worldPos(access, box, -(reach + 1), 0, doorZ);
			if (!chunkTouches(box, p.getX(), p.getZ())) break;
			reach++;
		}
		if (reach <= 0) return;

		int porchDepth = Math.min(PORCH_DEPTH, Math.max(0, reach - STAIR_STEPS));
		int steps = reach >= STAIR_STEPS ? STAIR_STEPS : 0;

		BlockState jar = TFBlocks.FIREFLY_JAR.value().defaultBlockState();
		BlockState redMush = Blocks.RED_MUSHROOM_BLOCK.defaultBlockState();
		// 台阶状态（平台最外一排也用这个）：朝上的一面朝塔身。FACING 写 WEST ——
		// 局部坐标里塔在 +x 方向，按 TF 的旋转约定四个朝向都验算过。
		BlockState stair = Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST);

		// 门楣上方一条红伞块"匾额" + 门两旁各一盏萤火虫罐壁灯。壁灯只换外墙那一层
		// （x=1 的里层墙还在，不会开洞）；就算门外够不着铺平台，至少留下这个标记。
		for (int dz = -1; dz <= 1; dz++) {
			access.twilightmoonshine$placeBlock(world, redMush, 0, 3, doorZ + dz, sbb);
		}
		access.twilightmoonshine$placeBlock(world, jar, 0, 2, doorZ - 1, sbb);
		access.twilightmoonshine$placeBlock(world, jar, 0, 2, doorZ + 1, sbb);

		// 平台：橡木板面 + 每列往下填菌柄基座到地面。最外一排换成台阶 ——
		// 它接住台阶顶端到平台面的半格高差，从下面走上来不用跳。
		BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
		for (int x = -1; x >= -porchDepth; x--) {
			BlockState deck = x == -porchDepth ? stair : planks;
			for (int z = z0; z <= z1; z++) {
				access.twilightmoonshine$placeBlock(world, deck, x, 0, z, sbb);
				pillarToGround(access, world, sbb, box, x, -1, z);
			}
		}

		// 平台两侧的橡木栏杆（雨棚支撑柱占掉的那两格让位）
		for (int x = -1; x >= -porchDepth; x--) {
			if (porchDepth >= 2 && x == -2) continue;
			access.twilightmoonshine$placeBlock(world, Blocks.OAK_FENCE.defaultBlockState(), x, 1, z0, sbb);
			access.twilightmoonshine$placeBlock(world, Blocks.OAK_FENCE.defaultBlockState(), x, 1, z1, sbb);
		}

		// 门檐：贴墙一排出挑（比平台宽两格），往上再收两层做成三级叠檐。
		// 但门墙这面可能正好挂着一条走廊（主塔往这面接的桥，桥面占 local y >= 4），
		// 檐不能长到桥面上去（桥后铺会把檐截断）—— 从 openings 里读出这面墙（局部 x=0）
		// 上所有高于大门的开口，把檐顶压到桥面（开口 y - 1 那层）以下。
		int canopyTop = 5;
		for (BlockPos opening : ((TowerWingComponentAccessor) piece).twilightmoonshine$getOpenings()) {
			if (opening.getX() == 0 && opening.getY() > 2) {
				canopyTop = Math.min(canopyTop, opening.getY() - 2);
			}
		}
		canopyTop = Math.max(3, canopyTop);

		if (porchDepth >= 1) {
			for (int z = doorZ - 3; z <= doorZ + 3; z++) {
				access.twilightmoonshine$placeBlock(world, redMush, -1, 3, z, sbb);
			}
			if (canopyTop >= 4) {
				for (int z = z0; z <= z1; z++) {
					access.twilightmoonshine$placeBlock(world, redMush, -1, 4, z, sbb);
				}
			}
			if (canopyTop >= 5) {
				for (int z = doorZ - 1; z <= doorZ + 1; z++) {
					access.twilightmoonshine$placeBlock(world, redMush, -1, 5, z, sbb);
				}
			}
		}
		if (porchDepth >= 2) {
			for (int z = z0; z <= z1; z++) {
				access.twilightmoonshine$placeBlock(world, redMush, -2, 3, z, sbb);
			}
			access.twilightmoonshine$placeBlock(world, STEM, -2, 1, z0, sbb);
			access.twilightmoonshine$placeBlock(world, STEM, -2, 2, z0, sbb);
			access.twilightmoonshine$placeBlock(world, STEM, -2, 1, z1, sbb);
			access.twilightmoonshine$placeBlock(world, STEM, -2, 2, z1, sbb);
		}

		// 台阶：每级往下一格、往外一格，最多 5 级。先量出铺到哪一级 —— 哪一级的格子
		// 已经被地表实心方块占住（从这格往下扫到的第一个实心方块就是这格自己，
		// 平地时正好是最外那级），那一级和更外侧的级就都不铺，否则会嵌在土里。
		// 判据只看真地形（terrainUnder 跳过我们自己铺的台阶）：这排台阶的 5 列正好
		// 骑在一条区块边界上，postProcess 两个区块各跑一遍 —— 先跑的那半铺好台阶后，
		// 后跑的那遍会在扫描里看到对面半、把自己这级误判成"嵌在土里"→ 整排不铺，
		// 楼梯就只剩一半。量、铺分两趟，保证判据读到的是铺之前的地形。
		int placed = 0;
		for (int i = 1; i <= steps; i++) {
			int sx = -(porchDepth + i);
			int sy = -i;
			boolean buried = false;
			for (int z = z0; z <= z1; z++) {
				int gy = terrainUnder(world, worldPos(access, box, sx, sy, z));
				if (gy != NO_GROUND && gy >= box.minY() + sy) {
					buried = true;
					break;
				}
			}
			if (buried) break;
			placed = i;
		}
		for (int i = 1; i <= placed; i++) {
			int sx = -(porchDepth + i);
			int sy = -i;
			for (int z = z0; z <= z1; z++) {
				access.twilightmoonshine$placeBlock(world, stair, sx, sy, z, sbb);
				pillarToGround(access, world, sbb, box, sx, sy - 1, z);
			}
		}

		// 台阶底部的两盏蘑菇路灯（立在铺出来的最外一级旁边）
		if (placed > 0) {
			int bx = -(porchDepth + placed);
			mushroomLamp(access, world, sbb, box, bx, z0 - 1);
			mushroomLamp(access, world, sbb, box, bx, z1 + 1);
		}
	}

	/** 结构局部坐标 → 世界坐标（y 用 box.minY() + y）。 */
	private static BlockPos worldPos(TFStructureComponentOldAccessor access, BoundingBox box, int x, int y, int z) {
		return new BlockPos(access.twilightmoonshine$getWorldX(x, z), box.minY() + y, access.twilightmoonshine$getWorldZ(x, z));
	}

	/** 该世界列所在的区块是否与包围盒相交 —— 相交的区块才会收到 postProcess 调用，才能写。 */
	private static boolean chunkTouches(BoundingBox box, int worldX, int worldZ) {
		int cx = worldX >> 4;
		int cz = worldZ >> 4;
		return box.maxX() >= (cx << 4) && box.minX() <= (cx << 4) + 15
			&& box.maxZ() >= (cz << 4) && box.minZ() <= (cz << 4) + 15;
	}

	/** 从 (x, topY, z)（局部）往下填菌柄柱，直到踩到地面；地面够不着就整根不放。 */
	private static void pillarToGround(TFStructureComponentOldAccessor access, WorldGenLevel world, BoundingBox sbb, BoundingBox box, int x, int topY, int z) {
		int groundY = groundUnder(world, worldPos(access, box, x, topY, z));
		if (groundY == NO_GROUND) return;
		for (int y = topY; box.minY() + y > groundY; y--) {
			access.twilightmoonshine$placeBlock(world, STEM, x, y, z, sbb);
		}
	}

	/**
	 * 从 from 往下找第一个"能站"的方块（空气和液体都跳过 —— 水里要一直探到湖底），
	 * 返回它的世界 y；整列都没有就返回 {@link #NO_GROUND}。
	 */
	private static int groundUnder(WorldGenLevel world, BlockPos from) {
		for (int y = from.getY(); y >= world.getMinBuildHeight(); y--) {
			BlockState state = world.getBlockState(new BlockPos(from.getX(), y, from.getZ()));
			if (state.isAir() || !state.getFluidState().isEmpty()) continue;
			return y;
		}
		return NO_GROUND;
	}

	/**
	 * 埋地判据专用的下探：跳过空气、液体，**以及我们自己铺的台阶**，只看真地形。
	 * <p>
	 * 台阶那 5 列正好骑在 z 方向的一条区块边界上（3 列 + 2 列分属两个区块）——
	 * 出发主塔落点固定（区块居中），门面在区块里偏移 6，门外 9 列中台阶的 z
	 * 落在边界两侧，{@code postProcess} 两个区块各跑一遍。先跑的那半把台阶铺进
	 * 格子后，后跑的那遍从格子往下扫时，第一个"方块"就是对面半铺好的台阶自己，
	 * 于是把这级判成"已经嵌在土里"→ 整排不铺，表现就是楼梯只铺了左右一半
	 * （对应那半边的路灯也跟着立不起来）。跳过台阶后两遍看到的都是真地形，
	 * 算出的 {@code placed} 一致，先跑后跑铺出来完全一样。
	 * <p>
	 * 菌柄柱（台阶/平台底下填的）不用特意跳过：它只会出现在格子**下面**，
	 * 扫到它和扫穿它看到更下面的地形，对"这格是不是地形"是同一个答案。
	 */
	private static int terrainUnder(WorldGenLevel world, BlockPos from) {
		for (int y = from.getY(); y >= world.getMinBuildHeight(); y--) {
			BlockPos pos = new BlockPos(from.getX(), y, from.getZ());
			BlockState state = world.getBlockState(pos);
			if (state.isAir() || !state.getFluidState().isEmpty()) continue;
			if (state.is(Blocks.SPRUCE_STAIRS)) continue; // 台阶（平台最外那排也是它）
			return y;
		}
		return NO_GROUND;
	}

	/**
	 * 一根蘑菇路灯：菌柄两格 + 发光蘑菇伞一盏。
	 * 从局部 y=3 开始往下找地面 —— 再往上（y=4 起）是各层走廊楼板所在的高度带，
	 * 有走廊从头顶经过时会先把走廊地板当成"地面"。
	 */
	private static void mushroomLamp(TFStructureComponentOldAccessor access, WorldGenLevel world, BoundingBox sbb, BoundingBox box, int x, int z) {
		int groundY = groundUnder(world, worldPos(access, box, x, 3, z));
		if (groundY == NO_GROUND) return;
		int gy = groundY - box.minY();
		access.twilightmoonshine$placeBlock(world, STEM, x, gy + 1, z, sbb);
		access.twilightmoonshine$placeBlock(world, STEM, x, gy + 2, z, sbb);
		access.twilightmoonshine$placeBlock(world, TFBlocks.HUGE_MUSHGLOOM.value().defaultBlockState(), x, gy + 3, z, sbb);
	}

	/** 复刻 {@code TFStructureComponentOld#setSpawnerInWorld}：放刷怪笼方块 + 写实体 id。 */
	private static void spawner(TFStructureComponentOldAccessor access, WorldGenLevel world, BoundingBox sbb, BoundingBox box, int x, int y, int z, EntityType<?> type) {
		BlockPos pos = new BlockPos(access.twilightmoonshine$getWorldX(x, z), box.minY() + y, access.twilightmoonshine$getWorldZ(x, z));
		if (!sbb.isInside(pos)) return;

		world.setBlock(pos, Blocks.SPAWNER.defaultBlockState(), Block.UPDATE_CLIENTS);
		if (world.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) {
			spawner.setEntityId(type, world.getRandom());
		}
	}

	/**
	 * 放一口大箱子并挂上塔房战利品表。
	 * <p>
	 * 朝向写结构局部坐标系（{@code +x = EAST}），{@code placeBlock} 会按 {@code this.rotation}
	 * 再转一次；内容种子由 TF 自己的 {@code generateChestContents} 按坐标派生，跨区块稳定。
	 */
	private static void chest(TFStructureComponentOldAccessor access, WorldGenLevel world, BoundingBox sbb, BoundingBox box, int x, int y, int z, Direction facing) {
		access.twilightmoonshine$placeBlock(world, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), x, y, z, sbb);

		BlockPos pos = new BlockPos(access.twilightmoonshine$getWorldX(x, z), box.minY() + y, access.twilightmoonshine$getWorldZ(x, z));
		if (sbb.isInside(pos)) {
			TFLootTables.generateChestContents(world, pos, TFLootTables.TOWER_ROOM);
		}
	}

	/** 每个结构盒固定一份种子 —— {@code postProcess} 每区块跑一遍，靠它保证每遍的决策一致。 */
	private static long seedOf(StructurePiece piece, int size) {
		BoundingBox box = piece.getBoundingBox();
		return box.minX() * 341873128712L + box.minZ() * 132897987541L + (long) box.minY() * 42317861L + size;
	}

	private MushroomTowerDecor() {}
}
