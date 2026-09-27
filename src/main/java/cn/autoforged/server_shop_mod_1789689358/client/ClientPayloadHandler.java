package cn.autoforged.server_shop_mod_1789689358.client;

import cn.autoforged.server_shop_mod_1789689358.client.screen.GrantScreen;
import cn.autoforged.server_shop_mod_1789689358.client.screen.ShopInputScreen;
import cn.autoforged.server_shop_mod_1789689358.client.screen.ShopScreen;
import cn.autoforged.server_shop_mod_1789689358.client.screen.StallBrowseScreen;
import cn.autoforged.server_shop_mod_1789689358.client.screen.TradeRequestScreen;
import cn.autoforged.server_shop_mod_1789689358.client.screen.TradeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/** 客户端专用：接收服务端下发的快照/输入请求并打开对应界面。 */
public final class ClientPayloadHandler {
    private ClientPayloadHandler() {
    }

    public static void handleSync(CompoundTag data, Player player) {
        Minecraft minecraft = Minecraft.getInstance();
        String kind = data.getString("kind");
        if ("input".equals(kind)) {
            minecraft.setScreen(new ShopInputScreen(data));
        } else if ("stall_browse".equals(kind)) {
            // 已经在浏览同一摊位时就地刷新，避免重建界面导致翻页位置丢失。
            if (minecraft.screen instanceof StallBrowseScreen browse) {
                browse.updateEntries(data);
            } else {
                minecraft.setScreen(new StallBrowseScreen(data));
            }
        } else if ("trade".equals(kind)) {
            ClientShopData.updateTrade(data, player.level().registryAccess());
            if (minecraft.screen instanceof TradeScreen tradeScreen) {
                tradeScreen.onDataUpdated();
            } else if (data.getBoolean("forceOpen")) {
                // 需求 3：只有发起方/点击提示方才会被拉进交易界面。
                minecraft.setScreen(new TradeScreen());
            }
        } else if ("trade_closed".equals(kind)) {
            ClientShopData.clearTrade();
            if (minecraft.screen instanceof TradeScreen || minecraft.screen instanceof TradeRequestScreen) {
                minecraft.setScreen(null);
            }
        } else if ("trade_requests".equals(kind)) {
            // 需求 1：对方发来的交易请求列表。
            ClientShopData.updateRequests(data);
            if (minecraft.screen instanceof TradeRequestScreen requestScreen) {
                requestScreen.onDataUpdated();
            } else if (data.getBoolean("forceOpen")) {
                minecraft.setScreen(new TradeRequestScreen());
            }
        } else if ("admin_grant".equals(kind)) {
            // 管理员发放界面的可选玩家列表（在线 + 历史离线），界面已打开时原地刷新。
            ClientShopData.updateGrantTargets(data);
            if (minecraft.screen instanceof GrantScreen grantScreen) {
                grantScreen.onDataUpdated();
            }
        } else if ("notify".equals(kind)) {
            // 需求 10：服务端权威下发未读数量，客户端渲染红点并（可能）播放提示音。
            cn.autoforged.server_shop_mod_1789689358.client.ClientNotifications.update(data);
        } else {
            ClientShopData.update(data, player.level().registryAccess());
            boolean silent = data.getBoolean("silent");
            if (minecraft.screen instanceof ShopScreen shopScreen) {
                shopScreen.onDataUpdated();
            } else if (!silent) {
                // 静默快照（管理员重载/摊位同步）只更新缓存，不打断玩家当前界面。
                minecraft.setScreen(new ShopScreen(data.getString("defaultTab")));
            }
        }
    }
}
