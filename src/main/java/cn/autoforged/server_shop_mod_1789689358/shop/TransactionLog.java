package cn.autoforged.server_shop_mod_1789689358.shop;

import cn.autoforged.server_shop_mod_1789689358.Config;
import cn.autoforged.server_shop_mod_1789689358.data.PlayerAccount;
import cn.autoforged.server_shop_mod_1789689358.data.TransactionEntry;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

/**
 * 交易/资产流水：所有“钱或物品发生转移”的地方都往这里写一条，管理员用
 * {@code /shop admin history <玩家>} 翻页查看。
 * <p>
 * 只记录文本摘要，条目数由 {@link Config#transactionHistorySize} 限制（超出丢最旧的）。
 */
public final class TransactionLog {
    public static final String BUY = "buy";
    public static final String SELL = "sell";
    public static final String STALL_BUY = "stall_buy";
    public static final String STALL_SELL = "stall_sell";
    public static final String AUCTION_BID = "auction_bid";
    public static final String AUCTION_REFUND = "auction_refund";
    public static final String AUCTION_WIN = "auction_win";
    public static final String AUCTION_SELL = "auction_sell";
    public static final String TRANSFER_IN = "transfer_in";
    public static final String TRANSFER_OUT = "transfer_out";
    public static final String TRADE_IN = "trade_in";
    public static final String TRADE_OUT = "trade_out";
    public static final String MAIL_ITEM = "mail_item";
    public static final String MAIL_MONEY = "mail_money";
    public static final String ADMIN_MONEY = "admin_money";
    public static final String ADMIN_ITEM = "admin_item";
    /** 付费刷新每日商店。 */
    public static final String SHOP_REFRESH = "shop_refresh";
    /** 收购清单全部完成奖励。 */
    public static final String BUYBACK_BONUS = "buyback_bonus";

    /** 管理员查看时每页条数。 */
    public static final int PAGE_SIZE = 8;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private TransactionLog() {
    }

    /** 记录一条流水（服务端各资产变动点调用）。 */
    public static void record(MinecraftServer server, UUID owner, String type, String counterParty,
            long amount, long balanceAfter, String note) {
        if (server == null || owner == null) {
            return;
        }
        PlayerAccount account = ShopManager.data(server).account(owner);
        TransactionEntry entry = new TransactionEntry();
        entry.time = System.currentTimeMillis();
        entry.type = type == null ? "" : type;
        entry.counterParty = counterParty == null ? "" : counterParty;
        entry.amount = amount;
        entry.balanceAfter = balanceAfter;
        entry.note = note == null ? "" : note;
        account.transactions.add(entry);
        int cap = Config.INSTANCE.transactionHistorySize.get();
        while (account.transactions.size() > cap) {
            account.transactions.remove(0);
        }
        ShopManager.data(server).setDirty();
    }

    /** 物品说明文本（名称×数量），空栈返回空串。 */
    public static String itemText(ItemStack stack) {
        return stack == null || stack.isEmpty() ? "" : stack.getHoverName().getString() + "×" + stack.getCount();
    }

    /**
     * 渲染成聊天行：第一行是表头（玩家名 / 页码 / 条数 / 当前余额），其后是该页流水（最新在上）。
     *
     * @param page 从 1 开始
     */
    public static List<Component> format(PlayerAccount account, String name, int page) {
        List<Component> lines = new ArrayList<>();
        List<TransactionEntry> all = account.transactions;
        int pages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.min(Math.max(1, page), pages);
        lines.add(Component.translatable("msg.server_shop_mod.history.header",
                name, current, pages, all.size(), account.balance).withStyle(ChatFormatting.GOLD));
        if (all.isEmpty()) {
            lines.add(Component.translatable("msg.server_shop_mod.history.empty").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        int to = all.size() - (current - 1) * PAGE_SIZE;
        int from = Math.max(0, to - PAGE_SIZE);
        for (int i = to - 1; i >= from; i--) {
            TransactionEntry entry = all.get(i);
            String time = entry.time > 0L
                    ? TIME_FORMAT.format(Instant.ofEpochMilli(entry.time).atZone(ZoneId.systemDefault()))
                    : "-";
            String amountText = entry.amount == 0L
                    ? ""
                    : (entry.amount > 0L ? "+" + entry.amount : Long.toString(entry.amount));
            Component line = Component.translatable("msg.server_shop_mod.history.line",
                    time,
                    Component.translatable("gui.server_shop_mod.history." + entry.type).getString(),
                    entry.counterParty,
                    amountText,
                    entry.balanceAfter,
                    entry.note);
            lines.add(line.copy().withStyle(entry.amount > 0L ? ChatFormatting.GREEN
                    : entry.amount < 0L ? ChatFormatting.RED : ChatFormatting.GRAY));
        }
        if (pages > 1) {
            lines.add(Component.translatable("msg.server_shop_mod.history.footer", current, pages)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }
}
