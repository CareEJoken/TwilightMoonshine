package twilightmoonshine.moonlight;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import twilightmoonshine.TwilightMoonshine;

/**
 * 服务端 → 客户端：同步"月光"开关状态。
 */
public record MoonlightSyncPayload(boolean active) implements CustomPacketPayload {

    public static final Type<MoonlightSyncPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(TwilightMoonshine.MODID, "moonlight_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MoonlightSyncPayload> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.BOOL, MoonlightSyncPayload::active, MoonlightSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 广播给该维度的所有玩家 */
    public static void broadcast(ServerLevel level, boolean active) {
        MoonlightSyncPayload payload = new MoonlightSyncPayload(active);
        for (ServerPlayer player : level.players()) {
            player.connection.send(payload);
        }
    }
}
