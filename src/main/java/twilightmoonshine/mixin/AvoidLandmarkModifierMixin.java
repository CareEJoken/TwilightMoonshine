package twilightmoonshine.mixin;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import twilightforest.TwilightForestMod;
import twilightforest.world.components.placements.AvoidLandmarkModifier;
import twilightforest.world.components.structures.type.MushroomTowerStructure;

/**
 * 蘑菇塔顶 / 塔里不许再长"小型结构"（水井、路灯、石圈、方尖碑、树…）。
 * <p>
 * TF 的 {@code twilightforest:no_structure}（就是本类）本该拦住这些地物，但它第一道闸门
 * {@code clearFromStructureZone} 会先看结构自己的装饰配置：蘑菇塔的数据里写的是
 * {@code allow_biome_surface_decorations: true}（TF 关掉这座塔之前留下的值，从没按"巨型实心建筑"
 * 调过，对比同量级的娜迦庭院 {@code (3,false,true,true)}、暗黑塔 {@code (1,false,true,true)}
 * 都是 false），于是它直接放行 —— 剩下的 clearance 半径（2 → 32 格）也不用看了。
 * <p>
 * 于是水井的 {@code heightmap: OCEAN_FLOOR} 在结构之后跑，看到的是塔顶方块，就正好落在塔顶上。
 * （结构在 {@code SURFACE_STRUCTURES} 步铺方块，地物在 {@code VEGETAL_DECORATION} 步才跑；
 * heightmap 是那时候才 prime 的，扫的就是已经建好的塔。）
 * <p>
 * 这里不去动配置（半径式清除对一座横跨 100+ 格、四向分叉的塔来说太粗，盖不住各个翼塔），
 * 改成按 piece 精确判断：地物位置只要落在蘑菇塔**任意一个 piece** 的包围盒里（含屋顶 piece ——
 * 它的盒子比塔身向外扩了 {@code size*1.6} 的檐，正好罩住整个蘑菇伞），就拦掉。
 * 只处理"占用地表/地下"的地物；草、花、蘑菇这类纯植被沿用 TF 原本的规则（阵雨庭院里长草是特性）。
 */
@Mixin(value = AvoidLandmarkModifier.class, remap = false)
public abstract class AvoidLandmarkModifierMixin {

	@Shadow
	@Final
	private boolean occupiesSurface;

	@Shadow
	@Final
	private boolean occupiesUnderground;

	@Shadow
	@Final
	private HolderSet<Structure> structuresAllowed;

	@Inject(method = "structureTypeBlocksFeaturePlacement", at = @At("HEAD"), cancellable = true)
	private void twilightmoonshine$blockInsideMushroomTower(PlacementContext context, BlockPos pos, Structure structure, LongSet coordsForStarts, CallbackInfoReturnable<Boolean> cir) {
		if (!(structure instanceof MushroomTowerStructure)) return;

		// 纯植被（occupies_surface/underground 都是 false）不归这条管
		if (!this.occupiesSurface && !this.occupiesUnderground) return;

		// TF 原本的"允许重叠"名单，照抄一遍（现在的数据里没人放行蘑菇塔，留个口子）
		for (Holder<Structure> allowed : this.structuresAllowed) {
			if (allowed.isBound() && structure.equals(allowed.value())) return;
		}

		if (this.twilightmoonshine$insideMushroomTower(context.getLevel(), pos, coordsForStarts, structure)) {
			TwilightForestMod.LOGGER.debug("Blocked a feature at {} - it falls inside the mushroom tower", pos);
			cir.setReturnValue(true);
		}
	}

	/**
	 * 位置是否落在蘑菇塔某个 piece 的包围盒内。
	 * <p>
	 * 竖直方向必须往上放宽几格：地物原点取的是 heightmap 的"最高方块 + 1"
	 * （{@code WorldGenRegion#getHeight} 在 {@code ChunkAccess#getHeight} 之上又 +1），
	 * 而屋顶 piece 的盒子顶面比它真正的顶面方块还低一格（{@code TowerRoofMushroomComponent}
	 * 的盒子是 {@code wingBox.maxY + height + 1}，伞顶方块铺到 {@code maxY + 1}），
	 * 所以塔顶上的水井原点会落在盒子外面 2 格 —— 不放宽就漏掉了。
	 * 水平各放宽 1 格，收住贴着墙面 / 伞沿落下来的那些。
	 */
	private boolean twilightmoonshine$insideMushroomTower(WorldGenLevel level, BlockPos pos, LongSet coordsForStarts, Structure structure) {
		int x = pos.getX();
		int y = pos.getY();
		int z = pos.getZ();

		for (long packedChunkCoord : coordsForStarts) {
			int startChunkX = (int) packedChunkCoord;
			int startChunkZ = (int) (packedChunkCoord >> 32);

			ChunkAccess startChunk = level.getChunk(startChunkX, startChunkZ);
			if (startChunk == null) continue;

			StructureStart startForStructure = startChunk.getStartForStructure(structure);
			if (startForStructure == null || !startForStructure.isValid()) continue;

			for (StructurePiece piece : startForStructure.getPieces()) {
				BoundingBox box = piece.getBoundingBox();

				if (x >= box.minX() - 1 && x <= box.maxX() + 1
					&& z >= box.minZ() - 1 && z <= box.maxZ() + 1
					&& y >= box.minY() && y <= box.maxY() + 4) {
					return true;
				}
			}
		}

		return false;
	}
}
