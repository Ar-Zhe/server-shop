package cn.autoforged.server_shop_mod_1789689358.client;

import cn.autoforged.server_shop_mod_1789689358.data.ConfiguredOffer;
import cn.autoforged.server_shop_mod_1789689358.data.DailyBuybackEntry;
import cn.autoforged.server_shop_mod_1789689358.data.DailyShopEntry;
import cn.autoforged.server_shop_mod_1789689358.data.MailEntry;
import cn.autoforged.server_shop_mod_1789689358.data.ShopNbt;
import cn.autoforged.server_shop_mod_1789689358.data.StallEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** 客户端缓存的商店快照。仅作展示，所有数值以服务端为准。 */
public final class ClientShopData {
    public static long balance;
    public static String currency = "";
    public static String symbol = "";
    /** 需求 6：金钱显示统一追加的后缀（如“星币”），颜色与金额一致。 */
    public static String moneySuffix = "";
    public static boolean canAdmin;
    public static String defaultTab = "daily";
    public static String playerName = "";

    // ---- 玩家交易（kind=trade）----
    public static String tradeSelfName = "";
    public static String tradeOtherName = "";
    public static List<ItemStack> tradeSelfItems = new ArrayList<>();
    public static List<ItemStack> tradeOtherItems = new ArrayList<>();
    public static long tradeSelfMoney;
    public static long tradeOtherMoney;
    public static boolean tradeSelfAgreed;
    public static boolean tradeOtherAgreed;
    public static boolean tradeSelfConfirmed;
    public static boolean tradeOtherConfirmed;
    public static boolean tradeAgreedBoth;
    public static long tradeCountdownMs;
    /** 需求 2：对方是否已同意；未同意前发起方看到对方那侧为灰色等待态。 */
    public static boolean tradeAccepted;
    /** 需求 2：当前玩家是否为发起方。 */
    public static boolean tradeRequester;

    /** 需求 11：服务端当前记录的编辑模块（shop=每日商店 / buyback=每日收购）。 */
    public static String adminEditType = "shop";
    /** 需求 1：收到的交易请求列表。 */
    public static List<TradeRequest> tradeRequests = new ArrayList<>();

    public static List<DailyShopEntry> dailyShop = new ArrayList<>();
    public static List<Integer> dailyFlipped = new ArrayList<>();
    public static List<OnlinePlayer> onlinePlayers = new ArrayList<>();
    public static List<DailyBuybackEntry> dailyBuyback = new ArrayList<>();
    public static List<AuctionView> auctions = new ArrayList<>();
    public static List<StallEntry> stall = new ArrayList<>();
    public static List<StallPlayer> stallPlayers = new ArrayList<>();
    public static List<MailEntry> mail = new ArrayList<>();
    public static List<ConfiguredOffer> adminShop = new ArrayList<>();
    public static List<ConfiguredOffer> adminBuyback = new ArrayList<>();
    /** 管理员发放界面的可选玩家（在线优先，含历史离线玩家）。 */
    public static List<GrantTarget> grantTargets = new ArrayList<>();

    // ---- 每日商店付费刷新 / 收购全清奖励 ----
    /** 今日已付费刷新次数。 */
    public static int refreshUsed;
    /** 今日可付费刷新总次数（= 服务端价格表长度）。 */
    public static int refreshMax;
    /** 下一次付费刷新的花费；-1 表示今日次数已用完。 */
    public static long refreshCost = -1L;
    /** 当日收购清单全部完成的奖励金额（0=关闭）。 */
    public static long buybackBonus;
    /** 该奖励今日是否已发放。 */
    public static boolean buybackBonusDone;
    /** 收购清单里还剩几项没卖满。 */
    public static int buybackLeft;

    // ---- 界面状态（跨界面重建保留）----
    // 服务端在每次操作后会重建商店界面；若不在客户端记住页数，编辑条目/买卖后就会被弹回第一页。
    public static String lastTab = "daily";
    public static int lastAuctionPage;
    public static int lastAdminPage;
    public static int lastMailPage;
    public static String lastAdminMode = "shop";

    private ClientShopData() {
    }

    public static void update(CompoundTag tag, HolderLookup.Provider registries) {
        balance = tag.getLong("balance");
        currency = tag.getString("currency");
        symbol = tag.getString("symbol");
        // 需求 6：统一金钱后缀（如“星币”）。
        moneySuffix = tag.contains("moneySuffix") ? tag.getString("moneySuffix") : currency;
        canAdmin = tag.getBoolean("canAdmin");
        defaultTab = tag.getString("defaultTab");
        playerName = tag.getString("playerName");
        if (tag.contains("adminEditType")) {
            adminEditType = tag.getString("adminEditType");
        }
        // 付费刷新每日商店 / 收购全清奖励状态。
        refreshUsed = tag.getInt("refreshUsed");
        refreshMax = tag.getInt("refreshMax");
        refreshCost = tag.contains("refreshCost") ? tag.getLong("refreshCost") : -1L;
        buybackBonus = tag.getLong("buybackBonus");
        buybackBonusDone = tag.getBoolean("buybackBonusDone");
        buybackLeft = tag.getInt("buybackLeft");

        List<DailyShopEntry> newDaily = new ArrayList<>();
        ListTag dailyTag = tag.getList("dailyShop", Tag.TAG_COMPOUND);
        for (int i = 0; i < dailyTag.size(); i++) {
            newDaily.add(DailyShopEntry.load(dailyTag.getCompound(i), registries));
        }
        dailyShop = newDaily;

        List<Integer> newFlipped = new ArrayList<>();
        for (int index : tag.getIntArray("dailyFlipped")) {
            newFlipped.add(index);
        }
        dailyFlipped = newFlipped;

        List<OnlinePlayer> newOnline = new ArrayList<>();
        ListTag onlineTag = tag.getList("onlinePlayers", Tag.TAG_COMPOUND);
        for (int i = 0; i < onlineTag.size(); i++) {
            CompoundTag c = onlineTag.getCompound(i);
            OnlinePlayer view = new OnlinePlayer();
            try {
                view.uuid = UUID.fromString(c.getString("uuid"));
            } catch (IllegalArgumentException ignored) {
                view.uuid = null;
            }
            view.name = c.getString("name");
            newOnline.add(view);
        }
        onlinePlayers = newOnline;

        List<DailyBuybackEntry> newBuyback = new ArrayList<>();
        ListTag buybackTag = tag.getList("dailyBuyback", Tag.TAG_COMPOUND);
        for (int i = 0; i < buybackTag.size(); i++) {
            newBuyback.add(DailyBuybackEntry.load(buybackTag.getCompound(i), registries));
        }
        dailyBuyback = newBuyback;

        List<AuctionView> newAuctions = new ArrayList<>();
        ListTag auctionTag = tag.getList("auctions", Tag.TAG_COMPOUND);
        for (int i = 0; i < auctionTag.size(); i++) {
            CompoundTag c = auctionTag.getCompound(i);
            AuctionView view = new AuctionView();
            view.id = c.getLong("id");
            view.stack = ItemStack.parseOptional(registries, c.getCompound("item"));
            view.seller = c.getString("seller");
            view.startPrice = c.getLong("startPrice");
            view.currentBid = c.getLong("currentBid");
            // 需求 3：上一次出价，用于竞拍界面的划掉效果。
            view.lastBid = c.getLong("lastBid");
            view.topBidder = c.getString("topBidder");
            view.remaining = c.getLong("remaining");
            // 需求三：记录快照到达时刻，界面据此在两次同步之间实时递减剩余时间。
            view.receivedAt = System.currentTimeMillis();
            view.own = c.getBoolean("own");
            // 我是不是当前最高出价者（用于“最高：你”提示）。
            view.topMe = c.getBoolean("topMe");
            newAuctions.add(view);
        }
        auctions = newAuctions;

        List<StallEntry> newStall = new ArrayList<>();
        ListTag stallTag = tag.getList("stall", Tag.TAG_COMPOUND);
        for (int i = 0; i < stallTag.size(); i++) {
            newStall.add(StallEntry.load(stallTag.getCompound(i), registries));
        }
        stall = newStall;

        List<StallPlayer> newPlayers = new ArrayList<>();
        ListTag playerTag = tag.getList("stallPlayers", Tag.TAG_COMPOUND);
        for (int i = 0; i < playerTag.size(); i++) {
            CompoundTag c = playerTag.getCompound(i);
            StallPlayer view = new StallPlayer();
            try {
                view.uuid = UUID.fromString(c.getString("uuid"));
            } catch (IllegalArgumentException ignored) {
                view.uuid = null;
            }
            view.name = c.getString("name");
            view.count = c.getInt("count");
            newPlayers.add(view);
        }
        stallPlayers = newPlayers;

        List<MailEntry> newMail = new ArrayList<>();
        ListTag mailTag = tag.getList("mail", Tag.TAG_COMPOUND);
        for (int i = 0; i < mailTag.size(); i++) {
            newMail.add(MailEntry.load(mailTag.getCompound(i), registries));
        }
        mail = newMail;

        adminShop = parseOffers(tag.getList("adminShop", Tag.TAG_COMPOUND), registries);
        adminBuyback = parseOffers(tag.getList("adminBuyback", Tag.TAG_COMPOUND), registries);
    }

    /** 单独更新交易面板数据（kind=trade）。 */
    public static void updateTrade(CompoundTag tag, HolderLookup.Provider registries) {
        tradeSelfName = tag.getString("selfName");
        tradeOtherName = tag.getString("otherName");
        tradeSelfItems = parseItems(tag.getList("selfItems", Tag.TAG_COMPOUND), registries);
        tradeOtherItems = parseItems(tag.getList("otherItems", Tag.TAG_COMPOUND), registries);
        tradeSelfMoney = tag.getLong("selfMoney");
        tradeOtherMoney = tag.getLong("otherMoney");
        tradeSelfAgreed = tag.getBoolean("selfAgreed");
        tradeOtherAgreed = tag.getBoolean("otherAgreed");
        tradeSelfConfirmed = tag.getBoolean("selfConfirmed");
        tradeOtherConfirmed = tag.getBoolean("otherConfirmed");
        tradeAgreedBoth = tag.getBoolean("agreedBoth");
        tradeCountdownMs = tag.getLong("countdown");
        tradeAccepted = tag.getBoolean("accepted");
        tradeRequester = tag.getBoolean("requester");
    }

    /** 管理员发放界面：更新可选玩家列表（kind=admin_grant）。 */
    public static void updateGrantTargets(CompoundTag tag) {
        List<GrantTarget> result = new ArrayList<>();
        ListTag list = tag.getList("targets", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            GrantTarget target = new GrantTarget();
            try {
                target.uuid = UUID.fromString(c.getString("uuid"));
            } catch (IllegalArgumentException ignored) {
                target.uuid = null;
            }
            target.name = c.getString("name");
            target.online = c.getBoolean("online");
            result.add(target);
        }
        grantTargets = result;
    }

    /** 需求 1：更新“收到的交易请求”列表。 */
    public static void updateRequests(CompoundTag tag) {
        List<TradeRequest> result = new ArrayList<>();
        ListTag list = tag.getList("requests", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            TradeRequest request = new TradeRequest();
            request.id = c.getLong("id");
            request.from = c.getString("from");
            request.ageMs = c.getLong("age");
            // 需求二：记录快照到达时刻，倒计时文字据此每帧实时递减。
            request.receivedAt = System.currentTimeMillis();
            result.add(request);
        }
        tradeRequests = result;
    }

    /** 需求 6：金钱显示统一为“金额+星币”，颜色与金额一致（金色）。 */
    public static String money(long amount) {
        return amount + (moneySuffix == null || moneySuffix.isEmpty() ? "" : moneySuffix);
    }

    public static String money(long amount, String prefix) {
        return prefix + money(amount);
    }

    /** 交易结束后清空本地交易缓存，避免影响后续界面判断。 */
    public static void clearTrade() {
        tradeSelfName = "";
        tradeOtherName = "";
        tradeSelfItems = new ArrayList<>();
        tradeOtherItems = new ArrayList<>();
        tradeSelfMoney = 0L;
        tradeOtherMoney = 0L;
        tradeSelfAgreed = false;
        tradeOtherAgreed = false;
        tradeSelfConfirmed = false;
        tradeOtherConfirmed = false;
        tradeAgreedBoth = false;
        tradeCountdownMs = 0L;
        tradeAccepted = false;
        tradeRequester = false;
    }

    private static List<ItemStack> parseItems(ListTag list, HolderLookup.Provider registries) {
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            result.add(ShopNbt.loadStack(list.getCompound(i), registries));
        }
        return result;
    }

    private static List<ConfiguredOffer> parseOffers(ListTag list, HolderLookup.Provider registries) {
        List<ConfiguredOffer> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            result.add(ConfiguredOffer.load(list.getCompound(i), registries));
        }
        return result;
    }

    public static final class AuctionView {
        public long id;
        public ItemStack stack = ItemStack.EMPTY;
        public String seller = "";
        public long startPrice;
        public long currentBid;
        /** 需求 3：上一次出价（0 表示尚无人出价）。 */
        public long lastBid;
        public String topBidder = "";
        public long remaining;
        /** 需求三：该快照到达客户端的时刻（毫秒），用于实时递减剩余时间。 */
        public long receivedAt;
        public boolean own;
        /** 我是不是当前最高出价者。 */
        public boolean topMe;
    }

    public static final class StallPlayer {
        public UUID uuid;
        public String name = "";
        public int count;
    }

    /** 在线玩家（需求 4：交易界面显示在线玩家名片）。 */
    public static final class OnlinePlayer {
        public UUID uuid;
        public String name = "";
    }

    /** 管理员发放界面的一个可选玩家（在线 + 历史离线玩家）。 */
    public static final class GrantTarget {
        public UUID uuid;
        public String name = "";
        public boolean online;
    }

    /** 需求 1：一条待处理的交易请求。 */
    public static final class TradeRequest {
        public long id;
        public String from = "";
        public long ageMs;
        /** 需求二：该请求快照到达客户端的时刻（毫秒），用于实时递减倒计时。 */
        public long receivedAt;
    }
}
