package cn.autoforged.server_shop_mod_1789689358.data;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** 单个玩家的全部经济相关数据。 */
public final class PlayerAccount {
    public long balance;
    public long lastTransferTick = -10000L;
    public String name = "";

    public long dailyShopDay = Long.MIN_VALUE;
    public final List<DailyShopEntry> dailyShop = new ArrayList<>();
    /** 已翻开的每日商店卡牌下标（需求 11：翻牌状态要持久化）。 */
    public final List<Integer> dailyFlipped = new ArrayList<>();

    public long dailyBuybackDay = Long.MIN_VALUE;
    public final List<DailyBuybackEntry> dailyBuyback = new ArrayList<>();

    public final List<MailEntry> mailbox = new ArrayList<>();
    public final List<StallEntry> stall = new ArrayList<>();
    /** 交易/资产流水（最新在末尾，条数上限见 Config.transactionHistorySize）。 */
    public final List<TransactionEntry> transactions = new ArrayList<>();
    /** 当日已付费刷新每日商店的次数（每日重置，价格表见 Config.dailyRefreshCosts）。 */
    public int dailyShopRefreshUsed;
    /** 当日“收购清单全部完成”奖励是否已发放（每日重置）。 */
    public boolean dailyBuybackBonusDone;

    /** 需求 10：各类型未读数量，服务端权威维护并持久化。 */
    public final Map<NotificationType, Integer> notifications = new EnumMap<>(NotificationType.class);
    /** 需求 10：上一次“商店有 N 条未处理事项”聊天提醒的现实时间戳（毫秒）。 */
    public long lastNotificationReminderMs;

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("balance", balance);
        tag.putLong("lastTransferTick", lastTransferTick);
        tag.putString("name", name);
        tag.putLong("dailyShopDay", dailyShopDay);
        tag.putLong("dailyBuybackDay", dailyBuybackDay);

        ListTag shopList = new ListTag();
        for (DailyShopEntry entry : dailyShop) {
            shopList.add(entry.save(registries));
        }
        tag.put("dailyShop", shopList);

        int[] flipped = new int[dailyFlipped.size()];
        for (int i = 0; i < flipped.length; i++) {
            flipped[i] = dailyFlipped.get(i);
        }
        tag.putIntArray("dailyFlipped", flipped);

        ListTag buybackList = new ListTag();
        for (DailyBuybackEntry entry : dailyBuyback) {
            buybackList.add(entry.save(registries));
        }
        tag.put("dailyBuyback", buybackList);

        ListTag mailList = new ListTag();
        for (MailEntry entry : mailbox) {
            mailList.add(entry.save(registries));
        }
        tag.put("mailbox", mailList);

        ListTag stallList = new ListTag();
        for (StallEntry entry : stall) {
            stallList.add(entry.save(registries));
        }
        tag.put("stall", stallList);

        ListTag historyList = new ListTag();
        for (TransactionEntry entry : transactions) {
            historyList.add(entry.save());
        }
        tag.put("transactions", historyList);
        tag.putInt("dailyShopRefreshUsed", dailyShopRefreshUsed);
        tag.putBoolean("dailyBuybackBonusDone", dailyBuybackBonusDone);

        CompoundTag notificationsTag = new CompoundTag();
        for (Map.Entry<NotificationType, Integer> entry : notifications.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                notificationsTag.putInt(entry.getKey().id(), entry.getValue());
            }
        }
        tag.put("notifications", notificationsTag);
        tag.putLong("lastNotificationReminderMs", lastNotificationReminderMs);
        return tag;
    }

    public static PlayerAccount load(CompoundTag tag, HolderLookup.Provider registries) {
        PlayerAccount account = new PlayerAccount();
        account.balance = tag.getLong("balance");
        account.lastTransferTick = tag.getLong("lastTransferTick");
        account.name = tag.getString("name");
        account.dailyShopDay = tag.contains("dailyShopDay") ? tag.getLong("dailyShopDay") : Long.MIN_VALUE;
        account.dailyBuybackDay = tag.contains("dailyBuybackDay") ? tag.getLong("dailyBuybackDay") : Long.MIN_VALUE;

        ListTag shopList = tag.getList("dailyShop", Tag.TAG_COMPOUND);
        for (int i = 0; i < shopList.size(); i++) {
            account.dailyShop.add(DailyShopEntry.load(shopList.getCompound(i), registries));
        }
        for (int index : tag.getIntArray("dailyFlipped")) {
            account.dailyFlipped.add(index);
        }
        ListTag buybackList = tag.getList("dailyBuyback", Tag.TAG_COMPOUND);
        for (int i = 0; i < buybackList.size(); i++) {
            account.dailyBuyback.add(DailyBuybackEntry.load(buybackList.getCompound(i), registries));
        }
        ListTag mailList = tag.getList("mailbox", Tag.TAG_COMPOUND);
        for (int i = 0; i < mailList.size(); i++) {
            account.mailbox.add(MailEntry.load(mailList.getCompound(i), registries));
        }
        ListTag stallList = tag.getList("stall", Tag.TAG_COMPOUND);
        for (int i = 0; i < stallList.size(); i++) {
            account.stall.add(StallEntry.load(stallList.getCompound(i), registries));
        }
        ListTag historyList = tag.getList("transactions", Tag.TAG_COMPOUND);
        for (int i = 0; i < historyList.size(); i++) {
            account.transactions.add(TransactionEntry.load(historyList.getCompound(i)));
        }
        account.dailyShopRefreshUsed = Math.max(0, tag.getInt("dailyShopRefreshUsed"));
        account.dailyBuybackBonusDone = tag.getBoolean("dailyBuybackBonusDone");
        CompoundTag notificationsTag = tag.getCompound("notifications");
        for (String key : notificationsTag.getAllKeys()) {
            NotificationType type = NotificationType.byId(key);
            if (type != null) {
                account.notifications.put(type, notificationsTag.getInt(key));
            }
        }
        account.lastNotificationReminderMs = tag.getLong("lastNotificationReminderMs");
        return account;
    }
}
