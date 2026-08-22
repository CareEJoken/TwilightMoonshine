package twilightmoonshine.item.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import twilightmoonshine.TwilightMoonshine;

/**
 * 秘密材料配方（配方书隐藏，不出现在成品提示里）：
 * JSON 里给出 5 个候选材料，实际合成时按世界种子抽 3 个作为该存档的"秘密材料"，
 * 工作台需恰好放入这 3 种材料（各 1 个、任意顺序、任意摆放，不能重复）。
 * 匹配在服务端阶段计算，不同存档配方各不相同；玩家可 /moonshine_secret 查看当前存档的秘密材料。
 */
public class SecretRecipe extends CustomRecipe {

    private final Item result;
    private final List<ResourceLocation> candidates;

    public SecretRecipe(Item result, List<ResourceLocation> candidates) {
        super(CraftingBookCategory.MISC);
        this.result = result;
        this.candidates = candidates;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        Set<Item> secret = SecretRecipeLogic.secretItemsFor(level, candidates);
        if (secret.size() != 3) return false;

        // 恰好 3 个非空格子，每个 1 个且互不相同，去重后与秘密名单完全一致
        Set<Item> present = new HashSet<>();
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.getCount() != 1) return false;
            if (!present.add(stack.getItem())) return false;
        }
        return present.size() == 3 && present.equals(secret);
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider access) {
        return getResultItem(access);
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider access) {
        return new ItemStack(this.result, 1);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return TwilightMoonshine.SECRET_RECIPE_SERIALIZER.get();
    }

    public static class Serializer implements RecipeSerializer<SecretRecipe> {

        public static final MapCodec<SecretRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("result").forGetter(o -> o.result),
            ResourceLocation.CODEC.listOf().fieldOf("candidates").forGetter(o -> o.candidates)
        ).apply(instance, SecretRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, SecretRecipe> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.registry(Registries.ITEM), o -> o.result,
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), o -> o.candidates,
            SecretRecipe::new);

        @Override
        public MapCodec<SecretRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, SecretRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
