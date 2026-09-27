package cn.autoforged.server_shop_mod_1789689358.event;

import cn.autoforged.server_shop_mod_1789689358.ServerShopMod;
import cn.autoforged.server_shop_mod_1789689358.command.ShopCommands;
import cn.autoforged.server_shop_mod_1789689358.shop.NotificationManager;
import cn.autoforged.server_shop_mod_1789689358.shop.ShopManager;
import cn.autoforged.server_shop_mod_1789689358.shop.TradeManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = ServerShopMod.MODID)
public final class ShopServerEvents {
    private static int tickCounter;

    private ShopServerEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        tickCounter++;
        ShopManager.tickAuctions(server);
        TradeManager.tick(server);
        NotificationManager.tick(server);
        if (tickCounter % 20 == 0) {
            ShopManager.tickDaily(server);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ShopManager.onLogin(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ShopManager.onLogout(player);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ShopCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        TradeManager.shutdown(event.getServer());
    }
}
