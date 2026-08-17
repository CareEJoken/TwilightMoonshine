package twilightmoonshine.item.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import twilightmoonshine.TwilightMoonshine;

/**
 * 药水 + 8 支箭 → 8 支特殊箭的合成配方。
 *
 * 中间必须是 minecraft:potion 且药水内容与 "potion" 字段匹配，
 * 周围 8 格必须是箭。用于：
 * - 荧光药水 → 光灵箭（result = minecraft:spectral_arrow，不复制药水）
 * - 暮色药水 → 暮色之箭（result = minecraft:tipped_arrow，copy_potion = true，
 *   输出复制中间药水的 potion_contents，因此延长版也能合成延长版药箭）
 */
public class PotionArrowRecipe extends CustomRecipe {

    private final Potion potion;
    private final Item result;
    private final boolean copyPotion;

    public PotionArrowRecipe(Potion potion, Item result, boolean copyPotion) {
        super(CraftingBookCategory.MISC);
        this.potion = potion;
        this.result = result;
        this.copyPotion = copyPotion;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.width() != 3 || input.height() != 3) return false;

        // 中间必须是药水且与配方指定的药水一致
        ItemStack center = input.getItem(1, 1);
        PotionContents contents = center.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        if (!center.is(Items.POTION) || contents.potion().isEmpty()
            || contents.potion().get().value() != this.potion) {
            return false;
        }

        // 周围 8 格必须是箭
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                if (x == 1 && y == 1) continue;
                if (!input.getItem(x, y).is(Items.ARROW)) return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider access) {
        ItemStack out = getResultItem(access);
        if (this.copyPotion) {
            // 药箭直接复制中间药水的 potion_contents（与配方药水一致）
            out.set(DataComponents.POTION_CONTENTS,
                input.getItem(1, 1).getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY));
        }
        return out;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider access) {
        ItemStack out = new ItemStack(this.result, 8);
        if (this.copyPotion) {
            out.set(DataComponents.POTION_CONTENTS,
                new PotionContents(BuiltInRegistries.POTION.wrapAsHolder(this.potion)));
        }
        return out;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 3 && height >= 3;
    }

    // 作为普通配方参与配方书/提示，而不是隐藏的特殊配方
    @Override
    public boolean isSpecial() {
        return false;
    }

    @Override
    public boolean showNotification() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return TwilightMoonshine.POTION_ARROW_SERIALIZER.get();
    }

    public static class Serializer implements RecipeSerializer<PotionArrowRecipe> {

        public static final MapCodec<PotionArrowRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BuiltInRegistries.POTION.byNameCodec().fieldOf("potion").forGetter(o -> o.potion),
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("result").forGetter(o -> o.result),
            Codec.BOOL.optionalFieldOf("copy_potion", false).forGetter(o -> o.copyPotion)
        ).apply(instance, PotionArrowRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, PotionArrowRecipe> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.registry(Registries.POTION), o -> o.potion,
            ByteBufCodecs.registry(Registries.ITEM), o -> o.result,
            ByteBufCodecs.BOOL, o -> o.copyPotion,
            PotionArrowRecipe::new);

        @Override
        public MapCodec<PotionArrowRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, PotionArrowRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
