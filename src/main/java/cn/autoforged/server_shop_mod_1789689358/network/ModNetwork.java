package cn.autoforged.server_shop_mod_1789689358.network;

import cn.autoforged.server_shop_mod_1789689358.ServerShopMod;
import cn.autoforged.server_shop_mod_1789689358.network.payload.ClientboundShopSyncPayload;
import cn.autoforged.server_shop_mod_1789689358.network.payload.ServerboundShopActionPayload;
import cn.autoforged.server_shop_mod_1789689358.shop.ShopManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = ServerShopMod.MODID)
public final class ModNetwork {
    private ModNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                ClientboundShopSyncPayload.TYPE,
                ClientboundShopSyncPayload.STREAM_CODEC,
                ModNetwork::handleSync);
        registrar.playToServer(
                ServerboundShopActionPayload.TYPE,
                ServerboundShopActionPayload.STREAM_CODEC,
                ModNetwork::handleAction);
    }

    // 客户端专用处理类只能通过 enqueueWork 内的 lambda 触达，避免专用服务器解析到 Screen。
    private static void handleSync(ClientboundShopSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> cn.autoforged.server_shop_mod_1789689358.client.ClientPayloadHandler.handleSync(payload.data(), context.player()));
    }

    private static void handleAction(ServerboundShopActionPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            ShopManager.handleAction(player, payload.data());
        }
    }
}
