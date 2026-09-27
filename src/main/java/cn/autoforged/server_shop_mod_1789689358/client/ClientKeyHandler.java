package cn.autoforged.server_shop_mod_1789689358.client;

import cn.autoforged.server_shop_mod_1789689358.ServerShopMod;
import cn.autoforged.server_shop_mod_1789689358.network.payload.ServerboundShopActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** 客户端按键处理（游戏事件总线）。 */
@EventBusSubscriber(modid = ServerShopMod.MODID, value = Dist.CLIENT)
public final class ClientKeyHandler {
    private ClientKeyHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        while (ClientEvents.OPEN_SHOP.get().consumeClick()) {
            if (minecraft.screen == null) {
                CompoundTag tag = new CompoundTag();
                tag.putString("action", "request_data");
                tag.putString("tab", "daily");
                PacketDistributor.sendToServer(new ServerboundShopActionPayload(tag));
            }
        }
    }
}
