package cn.autoforged.server_shop_mod_1789689358.shop;

import cn.autoforged.server_shop_mod_1789689358.data.NotificationType;
import cn.autoforged.server_shop_mod_1789689358.data.PlayerAccount;
import cn.autoforged.server_shop_mod_1789689358.data.ShopSavedData;
import cn.autoforged.server_shop_mod_1789689358.network.payload.ClientboundShopSyncPayload;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 需求 10：交易事项提示的服务端权威实现。
 * <p>
 * 未读数量只由服务端维护并持久化在 {@link PlayerAccount}，客户端无法自行修改。
 * 每次数量变化都会把全量计数下发给该玩家（kind=notify），客户端据此渲染红点/角标。
 * 另外每 10 分钟向仍在线且确有未读的玩家发送一次聊天栏提醒。
 */
public final class NotificationManager {
    /** 需求 10：聊天提醒间隔（现实时间，10 分钟）。 */
    private static final long REMINDER_INTERVAL_MS = 10L * 60L * 1000L;
    /** 检查提醒的间隔（现实时间，20 秒一次即可，避免每 tick 遍历）。 */
    private static final long REMINDER_SWEEP_MS = 20_000L;
    private static final int MAX_COUNT = 999;

    private static long lastSweepMs;

    private NotificationManager() {
    }

    /** 记录一次提示（默认 +1）。 */
    public static void notify(MinecraftServer server, UUID owner, NotificationType type) {
        notify(server, owner, type, 1);
    }

    public static void notify(MinecraftServer server, UUID owner, NotificationType type, int delta) {
        if (owner == null || type == null || delta <= 0) {
            return;
        }
        ShopSavedData data = ShopManager.data(server);
        PlayerAccount account = data.account(owner);
        int previous = account.notifications.getOrDefault(type, 0);
        int next = Math.min(MAX_COUNT, previous + delta);
        account.notifications.put(type, next);
        // 未读从 0 变正 = 新一轮未读开始，提醒节流时间戳复位，
        // 否则清空红点后隔很久再来一封邮件会立刻弹提醒（而不是“新未读后 10 分钟”）。
        if (previous == 0) {
            account.lastNotificationReminderMs = 0L;
        }
        data.setDirty();
        ServerPlayer online = server.getPlayerList().getPlayer(owner);
        if (online != null) {
            sendCounts(online, type);
        }
    }

    /** 需求“进入界面才清除”：客户端打开对应界面后请求清除，服务端清空并回传同步。 */
    public static void clear(ServerPlayer player, NotificationType type) {
        if (type == null) {
            return;
        }
        PlayerAccount account = ShopManager.account(player);
        if (account.notifications.getOrDefault(type, 0) == 0) {
            return;
        }
        account.notifications.put(type, 0);
        // 清空未读 = 提醒周期结束，节流时间戳一并复位。
        account.lastNotificationReminderMs = 0L;
        ShopManager.data(player.server).setDirty();
        sendCounts(player, null);
    }

    /** 把该玩家全部类型的未读数量下发给客户端；bumpType 非空时表示这是新增提示（客户端据此播报提示音）。 */
    public static void sendCounts(ServerPlayer player, NotificationType bumpType) {
        PlayerAccount account = ShopManager.account(player);
        CompoundTag counts = new CompoundTag();
        for (NotificationType type : NotificationType.values()) {
            counts.putInt(type.id(), account.notifications.getOrDefault(type, 0));
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", "notify");
        tag.putBoolean("bump", bumpType != null);
        if (bumpType != null) {
            tag.putString("type", bumpType.id());
        }
        tag.put("counts", counts);
        PacketDistributor.sendToPlayer(player, new ClientboundShopSyncPayload(tag));
    }

    public static int total(PlayerAccount account) {
        int total = 0;
        for (Map.Entry<NotificationType, Integer> entry : account.notifications.entrySet()) {
            if (entry.getValue() != null) {
                total += entry.getValue();
            }
        }
        return total;
    }

    /** 需求 10：玩家不一定开着商店界面，因此每 10 分钟在聊天栏提醒一次未处理事项。 */
    public static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (now - lastSweepMs < REMINDER_SWEEP_MS) {
            return;
        }
        lastSweepMs = now;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerAccount account = ShopManager.account(player);
            int total = total(account);
            if (total <= 0) {
                continue;
            }
            // 第一次发现未读时先记下时间戳，从这一刻起每 10 分钟提醒一次。
            if (account.lastNotificationReminderMs <= 0L) {
                account.lastNotificationReminderMs = now;
                ShopManager.data(server).setDirty();
                continue;
            }
            if (now - account.lastNotificationReminderMs < REMINDER_INTERVAL_MS) {
                continue;
            }
            account.lastNotificationReminderMs = now;
            ShopManager.data(server).setDirty();
            player.displayClientMessage(Component.translatable("msg.server_shop_mod.notify_reminder", total)
                    .withStyle(ChatFormatting.GOLD), false);
        }
    }
}
