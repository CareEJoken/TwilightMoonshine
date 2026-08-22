package twilightmoonshine.event;

import java.util.List;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import twilightmoonshine.TwilightMoonshine;
import twilightmoonshine.item.recipe.SecretRecipeLogic;

/**
 * 秘密配方相关指令。
 * /moonshine_secret：查看当前存档三种秘密配方（暮色植物萃取液 / 暮色荧光精华 / 暮色合金粉末）
 * 的三种秘密材料——各自从 5 个候选中按世界种子抽 3 个（权限等级 2），用于调试工作台秘密配方。
 */
@EventBusSubscriber(modid = TwilightMoonshine.MODID, bus = EventBusSubscriber.Bus.GAME)
public class ExtractCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("moonshine_secret")
            .requires(source -> source.hasPermission(2))
            .executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                Component message = secretLine(player, "command.twilightmoonshine.secret.plant_extract",
                        SecretRecipeLogic.PLANT_EXTRACT_CANDIDATES).copy()
                    .append(Component.literal("\n"))
                    .append(secretLine(player, "command.twilightmoonshine.secret.glow_essence",
                        SecretRecipeLogic.GLOW_ESSENCE_CANDIDATES))
                    .append(Component.literal("\n"))
                    .append(secretLine(player, "command.twilightmoonshine.secret.alloy",
                        SecretRecipeLogic.ALLOY_CANDIDATES));
                context.getSource().sendSuccess(() -> message, false);
                return 1;
            }));
    }

    private static Component secretLine(ServerPlayer player, String key, List<ResourceLocation> candidates) {
        Component names = SecretRecipeLogic.secretFor(player.level(), candidates).stream()
            .map(ExtractCommand::displayName)
            .reduce((a, b) -> a.copy().append("、").append(b))
            .orElse(Component.literal("?"));
        return Component.translatable(key, names);
    }

    private static Component displayName(ResourceLocation key) {
        Item item = BuiltInRegistries.ITEM.get(key);
        return item == null ? Component.literal(key.toString()) : new ItemStack(item).getHoverName();
    }
}
