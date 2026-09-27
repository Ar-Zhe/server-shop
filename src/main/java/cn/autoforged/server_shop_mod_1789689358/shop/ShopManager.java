package cn.autoforged.server_shop_mod_1789689358.shop;

import cn.autoforged.server_shop_mod_1789689358.Config;
import cn.autoforged.server_shop_mod_1789689358.ServerShopMod;
import cn.autoforged.server_shop_mod_1789689358.admin.AdminEditType;
import cn.autoforged.server_shop_mod_1789689358.data.AuctionEntry;
import cn.autoforged.server_shop_mod_1789689358.data.ConfiguredOffer;
import cn.autoforged.server_shop_mod_1789689358.data.DailyBuybackEntry;
import cn.autoforged.server_shop_mod_1789689358.data.DailyShopEntry;
import cn.autoforged.server_shop_mod_1789689358.data.MailEntry;
import cn.autoforged.server_shop_mod_1789689358.data.MailType;
import cn.autoforged.server_shop_mod_1789689358.data.NotificationType;
import cn.autoforged.server_shop_mod_1789689358.data.PlayerAccount;
import cn.autoforged.server_shop_mod_1789689358.data.Rarity;
import cn.autoforged.server_shop_mod_1789689358.data.ShopNbt;
import cn.autoforged.server_shop_mod_1789689358.data.ShopSavedData;
import cn.autoforged.server_shop_mod_1789689358.data.StallEntry;
import cn.autoforged.server_shop_mod_1789689358.menu.DepositMenu;
import cn.autoforged.server_shop_mod_1789689358.network.payload.ClientboundShopSyncPayload;
import cn.autoforged.server_shop_mod_1789689358.sound.ModSounds;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import javax.annotation.Nullable;

/** 服务端权威的商店逻辑。所有校验都在这里完成，客户端只负责显示与发起请求。 */
public final class ShopManager {
    public static final String SOURCE_AUCTION_UNSOLD = "auction_unsold";
    public static final String SOURCE_AUCTION_WON = "auction_won";
    public static final String SOURCE_STALL_UNSOLD = "stall_unsold";
    public static final String SOURCE_ADMIN = "admin";
    /** 需求：拍卖成交后给卖家的金额类邮件来源。 */
    public static final String SOURCE_AUCTION_SOLD = "auction_sold";
    /** 需求：摊位售出后给卖家的金额类邮件来源。 */
    public static final String SOURCE_STALL_SOLD = "stall_sold";
    /** 买家在摊位购买后，因背包放不下而进邮箱的物品来源（区别于“卖家上架未售出退还”）。 */
    public static final String SOURCE_STALL_BOUGHT = "stall_bought";
    /** 被超价后，退款余额装不下而改发金额邮件的来源。 */
    public static final String SOURCE_AUCTION_REFUND = "auction_refund";

    private static final Map<UUID, String> DEPOSIT_CONTEXT = new HashMap<>();
    private static final Map<UUID, ItemStack> PENDING_ITEM = new HashMap<>();
    /**
     * 需求 11：管理员通过幽灵槽放入的“模板物品”。
     * 幽灵槽会把原物品返还给管理员，因此这里只保存模板副本；模板绝不作为真实物品返还，
     * 避免“幽灵槽返还原物 + 悬空副本再返还”造成的刷物品漏洞。
     */
    private static final Map<UUID, ItemStack> ADMIN_TEMPLATE = new HashMap<>();

    /**
     * 需求 8/9：所有涉及余额、库存、竞拍的变更都在这把锁内执行，
     * 防止多名玩家同时购买最后一件商品/竞拍同一物品导致数据不一致。
     */
    public static final Object LOCK = new Object();

    /** 需求 11：服务端记录管理员当前正在编辑的模块（每日商店 / 每日收购）。 */
    private static final Map<UUID, AdminEditType> ADMIN_EDIT_TYPE = new HashMap<>();

    private ShopManager() {
    }

    public static ShopSavedData data(MinecraftServer server) {
        return ShopSavedData.get(server);
    }

    /**
     * 现实时间日：需求明确“每日0点”指现实时间，因此以服务器本地日历日（LocalDate）为准，
     * 在本地时间午夜切换，而不是按游戏内天数（24000 tick）。
     */
    private static long currentDay(net.minecraft.world.level.Level level) {
        return LocalDate.now().toEpochDay();
    }

    public static PlayerAccount account(ServerPlayer player) {
        ShopSavedData data = data(player.server);
        PlayerAccount account = data.account(player.getUUID());
        String name = player.getGameProfile().getName();
        if (!name.equals(account.name)) {
            account.name = name;
            data.setDirty();
        }
        return account;
    }

    // =========================================================== 每日刷新

    public static void ensureDaily(ServerPlayer player, PlayerAccount account, ShopSavedData data) {
        long day = currentDay(player.level());
        boolean dirty = false;
        boolean shopRefreshed = false;
        if (account.dailyShopDay != day) {
            account.dailyShopDay = day;
            account.dailyShop.clear();
            account.dailyFlipped.clear();
            // 新的一天：付费刷新次数重新计。
            account.dailyShopRefreshUsed = 0;
            generateDailyShop(player, account, data);
            dirty = true;
            shopRefreshed = true;
        }
        if (account.dailyBuybackDay != day) {
            account.dailyBuybackDay = day;
            account.dailyBuyback.clear();
            // 新的一天：全清奖励可以再拿一次。
            account.dailyBuybackBonusDone = false;
            generateDailyBuyback(player, account, data);
            dirty = true;
            shopRefreshed = true;
        }
        if (dirty) {
            data.setDirty();
        }
        // 需求 10：每日刷新时间到了给玩家一个商店提示红点。
        if (shopRefreshed) {
            NotificationManager.notify(player.server, player.getUUID(), NotificationType.SHOP);
        }
    }

    private static void generateDailyShop(ServerPlayer player, PlayerAccount account, ShopSavedData data) {
        account.dailyFlipped.clear();
        List<Rarity> available = new ArrayList<>();
        for (Rarity rarity : Rarity.values()) {
            if (hasRarity(data.shopPool, rarity)) {
                available.add(rarity);
            }
        }
        if (available.isEmpty()) {
            return;
        }
        RandomSource random = player.getRandom();
        Set<Item> chosen = new HashSet<>();
        // 各品质已出现的数量：普通档位有上限（默认最多 3 个），抽到满额时从可选品质里剔除。
        Map<Rarity, Integer> usedByRarity = new HashMap<>();
        int commonLimit = Config.INSTANCE.dailyShopCommonLimit.get();
        int slots = Config.INSTANCE.dailyShopSlots.get();
        for (int i = 0; i < slots; i++) {
            List<Rarity> usable = new ArrayList<>();
            for (Rarity rarity : available) {
                if (rarity == Rarity.COMMON && commonLimit > 0
                        && usedByRarity.getOrDefault(rarity, 0) >= commonLimit) {
                    continue;
                }
                usable.add(rarity);
            }
            // 理论上 available 里至少有一个可用档位；全满时退回原集合，保证槽位仍然填满。
            Rarity rarity = rollRarity(random, usable.isEmpty() ? available : usable);
            List<ConfiguredOffer> candidates = new ArrayList<>();
            for (ConfiguredOffer offer : data.shopPool) {
                if (offer.rarity == rarity && !offer.stack.isEmpty()) {
                    candidates.add(offer);
                }
            }
            if (candidates.isEmpty()) {
                continue;
            }
            List<ConfiguredOffer> fresh = new ArrayList<>();
            for (ConfiguredOffer offer : candidates) {
                if (!chosen.contains(offer.stack.getItem())) {
                    fresh.add(offer);
                }
            }
            ConfiguredOffer picked = pick(random, fresh.isEmpty() ? candidates : fresh);
            if (picked == null) {
                continue;
            }
            chosen.add(picked.stack.getItem());
            usedByRarity.merge(picked.rarity, 1, Integer::sum);
            DailyShopEntry entry = new DailyShopEntry(picked.stack.copy(), picked.price, picked.rarity);
            // 需求 7/11：每个物品各自的购买上限来自管理员配置的数量。
            entry.limit = Math.max(1, picked.amountOr(picked.rarity.purchaseLimit()));
            account.dailyShop.add(entry);
        }
    }

    private static void generateDailyBuyback(ServerPlayer player, PlayerAccount account, ShopSavedData data) {
        if (data.buybackPool.isEmpty()) {
            return;
        }
        List<ConfiguredOffer> pool = new ArrayList<>(data.buybackPool);
        RandomSource random = player.getRandom();
        // 每日最多随机 6 项收购需求：界面只渲染 6 行（没有翻页），生成端也按 6 夹取。
        int slots = Math.min(6, Math.min(Config.INSTANCE.buybackSlots.get(), pool.size()));
        for (int i = 0; i < slots; i++) {
            ConfiguredOffer picked = pick(random, pool);
            if (picked == null) {
                break;
            }
            pool.remove(picked);
            DailyBuybackEntry entry = new DailyBuybackEntry(picked.stack.copy(), picked.price);
            // 需求 6/9：收购品质与可收购数量来自管理员配置。
            entry.rarity = picked.rarity;
            entry.limit = Math.max(1, picked.amountOr(Config.INSTANCE.buybackCapacity.get()));
            account.dailyBuyback.add(entry);
        }
    }

    private static boolean hasRarity(List<ConfiguredOffer> pool, Rarity rarity) {
        for (ConfiguredOffer offer : pool) {
            if (offer.rarity == rarity && !offer.stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static Rarity rollRarity(RandomSource random, List<Rarity> available) {
        int total = 0;
        for (Rarity rarity : available) {
            total += Math.max(0, rarity.weight());
        }
        if (total <= 0) {
            return available.get(0);
        }
        int roll = random.nextInt(total);
        int cursor = 0;
        for (Rarity rarity : available) {
            cursor += Math.max(0, rarity.weight());
            if (roll < cursor) {
                return rarity;
            }
        }
        return available.get(available.size() - 1);
    }

    @Nullable
    private static ConfiguredOffer pick(RandomSource random, List<ConfiguredOffer> list) {
        if (list.isEmpty()) {
            return null;
        }
        return list.get(random.nextInt(list.size()));
    }

    /** 全局每日清理：摊位到期，未售出物品经邮箱退还。 */
    public static void tickDaily(MinecraftServer server) {
        ShopSavedData data = data(server);
        long day = currentDay(server.overworld());
        if (data.lastStallClearDay == Long.MIN_VALUE) {
            data.lastStallClearDay = day;
            return;
        }
        if (day == data.lastStallClearDay) {
            return;
        }
        data.lastStallClearDay = day;
        int expireDays = Config.INSTANCE.mailExpireDays.get();
        for (Map.Entry<UUID, PlayerAccount> entry : new ArrayList<>(data.accounts().entrySet())) {
            PlayerAccount account = entry.getValue();
            if (!account.stall.isEmpty()) {
                for (StallEntry stall : account.stall) {
                    // 需求 6：到期清摊时退还全部剩余库存，而不是 1 件。
                    if (stall.stock > 0) {
                        ItemStack refund = stall.stack.copy();
                        refund.setCount(stall.stock);
                        addMail(server, entry.getKey(), refund, SOURCE_STALL_UNSOLD);
                    }
                }
                account.stall.clear();
            }
            if (expireDays > 0) {
                // 只清理“已经领取/查看过”的邮件：未领取的物品、未查看的货款不能因为过期直接蒸发。
                // receivedDay<=0 表示旧存档缺这个字段，按“刚收到”处理，避免一开过期功能就清空历史邮件。
                account.mailbox.removeIf(mail -> !mail.pending()
                        && mail.receivedDay > 0
                        && day - mail.receivedDay >= expireDays);
            }
        }
        data.setDirty();
    }

    /** 拍卖结算。 */
    public static void tickAuctions(MinecraftServer server) {
        ShopSavedData data = data(server);
        if (data.auctions.isEmpty()) {
            return;
        }
        // 现实时间：拍卖到期时间以毫秒墙钟为准
        long now = System.currentTimeMillis();
        boolean changed = false;
        Iterator<AuctionEntry> iterator = data.auctions.iterator();
        while (iterator.hasNext()) {
            AuctionEntry auction = iterator.next();
            if (now < auction.endTime) {
                continue;
            }
            iterator.remove();
            changed = true;
            if (auction.hasBid()) {
                if (auction.stack.isEmpty()) {
                    // 存档里的物品解析失败（例如物品所属模组被移除）：不能成交，把买家的出价退回。
                    PlayerAccount winnerAccount = data.account(auction.topBidder);
                    long refundedAmount = credit(winnerAccount, auction.currentBid,
                            Config.INSTANCE.maxBalance.get());
                    if (refundedAmount < auction.currentBid) {
                        addMoneyMail(server, auction.topBidder, auction.currentBid - refundedAmount,
                                ItemStack.EMPTY, SOURCE_AUCTION_REFUND, "系统", null);
                    }
                    TransactionLog.record(server, auction.topBidder, TransactionLog.AUCTION_REFUND, "拍卖",
                            refundedAmount, winnerAccount.balance, "拍卖物品缺失，已退款");
                    ServerPlayer refundedPlayer = server.getPlayerList().getPlayer(auction.topBidder);
                    if (refundedPlayer != null) {
                        notifyPlayer(refundedPlayer, Component.translatable("msg.server_shop_mod.auction_broken_refund",
                                auction.currentBid).withStyle(ChatFormatting.GOLD));
                    }
                    data.setDirty();
                    continue;
                }
                addMail(server, auction.topBidder, auction.stack.copy(), SOURCE_AUCTION_WON);
                // 需求：拍卖成交所得以金额类邮件发放给卖家，玩家在邮箱中查看领取。
                long fee = auctionFee(auction.currentBid);
                long net = auction.currentBid - fee;
                addMoneyMail(server, auction.seller, net, auction.stack, SOURCE_AUCTION_SOLD,
                        auction.topBidderName, auction.topBidder);
                data.setDirty();
                // 流水：买家拿到物品（钱在出价时已扣），卖家货款进邮箱。
                TransactionLog.record(server, auction.topBidder, TransactionLog.AUCTION_WIN, "拍卖", 0L,
                        balanceOf(data, auction.topBidder), auction.stack.getHoverName().getString());
                TransactionLog.record(server, auction.seller, TransactionLog.AUCTION_SELL,
                        auction.topBidderName == null ? "" : auction.topBidderName, net,
                        balanceOf(data, auction.seller),
                        auction.stack.getHoverName().getString() + "（货款存邮箱）");
                // 需求 10：卖家物品卖出、买家竞拍成功都要有提示红点（邮件红点由 addMail 负责）。
                NotificationManager.notify(server, auction.seller, NotificationType.AUCTION);
                NotificationManager.notify(server, auction.topBidder, NotificationType.AUCTION);
                ServerPlayer sellerPlayer = server.getPlayerList().getPlayer(auction.seller);
                if (sellerPlayer != null) {
                    notifyPlayer(sellerPlayer, Component.translatable("msg.server_shop_mod.auction_sold_fee",
                            auction.stack.getHoverName(), auction.currentBid, fee, net)
                            .withStyle(ChatFormatting.GREEN));
                }
                ServerPlayer winner = server.getPlayerList().getPlayer(auction.topBidder);
                if (winner != null) {
                    notifyPlayer(winner, Component.translatable("msg.server_shop_mod.auction_won",
                            auction.stack.getHoverName()).withStyle(ChatFormatting.GREEN));
                }
            } else {
                addMail(server, auction.seller, auction.stack.copy(), SOURCE_AUCTION_UNSOLD);
                NotificationManager.notify(server, auction.seller, NotificationType.AUCTION);
                ServerPlayer sellerPlayer = server.getPlayerList().getPlayer(auction.seller);
                if (sellerPlayer != null) {
                    notifyPlayer(sellerPlayer, Component.translatable("msg.server_shop_mod.auction_expired",
                            auction.stack.getHoverName()).withStyle(ChatFormatting.YELLOW));
                }
            }
        }
        if (changed) {
            data.setDirty();
        }
    }

    // =========================================================== 快照与输入界面

    public static void sendSnapshot(ServerPlayer player, @Nullable String tab) {
        sendSnapshot(player, tab, false);
    }

    /**
     * 下发快照。
     * silent=true 时客户端只更新缓存、不强行打开界面（用于管理员重载/摊位变动的全员同步）。
     */
    public static void sendSnapshot(ServerPlayer player, @Nullable String tab, boolean silent) {
        PlayerAccount account = account(player);
        ShopSavedData data = data(player.server);
        ensureDaily(player, account, data);
        // 需求 3：价格始终以服务端商品池为权威，下发前用最新池数据刷新每日报价。
        refreshDailyPrices(account, data);
        HolderLookup.Provider regs = player.level().registryAccess();

        CompoundTag tag = new CompoundTag();
        tag.putString("kind", "snapshot");
        tag.putBoolean("silent", silent);
        tag.putLong("balance", account.balance);
        tag.putString("currency", Config.INSTANCE.currencyName.get());
        tag.putString("symbol", Config.INSTANCE.currencySymbol.get());
        // 需求 6：下发统一金钱后缀（如“星币”），客户端所有金额显示都用它。
        tag.putString("moneySuffix", Config.INSTANCE.currencyName.get());
        tag.putBoolean("canAdmin", player.hasPermissions(2));
        // 需求 11：下发服务端当前记录的编辑模块，界面据此高亮“每日商店/每日收购”并显示当前模式。
        tag.putString("adminEditType",
                ADMIN_EDIT_TYPE.getOrDefault(player.getUUID(), AdminEditType.DAILY_SHOP).id());
        tag.putString("defaultTab", tab == null ? "daily" : tab);
        // 主界面左上角需要显示玩家名称
        tag.putString("playerName", player.getGameProfile().getName());
        // 需求 4：交易界面需要展示在线玩家名片
        ListTag online = new ListTag();
        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            CompoundTag c = new CompoundTag();
            c.putString("uuid", other.getUUID().toString());
            c.putString("name", other.getGameProfile().getName());
            online.add(c);
        }
        tag.put("onlinePlayers", online);

        ListTag daily = new ListTag();
        for (DailyShopEntry entry : account.dailyShop) {
            CompoundTag c = ShopNbt.saveStack(entry.stack, regs);
            c.putLong("price", entry.price);
            c.putString("rarity", entry.rarity.id());
            c.putInt("bought", entry.bought);
            // 需求 11：上限按物品下发，不再使用品质公用上限。
            c.putInt("limit", entry.limit);
            daily.add(c);
        }
        tag.put("dailyShop", daily);

        // 付费刷新每日商店：refreshCost = -1 表示今日次数已用完（按钮变灰）。
        tag.putInt("refreshUsed", account.dailyShopRefreshUsed);
        tag.putInt("refreshMax", Config.INSTANCE.dailyRefreshCosts.get().size());
        tag.putLong("refreshCost", nextRefreshCost(account));

        int[] flipped = new int[account.dailyFlipped.size()];
        for (int i = 0; i < flipped.length; i++) {
            flipped[i] = account.dailyFlipped.get(i);
        }
        tag.putIntArray("dailyFlipped", flipped);

        ListTag buyback = new ListTag();
        for (DailyBuybackEntry entry : account.dailyBuyback) {
            CompoundTag c = ShopNbt.saveStack(entry.stack, regs);
            c.putLong("price", entry.price);
            c.putString("rarity", entry.rarity.id());
            c.putInt("sold", entry.sold);
            // 需求 6/9：按物品下发可收购数量。
            c.putInt("limit", entry.limit);
            buyback.add(c);
        }
        tag.put("dailyBuyback", buyback);

        // 收购全清奖励：界面据此显示「全部完成可再得 X金币（还剩 N 项）」。
        tag.putLong("buybackBonus", Config.INSTANCE.buybackAllBonus.get());
        tag.putBoolean("buybackBonusDone", account.dailyBuybackBonusDone);
        int buybackLeft = 0;
        for (DailyBuybackEntry entry : account.dailyBuyback) {
            if (entry.remaining() > 0) {
                buybackLeft++;
            }
        }
        tag.putInt("buybackLeft", buybackLeft);

        ListTag auctions = new ListTag();
        long now = System.currentTimeMillis();
        for (AuctionEntry auction : data.auctions) {
            CompoundTag c = ShopNbt.saveStack(auction.stack, regs);
            c.putLong("id", auction.id);
            c.putString("seller", auction.sellerName);
            c.putLong("startPrice", auction.startPrice);
            c.putLong("currentBid", auction.currentBid);
            // 需求 3：下发上一次出价，竞拍界面把它划掉展示（0 表示尚无人出价，显示起拍价）。
            c.putLong("lastBid", auction.lastBid);
            c.putString("topBidder", auction.topBidderName);
            c.putLong("remaining", Math.max(0, auction.endTime - now));
            c.putBoolean("own", auction.seller != null && auction.seller.equals(player.getUUID()));
            // 让界面能显示“最高：你”，玩家一眼看出自己的出价是否还在冻结中。
            c.putBoolean("topMe", auction.topBidder != null && auction.topBidder.equals(player.getUUID()));
            auctions.add(c);
        }
        tag.put("auctions", auctions);

        ListTag stall = new ListTag();
        for (StallEntry entry : account.stall) {
            CompoundTag c = ShopNbt.saveStack(entry.stack, regs);
            c.putLong("price", entry.price);
            // 需求 6：管理界面需要显示剩余库存/已售空。
            c.putInt("stock", entry.stock);
            // 需求 9：按商品统计的累计已售出件数。
            c.putInt("sold", entry.sold);
            stall.add(c);
        }
        tag.put("stall", stall);

        ListTag stallPlayers = new ListTag();
        List<Map.Entry<UUID, PlayerAccount>> stalls = new ArrayList<>();
        for (Map.Entry<UUID, PlayerAccount> entry : data.accounts().entrySet()) {
            if (!entry.getValue().stall.isEmpty()) {
                stalls.add(entry);
            }
        }
        stalls.sort(Comparator.comparingLong((Map.Entry<UUID, PlayerAccount> e) -> e.getValue().stall.get(0).order));
        for (Map.Entry<UUID, PlayerAccount> entry : stalls) {
            CompoundTag c = new CompoundTag();
            c.putString("uuid", entry.getKey().toString());
            c.putString("name", entry.getValue().name.isEmpty() ? "Unknown" : entry.getValue().name);
            c.putInt("count", entry.getValue().stall.size());
            stallPlayers.add(c);
        }
        tag.put("stallPlayers", stallPlayers);

        ListTag mail = new ListTag();
        // 需求：按时间戳倒序，最新的邮件排在最前。
        List<MailEntry> mails = new ArrayList<>(account.mailbox);
        mails.sort(Comparator.comparingLong((MailEntry m) -> m.sentAt).reversed());
        for (MailEntry entry : mails) {
            CompoundTag c = ShopNbt.saveStack(entry.stack, regs);
            c.putString("mailId", entry.mailId);
            c.putString("type", entry.type.id());
            c.putString("source", entry.source);
            // 需求 5：邮件需要显示发送者与时间戳。
            c.putString("sender", entry.sender);
            if (entry.senderUUID != null) {
                c.putUUID("senderUUID", entry.senderUUID);
            }
            c.putLong("currencyAmount", entry.currencyAmount);
            c.putBoolean("isClaimed", entry.isClaimed);
            c.putBoolean("isViewed", entry.isViewed);
            c.putLong("sentAt", entry.sentAt);
            mail.add(c);
        }
        tag.put("mail", mail);

        tag.put("adminShop", offerList(data.shopPool, regs));
        tag.put("adminBuyback", offerList(data.buybackPool, regs));

        PacketDistributor.sendToPlayer(player, new ClientboundShopSyncPayload(tag));
    }

    private static ListTag offerList(List<ConfiguredOffer> pool, HolderLookup.Provider regs) {
        ListTag list = new ListTag();
        for (ConfiguredOffer offer : pool) {
            CompoundTag c = ShopNbt.saveStack(offer.stack, regs);
            c.putLong("price", offer.price);
            c.putString("rarity", offer.rarity.id());
            // 需求 6/7：管理员配置界面上要能看到并修改数量/品质/价值。
            c.putInt("amount", offer.amount);
            list.add(c);
        }
        return list;
    }

    /**
     * 需求 1/3：每日商店与收购的价格、品质、数量始终跟随管理员配置的服务器商品池，
     * 管理员在界面改完（每日商店同样可自定义）后无需重启即可同步。
     */
    private static void refreshDailyPrices(PlayerAccount account, ShopSavedData data) {
        for (DailyShopEntry entry : account.dailyShop) {
            ConfiguredOffer match = findOffer(data.shopPool, entry.stack);
            if (match != null) {
                entry.price = match.price;
                entry.rarity = match.rarity;
                entry.limit = Math.max(1, match.amountOr(match.rarity.purchaseLimit()));
            }
        }
        for (DailyBuybackEntry entry : account.dailyBuyback) {
            ConfiguredOffer match = findOffer(data.buybackPool, entry.stack);
            if (match != null) {
                entry.price = match.price;
                entry.rarity = match.rarity;
                entry.limit = Math.max(1, match.amountOr(Config.INSTANCE.buybackCapacity.get()));
            }
        }
    }

    @Nullable
    private static ConfiguredOffer findOffer(List<ConfiguredOffer> pool, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        for (ConfiguredOffer offer : pool) {
            if (!offer.stack.isEmpty() && offer.stack.getItem() == stack.getItem()) {
                return offer;
            }
        }
        return null;
    }

    /**
     * 需求 2：管理员在本地改完商品后执行 /shop reload，即可让服务器与所有在线玩家同步，
     * 无需重启。会按最新商品池重新生成每日列表并静默推送快照（不打断玩家当前界面）。
     */
    public static void reload(MinecraftServer server) {
        ShopSavedData data = data(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerAccount account = account(player);
            account.dailyShopDay = Long.MIN_VALUE;
            account.dailyBuybackDay = Long.MIN_VALUE;
            account.dailyFlipped.clear();
        }
        data.setDirty();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendSnapshot(player, "daily", true);
        }
    }

    /** 静默地把最新快照推送给所有在线玩家，保证摊位等展示数据实时同步。 */
    public static void broadcastSnapshots(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendSnapshot(player, null, true);
        }
    }

    public static void openInput(ServerPlayer player, String action, String titleKey, @Nullable CompoundTag extra, String... fields) {
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", "input");
        tag.putString("action", action);
        tag.putString("title", titleKey);
        ListTag fieldList = new ListTag();
        for (String field : fields) {
            fieldList.add(StringTag.valueOf(field));
        }
        tag.put("fields", fieldList);
        if (extra != null) {
            tag.merge(extra);
        }
        PacketDistributor.sendToPlayer(player, new ClientboundShopSyncPayload(tag));
    }

    private static void openDeposit(ServerPlayer player, String context) {
        openDeposit(player, context, false);
    }

    private static void openDeposit(ServerPlayer player, String context, boolean ghost) {
        DEPOSIT_CONTEXT.put(player.getUUID(), context);
        // 需求 2：交易槽位已满时锁定放入槽位，防止网络延迟下进入界面后仍能塞入物品。
        boolean locked = "trade".equals(context) && TradeManager.itemsFull(player);
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> new DepositMenu(id, inventory, new SimpleContainer(1), ghost, locked),
                Component.translatable("gui.server_shop_mod.deposit")));
    }

    // =========================================================== 动作分发

    public static void handleAction(ServerPlayer player, CompoundTag tag) {
        String action = tag.getString("action");
        // 服务端权威：管理操作必须校验权限，客户端不再可信（canAdmin 只是显示开关）。
        if (action.startsWith("admin_") && !player.hasPermissions(2)) {
            return;
        }
        switch (action) {
            case "request_data" -> {
                returnPending(player);
                sendSnapshot(player, tag.getString("tab"));
            }
            case "create_auction" -> openDeposit(player, "auction");
            case "create_stall" -> openDeposit(player, "stall");
            case "deposit_confirm" -> handleDepositConfirm(player);
            case "buy_shop" -> buyShop(player, tag.getInt("index"), parseInt(tag.getString("quantity")));
            case "flip_shop" -> flipShop(player, tag.getInt("index"));
            // 付费刷新每日商店（价格与次数见 Config.dailyRefreshCosts）。
            case "refresh_daily_shop" -> refreshDailyShopPaid(player);
            case "sell_buyback" -> sellBuyback(player, tag.getInt("index"), parseInt(tag.getString("quantity")));
            case "bid" -> placeBid(player, tag);
            case "claim_mail" -> claimMail(player, tag.getString("mail_id"));
            case "view_mail" -> viewMail(player, tag.getString("mail_id"));
            case "claim_all_mail" -> claimAllMail(player);
            case "delete_mail" -> deleteMail(player, tag.getString("mail_id"), tag.getBoolean("confirmed"));
            // 需求四：一键清理已经领取/查看过的邮件。
            case "delete_read_mail" -> deleteReadMail(player);
            case "do_transfer" -> doTransfer(player, tag);
            case "open_stall" -> openStallBrowse(player, tag.getString("owner"));
            case "stall_buy" -> stallBuy(player, tag);
            case "stall_apply_price" -> stallApplyPrice(player, tag);
            case "remove_stall" -> removeStall(player, tag.getInt("index"));
            // 需求五：已取消补货，售完的商品只能下架后重新上架。
            // 需求 10：客户端打开对应界面后请求清除该类型红点（服务端权威清零并回传）。
            case "notif_clear" -> NotificationManager.clear(player, NotificationType.byId(tag.getString("type")));
            case "auction_set_start" -> auctionSetStart(player, tag);
            case "stall_set_price_new" -> stallSetPriceNew(player, tag);
            // ---- 玩家交易 ----
            case "trade_request" -> TradeManager.request(player, tag.getString("target"));
            case "trade_add_item" -> openDeposit(player, "trade");
            // 需求 3：从自己的交易区域删除已放入的物品并返还背包/邮箱。
            case "trade_remove_item" -> TradeManager.removeItem(player, tag.getInt("index"));
            case "trade_money_gui" -> {
                // 需求 3：交易金额界面显示玩家余额（服务端权威数值随输入界面下发）。
                CompoundTag moneyExtra = new CompoundTag();
                moneyExtra.putLong("balance", account(player).balance);
                moneyExtra.putBoolean("show_balance", true);
                openInput(player, "trade_set_money", "gui.server_shop_mod.input.trade_money", moneyExtra, "amount");
            }
            case "trade_set_money" -> TradeManager.setMoney(player, parsePositive(tag.getString("amount")));
            case "trade_agree" -> TradeManager.toggleAgree(player);
            case "trade_confirm" -> TradeManager.confirm(player);
            case "trade_cancel" -> TradeManager.cancel(player, true);
            // 需求 1/2：交易请求列表与接受/拒绝。
            case "trade_requests_open" -> TradeManager.sendRequestList(player, true);
            case "trade_accept" -> TradeManager.acceptRequest(player, tag.getLong("id"));
            case "trade_decline" -> TradeManager.declineRequest(player, tag.getLong("id"));
            // 需求 11：服务端记录管理员当前编辑的模块，物品放入时据此归属列表。
            case "admin_set_edit_type" -> setAdminEditType(player, tag.getString("type"));
            // 需求 11：管理员添加物品必须使用幽灵槽位，防止刷物品。
            case "admin_add" -> openDeposit(player, "admin", true);
            case "admin_apply_price" -> adminApplyPrice(player, tag);
            case "admin_remove" -> adminRemove(player, tag);
            case "admin_set_price_new" -> adminSetPriceNew(player, tag);
            // ---- 管理员发放（发钱 / 发物品）----
            case "admin_grant_open" -> GrantManager.open(player);
            case "admin_grant_request" -> GrantManager.sendTargets(player);
            case "admin_grant_send" -> GrantManager.send(player, tag);
            // ---- 管理员：查看某玩家交易流水（设置页“交易记录”按钮）----
            case "admin_history" -> sendHistory(player, tag.getString("target"), tag.getInt("page"));
            default -> {
            }
        }
    }

    private static void handleDepositConfirm(ServerPlayer player) {
        if (!(player.containerMenu instanceof DepositMenu menu)) {
            return;
        }
        ItemStack stack = menu.deposit().getItem(0);
        if (stack.isEmpty()) {
            notifyPlayer(player, Component.translatable("msg.server_shop_mod.deposit_empty").withStyle(ChatFormatting.RED));
            return;
        }
        ItemStack taken = stack.copy();
        String context = DEPOSIT_CONTEXT.getOrDefault(player.getUUID(), "");
        // 需求 11：幽灵槽不真正取走物品，关闭界面时会把原物品返还给管理员。
        if (!menu.isGhost()) {
            menu.deposit().setItem(0, ItemStack.EMPTY);
        }
        player.closeContainer();
        returnPending(player);
        // 交易放入的物品直接进入交易会话，不能走 PENDING_ITEM（否则会悬空）。
        if ("trade".equals(context)) {
            TradeManager.addItem(player, taken);
            return;
        }
        // 需求 11：管理员用幽灵槽，模板单独保存，不进入 PENDING_ITEM，避免与返还的原物重复。
        if ("admin".equals(context)) {
            ADMIN_TEMPLATE.put(player.getUUID(), taken);
            AdminEditType type = ADMIN_EDIT_TYPE.getOrDefault(player.getUUID(), AdminEditType.DAILY_SHOP);
            CompoundTag extra = new CompoundTag();
            extra.putString("mode", type.id());
            // 需求 6/7：价值、品质、数量一次配置完成。
            openInput(player, "admin_set_price_new", "gui.server_shop_mod.input.admin_offer", extra,
                    "price", "rarity", "quantity");
            return;
        }
        PENDING_ITEM.put(player.getUUID(), taken);
        switch (context) {
            case "auction" -> openInput(player, "auction_set_start", "gui.server_shop_mod.input.start_price",
                    null, "price");
            case "stall" -> {
                // 需求 4：上架时同时输入单件价格与库存数量，默认带出放入的物品数量（上限 64）。
                CompoundTag extra = new CompoundTag();
                extra.putString("default_quantity", Integer.toString(Math.max(1, Math.min(64, taken.getCount()))));
                openInput(player, "stall_set_price_new", "gui.server_shop_mod.input.stall_price", extra,
                        "price", "quantity");
            }
            default -> returnPending(player);
        }
    }

    /** 需求 11：记录管理员当前编辑的模块。 */
    public static void setAdminEditType(ServerPlayer player, String type) {
        ADMIN_EDIT_TYPE.put(player.getUUID(), AdminEditType.byId(type));
    }

    /** 优先使用服务端记录的编辑模块，缺省时退回消息里携带的 mode。 */
    private static boolean isShopMode(ServerPlayer player, CompoundTag tag) {
        AdminEditType stored = ADMIN_EDIT_TYPE.get(player.getUUID());
        if (stored != null) {
            return stored == AdminEditType.DAILY_SHOP;
        }
        return !"buyback".equals(tag.getString("mode"));
    }

    /** 把悬而未决的上架物品安全退还给玩家，避免取消输入界面导致物品丢失。 */
    private static void returnPending(ServerPlayer player) {
        ItemStack pending = PENDING_ITEM.remove(player.getUUID());
        if (pending != null && !pending.isEmpty()) {
            addItemOrMail(player, pending, SOURCE_ADMIN);
        }
        // 幽灵槽模板不再作为真实物品返还（原物已由幽灵槽退回），直接丢弃防止刷物品。
        ADMIN_TEMPLATE.remove(player.getUUID());
    }

    // =========================================================== 每日商店 / 收购

    /** 需求 11：翻开每日商店卡牌，持久化翻开状态并按稀有度播放对应音效。 */
    private static void flipShop(ServerPlayer player, int index) {
        PlayerAccount account = account(player);
        ShopSavedData data = data(player.server);
        ensureDaily(player, account, data);
        if (index < 0 || index >= account.dailyShop.size() || account.dailyFlipped.contains(index)) {
            return;
        }
        account.dailyFlipped.add(index);
        data.setDirty();
        DailyShopEntry entry = account.dailyShop.get(index);
        player.playNotifySound(ModSounds.forRarity(entry.rarity), SoundSource.PLAYERS, 1.0F, 1.0F);
        sendSnapshot(player, "daily");
    }

    /** 第 N 次付费刷新每日商店的花费；返回 -1 表示次数已用完（价格表见 Config.dailyRefreshCosts）。 */
    public static long nextRefreshCost(PlayerAccount account) {
        List<? extends String> costs = Config.INSTANCE.dailyRefreshCosts.get();
        int used = Math.max(0, account.dailyShopRefreshUsed);
        if (used >= costs.size()) {
            return -1L;
        }
        try {
            return Math.max(1L, Long.parseLong(String.valueOf(costs.get(used)).trim()));
        } catch (NumberFormatException ex) {
            return -1L;
        }
    }

    /**
     * 付费刷新每日商店：按价格表顺序扣费（默认第一次 200、第二次 500），
     * 每日次数由价格表长度决定（默认 2 次），扣完即不可再刷新。
     */
    private static void refreshDailyShopPaid(ServerPlayer player) {
        synchronized (LOCK) {
            PlayerAccount account = account(player);
            ShopSavedData data = data(player.server);
            ensureDaily(player, account, data);
            long cost = nextRefreshCost(account);
            if (cost < 0L) {
                notifyPlayer(player, Component.translatable("msg.server_shop_mod.refresh_no_uses")
                        .withStyle(ChatFormatting.RED));
                return;
            }
            if (data.shopPool.isEmpty()) {
                notifyPlayer(player, Component.translatable("msg.server_shop_mod.refresh_empty")
                        .withStyle(ChatFormatting.RED));
                return;
            }
            if (account.balance < cost) {
                notifyPlayer(player, Component.translatable("msg.server_shop_mod.no_money")
                        .withStyle(ChatFormatting.RED));
                return;
            }
            account.balance -= cost;
            account.dailyShopRefreshUsed++;
            account.dailyShop.clear();
            account.dailyFlipped.clear();
            generateDailyShop(player, account, data);
            refreshDailyPrices(account, data);
            data.setDirty();
            TransactionLog.record(player.server, player.getUUID(), TransactionLog.SHOP_REFRESH, "服务器商店",
                    -cost, account.balance, "");
            long next = nextRefreshCost(account);
            if (next < 0L) {
                notifyPlayer(player, Component.translatable("msg.server_shop_mod.refresh_ok_last", cost)
                        .withStyle(ChatFormatting.GREEN));
            } else {
                notifyPlayer(player, Component.translatable("msg.server_shop_mod.refresh_ok", cost, next)
                        .withStyle(ChatFormatting.GREEN));
            }
            sendSnapshot(player, "daily");
        }
    }

    /** 当日收购清单全部卖完的一次性奖励（每天一次，Config.buybackAllBonus）。 */
    private static void maybeGrantBuybackBonus(ServerPlayer player, PlayerAccount account, ShopSavedData data) {
        long bonus = Config.INSTANCE.buybackAllBonus.get();
        if (bonus <= 0L || account.dailyBuybackBonusDone || account.dailyBuyback.isEmpty()) {
            return;
        }
        for (DailyBuybackEntry entry : account.dailyBuyback) {
            if (entry.remaining() > 0) {
                return;
            }
        }
        account.dailyBuybackBonusDone = true;
        long credited = credit(account, bonus, Config.INSTANCE.maxBalance.get());
        data.setDirty();
        TransactionLog.record(player.server, player.getUUID(), TransactionLog.BUYBACK_BONUS, "服务器商店",
                credited, account.balance, "");
        notifyPlayer(player, Component.translatable("msg.server_shop_mod.buyback_all_bonus", credited)
                .withStyle(ChatFormatting.GOLD));
    }

    /** 需求 8：每日商店支持一次购买多件（数量由客户端弹窗选择，服务端按限购/余额夹取）。 */
    private static void buyShop(ServerPlayer player, int index, int requested) {
        // 需求 8：限购、余额、发货在同一把锁内完成，防止并发抢购最后一件。
        synchronized (LOCK) {
            PlayerAccount account = account(player);
            ShopSavedData data = data(player.server);
            ensureDaily(player, account, data);
            if (index < 0 || index >= account.dailyShop.size()) {
                return;
            }
            DailyShopEntry entry = account.dailyShop.get(index);
            // 需求 11：上限按物品计算，而不是同一品质的所有物品共用一个上限。
            int remaining = entry.limit - entry.bought;
            if (remaining <= 0) {
                notifyPlayer(player, Component.translatable("msg.server_shop_mod.limit_reached").withStyle(ChatFormatting.RED));
                return;
            }
            int buyCount = Math.min(Math.max(1, requested), remaining);
            // 价格×数量必须做溢出保护：极端价格下裸乘会溢出成负数，
            // 于是“余额不足”判定恒通过、扣款反而变成给买家加钱。
            long total = safeMultiply(entry.price, buyCount);
            if (total <= 0L) {
                notifyPlayer(player, Component.translatable("msg.server_shop_mod.invalid_price").withStyle(ChatFormatting.RED));
                return;
            }
            // 需求 9：价格重新从服务端条目读取，绝不使用客户端传来的价格。
            if (account.balance < total) {
                notifyPlayer(player, Component.translatable("msg.server_shop_mod.no_money").withStyle(ChatFormatting.RED));
                return;
            }
            // 需求 10：保留附魔/自定义名/NBT；数量超过单栈上限时按份交付。
            Component rewardName = entry.stack.getHoverName();
            account.balance -= total;
            entry.bought += buyCount;
            data.setDirty();
            // 背包放不下的部分转邮箱，避免因买多件而丢物品。
            // 这里不再用“没有空格就拒绝购买”的预检：那会把“没有空槽但同类还能堆叠”误判成背包已满。
            deliver(player, entry.stack, buyCount, SOURCE_ADMIN);
            TransactionLog.record(player.server, player.getUUID(), TransactionLog.BUY, "服务器商店",
                    -total, account.balance, entry.stack.getHoverName().getString() + "×" + buyCount);
            // 需求 6：摘要统一为“购买物品X，花费100星币”。
            // 需求一：购买模板「花费 金额 金币 购买 物品名」，金额在前、物品名在后，物品名可为纯数字。
            if (buyCount <= 1) {
                notifyPlayer(player, Component.translatable("msg.server_shop_mod.bought",
                        total, entry.stack.getHoverName()).withStyle(ChatFormatting.GREEN));
            } else {
                notifyPlayer(player, Component.translatable("msg.server_shop_mod.bought_count",
                        total, entry.stack.getHoverName(), buyCount).withStyle(ChatFormatting.GREEN));
            }
            sendSnapshot(player, "daily");
        }
    }

    /** 需求 9：玩家可自行选择卖出数量，上限为该物品剩余的收购数量。 */
    private static void sellBuyback(ServerPlayer player, int index, int requested) {
        synchronized (LOCK) {
            PlayerAccount account = account(player);
            ShopSavedData data = data(player.server);
            ensureDaily(player, account, data);
            if (index < 0 || index >= account.dailyBuyback.size()) {
                return;
            }
            DailyBuybackEntry entry = account.dailyBuyback.get(index);
            if (entry.remaining() <= 0) {
                notifyPlayer(player, Component.translatable("msg.server_shop_mod.buyback_full").withStyle(ChatFormatting.RED));
                return;
            }
            // 余额剩余空间不足时提前夹取/拒绝，避免“物品已被收走、钱却因上限被吞掉”。
            long max = Config.INSTANCE.maxBalance.get();
            long room = Math.max(0L, max - account.balance);
            if (entry.price <= 0L || room < entry.price) {
                notifyPlayer(player, Component.translatable("msg.server_shop_mod.invalid_price").withStyle(ChatFormatting.RED));
                return;
            }
            int affordable = (int) Math.min(Integer.MAX_VALUE, room / entry.price);
            int wanted = Math.min(requested <= 0 ? 1 : requested, Math.min(entry.remaining(), affordable));
            int taken = takeMatching(player, entry.stack.getItem(), wanted);
            if (taken <= 0) {
                notifyPlayer(player, Component.translatable("msg.server_shop_mod.not_enough_items").withStyle(ChatFormatting.RED));
                return;
            }
            long total = safeMultiply(entry.price, taken);
            if (total <= 0L) {
                // 收购价异常（溢出/非正数）：把已经取走的物品原样还给玩家，不做任何结算。
                deliver(player, entry.stack, taken, SOURCE_ADMIN);
                notifyPlayer(player, Component.translatable("msg.server_shop_mod.invalid_price").withStyle(ChatFormatting.RED));
                return;
            }
            entry.sold += taken;
            account.balance = Math.min(max, account.balance + total);
            data.setDirty();
            TransactionLog.record(player.server, player.getUUID(), TransactionLog.SELL, "服务器商店",
                    total, account.balance, entry.stack.getHoverName().getString() + "×" + taken);
            notifyPlayer(player, Component.translatable("msg.server_shop_mod.sold_count", entry.stack.getHoverName(), taken, total)
                    .withStyle(ChatFormatting.GREEN));
            // 当日收购清单全部卖完 → 一次性奖励（在推送快照前结算，界面立刻能看到）。
            maybeGrantBuybackBonus(player, account, data);
            sendSnapshot(player, "buyback");
        }
    }

    /** 从背包中按数量取走匹配物品，返回实际取走的数量。 */
    private static int takeMatching(ServerPlayer player, Item item, int max) {
        Inventory inventory = player.getInventory();
        int taken = 0;
        for (int i = 0; i < inventory.getContainerSize() && taken < max; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || stack.getItem() != item) {
                continue;
            }
            int take = Math.min(max - taken, stack.getCount());
            stack.shrink(take);
            taken += take;
            if (stack.isEmpty()) {
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
        return taken;
    }

    // =========================================================== 拍卖

    private static void auctionSetStart(ServerPlayer player, CompoundTag tag) {
        ItemStack stack = PENDING_ITEM.remove(player.getUUID());
        if (stack == null || stack.isEmpty()) {
            return;
        }
        long price = parsePrice(tag.getString("price"));
        if (price <= 0) {
            addItemOrMail(player, stack, SOURCE_ADMIN);
            notifyPlayer(player, Component.translatable("msg.server_shop_mod.invalid_price").withStyle(ChatFormatting.RED));
            return;
        }
        ShopSavedData data = data(player.server);
        if (data.auctionsOf(player.getUUID()).size() >= Config.INSTANCE.maxConcurrentAuctions.get()) {
            addItemOrMail(player, stack, SOURCE_ADMIN);
            notifyPlayer(player, Component.translatable("msg.server_shop_mod.auction_limit").withStyle(ChatFormatting.RED));
            return;
        }
        AuctionEntry auction = new AuctionEntry();
        auction.id = data.nextAuctionId++;
        auction.seller = player.getUUID();
        auction.sellerName = player.getGameProfile().getName();
        auction.stack = stack;
        auction.startPrice = price;
        auction.currentBid = 0;
        // 现实时间：持续 auctionDurationSeconds 秒（默认 2 小时）
        auction.endTime = System.currentTimeMillis() + Config.INSTANCE.auctionDurationSeconds.get() * 1000L;
        data.auctions.add(auction);
        data.setDirty();
        notifyPlayer(player, Component.translatable("msg.server_shop_mod.auction_created", stack.getHoverName(), price)
                .withStyle(ChatFormatting.GREEN));
        sendSnapshot(player, "auction");
    }

    private static void placeBid(ServerPlayer player, CompoundTag tag) {
        // 需求 8：竞拍校验与扣款/退款必须在同一把锁内，避免同时出价导致余额错乱。
        synchronized (LOCK) {
        ShopSavedData data = data(player.server);
        long id = tag.getLong("id");
        long amount = parsePositive(tag.getString("amount"));
        AuctionEntry auction = null;
        for (AuctionEntry entry : data.auctions) {
            if (entry.id == id) {
                auction = entry;
                break;
            }
        }
        if (auction == null) {
            return;
        }
        if (System.currentTimeMillis() >= auction.endTime) {
            // 已经到期但还没被结算的挂单不再接受出价，避免“截止后再出价也算成交”。
            return;
        }
        if (auction.seller != null && auction.seller.equals(player.getUUID())) {
            notifyPlayer(player, Component.translatable("msg.server_shop_mod.bid_own").withStyle(ChatFormatting.RED));
            return;
        }
        long minimum = auction.hasBid()
                ? auction.currentBid + Config.INSTANCE.auctionMinIncrement.get()
                : auction.startPrice;
        if (amount < minimum) {
            notifyPlayer(player, Component.translatable("msg.server_shop_mod.bid_too_low", minimum)
                    .withStyle(ChatFormatting.RED));
            return;
        }
        PlayerAccount bidder = account(player);
        if (bidder.balance < amount) {
            notifyPlayer(player, Component.translatable("msg.server_shop_mod.no_money").withStyle(ChatFormatting.RED));
            return;
        }
        // 退还上一位出价者
        if (auction.hasBid()) {
            // 需求三：被超价日志同时附带旧价格（上一次最高价）与新价格（本次出价）。
            long previousPrice = auction.currentBid;
            PlayerAccount previous = data.account(auction.topBidder);
            // 退款同样走饱和入账：余额装不下的部分改成金额邮件退回，绝不静默销毁。
            long refunded = credit(previous, previousPrice, Config.INSTANCE.maxBalance.get());
            if (refunded < previousPrice) {
                addMoneyMail(player.server, auction.topBidder, previousPrice - refunded, ItemStack.EMPTY,
                        SOURCE_AUCTION_REFUND, "系统", null);
            }
            TransactionLog.record(player.server, auction.topBidder, TransactionLog.AUCTION_REFUND, "拍卖",
                    refunded, previous.balance, auction.stack.getHoverName().getString());
            ServerPlayer previousPlayer = player.server.getPlayerList().getPlayer(auction.topBidder);
            if (previousPlayer != null) {
                notifyPlayer(previousPlayer, Component.translatable("msg.server_shop_mod.outbid",
                        auction.stack.getHoverName(), previousPrice, amount).withStyle(ChatFormatting.YELLOW));
                // 钱已经退回（余额或邮箱），立刻刷新他的界面余额，避免显示还在冻结中。
                sendSnapshot(previousPlayer, "auction", true);
            }
            // 需求 10：被超价的一方收到拍卖提示红点（离线也持久化）。
            NotificationManager.notify(player.server, auction.topBidder, NotificationType.AUCTION);
        }
        bidder.balance -= amount;
        TransactionLog.record(player.server, player.getUUID(), TransactionLog.AUCTION_BID,
                auction.sellerName == null ? "" : auction.sellerName, -amount, bidder.balance,
                auction.stack.getHoverName().getString());
        // 需求 3：记录上一次出价，供竞拍界面把被超过的价格划掉展示。
        auction.lastBid = auction.currentBid;
        auction.currentBid = amount;
        auction.topBidder = player.getUUID();
        auction.topBidderName = player.getGameProfile().getName();
        if (Config.INSTANCE.antiSnipe.get()) {
            long remaining = auction.endTime - System.currentTimeMillis();
            long threshold = Config.INSTANCE.antiSnipeSeconds.get() * 1000L;
            long extension = Config.INSTANCE.antiSnipeExtensionSeconds.get() * 1000L;
            if (extension > 0 && remaining < threshold) {
                auction.endTime += extension;
            }
        }
        data.setDirty();
        notifyPlayer(player, Component.translatable("msg.server_shop_mod.bid_placed", amount, auction.stack.getHoverName())
                .withStyle(ChatFormatting.GREEN));
        sendSnapshot(player, "auction");
        }
    }

    // =========================================================== 摊位

    private static void stallSetPriceNew(ServerPlayer player, CompoundTag tag) {
        ItemStack stack = PENDING_ITEM.remove(player.getUUID());
        if (stack == null || stack.isEmpty()) {
            return;
        }
        long price = parsePrice(tag.getString("price"));
        // 需求 4：上架时可设置库存数量（单件价格），最大 64，且不能超过实际持有量。
        int requested = parseInt(tag.getString("quantity"));
        int total = Math.min(64, requested <= 0 ? Math.max(1, stack.getCount()) : requested);
        total = Math.min(total, Math.max(1, stack.getCount()));
        PlayerAccount account = account(player);
        ShopSavedData data = data(player.server);
        if (price <= 0) {
            addItemOrMail(player, stack, SOURCE_ADMIN);
            notifyPlayer(player, Component.translatable("msg.server_shop_mod.invalid_price").withStyle(ChatFormatting.RED));
            return;
        }
        if (account.stall.size() >= Config.INSTANCE.stallSlots.get()) {
            addItemOrMail(player, stack, SOURCE_ADMIN);
            notifyPlayer(player, Component.translatable("msg.server_shop_mod.stall_full").withStyle(ChatFormatting.RED));
            return;
        }
        double feePercent = Config.INSTANCE.stallListingFeePercent.get();
        long fee = (long) Math.ceil(price * feePercent / 100.0D);
        if (fee > 0 && account.balance < fee) {
            addItemOrMail(player, stack, SOURCE_ADMIN);
            notifyPlayer(player, Component.translatable("msg.server_shop_mod.no_money").withStyle(ChatFormatting.RED));
            return;
        }
        account.balance -= fee;
        // 超出上架数量的部分原路退还，避免物品丢失。
        if (total < stack.getCount()) {
            ItemStack leftover = stack.copy();
            leftover.setCount(stack.getCount() - total);
            addItemOrMail(player, leftover, SOURCE_STALL_UNSOLD);
        }
        ItemStack unit = stack.copy();
        unit.setCount(1);
        account.stall.add(new StallEntry(unit, price, data.listingCounter++, total));
        data.setDirty();
        notifyPlayer(player, Component.translatable("msg.server_shop_mod.stall_listed", unit.getHoverName(), price, total)
                .withStyle(ChatFormatting.GREEN));
        sendSnapshot(player, "stall");
        broadcastSnapshots(player.server);
    }

    private static void stallApplyPrice(ServerPlayer player, CompoundTag tag) {
        int index = tag.getInt("index");
        long price = parsePrice(tag.getString("price"));
        PlayerAccount account = account(player);
        if (index < 0 || index >= account.stall.size() || price <= 0) {
            return;
        }
        account.stall.get(index).price = price;
        data(player.server).setDirty();
        sendSnapshot(player, "stall");
        broadcastSnapshots(player.server);
    }

    private static void removeStall(ServerPlayer player, int index) {
        PlayerAccount account = account(player);
        if (index < 0 || index >= account.stall.size()) {
            return;
        }
        StallEntry entry = account.stall.remove(index);
        // 需求 6：下架时把剩余库存（不是 1 件）全部退还。
        if (entry.stock > 0) {
            ItemStack refund = entry.stack.copy();
            refund.setCount(entry.stock);
            addItemOrMail(player, refund, SOURCE_STALL_UNSOLD);
        }
        data(player.server).setDirty();
        sendSnapshot(player, "stall");
        broadcastSnapshots(player.server);
    }

    private static void openStallBrowse(ServerPlayer player, String ownerUuid) {
        UUID owner;
        try {
            owner = UUID.fromString(ownerUuid);
        } catch (IllegalArgumentException ex) {
            return;
        }
        ShopSavedData data = data(player.server);
        PlayerAccount account = data.peek(owner);
        if (account == null) {
            return;
        }
        HolderLookup.Provider regs = player.level().registryAccess();
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", "stall_browse");
        tag.putString("owner", owner.toString());
        tag.putString("ownerName", account.name.isEmpty() ? "Unknown" : account.name);
        ListTag list = new ListTag();
        for (int i = 0; i < account.stall.size(); i++) {
            StallEntry entry = account.stall.get(i);
            CompoundTag c = ShopNbt.saveStack(entry.stack, regs);
            c.putLong("price", entry.price);
            c.putInt("stock", entry.stock);
            // 需求 9：浏览摊位时也显示该商品已售出件数。
            c.putInt("sold", entry.sold);
            c.putInt("index", i);
            list.add(c);
        }
        tag.put("entries", list);
        PacketDistributor.sendToPlayer(player, new ClientboundShopSyncPayload(tag));
    }

    private static void stallBuy(ServerPlayer player, CompoundTag tag) {
        UUID owner;
        try {
            owner = UUID.fromString(tag.getString("owner"));
        } catch (IllegalArgumentException ex) {
            return;
        }
        int index = tag.getInt("index");
        // 需求 5：购买数量由玩家在弹窗中选择，服务端再次夹取到库存与余额上限。
        int requested = parseInt(tag.getString("quantity"));
        int want = requested <= 0 ? 1 : requested;
        if (owner.equals(player.getUUID())) {
            return;
        }
        synchronized (LOCK) {
            ShopSavedData data = data(player.server);
            PlayerAccount sellerAccount = data.peek(owner);
            // 需求 9：按索引从服务端真实摊位数据取物品与价格，不信任客户端。
            if (sellerAccount == null || index < 0 || index >= sellerAccount.stall.size()) {
                return;
            }
            StallEntry entry = sellerAccount.stall.get(index);
            if (entry.stock <= 0) {
                notifyPlayer(player, Component.translatable("msg.server_shop_mod.stall_sold_out").withStyle(ChatFormatting.RED));
                return;
            }
            int buyCount = Math.min(want, entry.stock);
            // 摊位单价由玩家自己设置，同样必须做溢出保护（裸乘溢出为负 → 判定失效 + 给买家加钱）。
            long total = safeMultiply(entry.price, buyCount);
            if (total <= 0L) {
                notifyPlayer(player, Component.translatable("msg.server_shop_mod.invalid_price").withStyle(ChatFormatting.RED));
                return;
            }
            PlayerAccount buyer = account(player);
            if (buyer.balance < total) {
                notifyPlayer(player, Component.translatable("msg.server_shop_mod.no_money").withStyle(ChatFormatting.RED));
                return;
            }
            buyer.balance -= total;
            entry.stock -= buyCount;
            // 需求 9：记录该商品累计已售出件数（各商品分别统计）。
            entry.sold += buyCount;
            // 需求：卖家所得以金额类邮件发放，玩家在邮箱中查看领取。
            addMoneyMail(player.server, owner, total, entry.stack.copy(), SOURCE_STALL_SOLD,
                    player.getGameProfile().getName(), player.getUUID());
            // 需求 5/10：保留 NBT；装不下的部分发到邮箱。
            // 来源用“购买”而不是“上架未售出”，否则买家邮箱里会显示成“你上架的物品没卖出去，已退还”。
            Component rewardName = entry.stack.getHoverName();
            boolean mailed = deliver(player, entry.stack, buyCount, SOURCE_STALL_BOUGHT);
            data.setDirty();
            // 需求一：摘要统一为「花费 金额 金币 购买 物品名×数量」，金额字段在前。
            notifyPlayer(player, Component.translatable("msg.server_shop_mod.bought_count", total, rewardName, buyCount)
                    .withStyle(ChatFormatting.GREEN));
            if (mailed) {
                notifyPlayer(player, Component.translatable("msg.server_shop_mod.inventory_full_mail").withStyle(ChatFormatting.RED));
            }
            ServerPlayer seller = player.server.getPlayerList().getPlayer(owner);
            if (seller != null) {
                notifyPlayer(seller, Component.translatable("msg.server_shop_mod.stall_sold", rewardName, buyCount, total)
                        .withStyle(ChatFormatting.GREEN));
            }
            // 需求 10：卖家摊位有物品被买走 → 提示红点。
            NotificationManager.notify(player.server, owner, NotificationType.STALL);
            String sellerName = sellerAccount.name == null || sellerAccount.name.isEmpty() ? "摊位" : sellerAccount.name;
            TransactionLog.record(player.server, player.getUUID(), TransactionLog.STALL_BUY, sellerName,
                    -total, buyer.balance, rewardName + "×" + buyCount);
            TransactionLog.record(player.server, owner, TransactionLog.STALL_SELL,
                    player.getGameProfile().getName(), total, sellerAccount.balance,
                    rewardName + "×" + buyCount + "（货款存邮箱）");
        }
        // 静默刷新买家缓存，并把最新库存推回正在浏览的摊位界面（需求 5：买完还剩多少显示多少）。
        sendSnapshot(player, "stall", true);
        broadcastSnapshots(player.server);
        openStallBrowse(player, owner.toString());
    }

    // =========================================================== 邮箱

    /** 按 mailId 在服务端真实邮箱里查表，绝不信任客户端传来的下标。 */
    private static MailEntry findMail(PlayerAccount account, String mailId) {
        if (mailId == null || mailId.isEmpty()) {
            return null;
        }
        for (MailEntry entry : account.mailbox) {
            if (mailId.equals(entry.mailId)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * 物品类邮件：领取进背包。背包放不下的部分继续留在邮件里（绝不丢 NBT，也不会重复发放）。
     * 并发安全：整段在 LOCK 内执行，并且只有全部放入后才置 isClaimed=true。
     */
    private static void claimMail(ServerPlayer player, String mailId) {
        synchronized (LOCK) {
            PlayerAccount account = account(player);
            MailEntry entry = findMail(account, mailId);
            if (entry == null || entry.type != MailType.ITEM || entry.isClaimed || entry.stack.isEmpty()) {
                return;
            }
            ItemStack remaining = entry.stack.copy();
            player.getInventory().add(remaining);
            if (!remaining.isEmpty()) {
                entry.stack = remaining;
                // 邮件里的数量已经在内存里减少，必须标脏落盘：SavedData 只写 isDirty() 的数据，
                // 否则定时重启/崩溃后邮件恢复原数量，等于“部分领取 + 重启”复制物品。
                data(player.server).setDirty();
                notifyPlayer(player, Component.translatable("msg.server_shop_mod.inventory_full").withStyle(ChatFormatting.RED));
                sendSnapshot(player, "mail");
                return;
            }
            entry.isClaimed = true;
            data(player.server).setDirty();
            TransactionLog.record(player.server, player.getUUID(), TransactionLog.MAIL_ITEM, "邮箱",
                    0L, account.balance, TransactionLog.itemText(entry.stack));
            notifyPlayer(player, Component.translatable("msg.server_shop_mod.mail_claimed", entry.stack.getHoverName())
                    .withStyle(ChatFormatting.GREEN));
            sendSnapshot(player, "mail");
        }
    }

    /** 金额类邮件：把星币加进钱包并标记已查看。 */
    private static void viewMail(ServerPlayer player, String mailId) {
        synchronized (LOCK) {
            PlayerAccount account = account(player);
            MailEntry entry = findMail(account, mailId);
            if (entry == null || entry.type != MailType.MONEY || entry.isViewed) {
                return;
            }
            long amount = entry.currencyAmount;
            long credited = credit(account, amount, Config.INSTANCE.maxBalance.get());
            if (credited < amount) {
                // 余额装不下：只领能装下的部分，剩下的留在邮件里（绝不静默销毁）。
                entry.currencyAmount = amount - credited;
                data(player.server).setDirty();
                notifyPlayer(player, Component.translatable("msg.server_shop_mod.mail_partial_money",
                        credited, entry.currencyAmount).withStyle(ChatFormatting.GOLD));
                sendSnapshot(player, "mail");
                return;
            }
            entry.isViewed = true;
            data(player.server).setDirty();
            TransactionLog.record(player.server, player.getUUID(), TransactionLog.MAIL_MONEY, "邮箱",
                    credited, account.balance, "");
            notifyPlayer(player, Component.translatable("msg.server_shop_mod.mail_viewed", amount)
                    .withStyle(ChatFormatting.GREEN));
            sendSnapshot(player, "mail");
        }
    }

    /** 一键领取/查看所有未处理邮件；背包放不下的物品留在邮箱并提示。 */
    private static void claimAllMail(ServerPlayer player) {
        synchronized (LOCK) {
            PlayerAccount account = account(player);
            if (account.mailbox.isEmpty()) {
                return;
            }
            boolean changed = false;
            boolean full = false;
            for (MailEntry entry : account.mailbox) {
                if (entry.type == MailType.MONEY) {
                    if (!entry.isViewed) {
                        long amount = entry.currencyAmount;
                        long credited = credit(account, amount, Config.INSTANCE.maxBalance.get());
                        if (credited < amount) {
                            // 装不下的部分留在邮件里，等余额有空间再领。
                            entry.currencyAmount = amount - credited;
                        } else {
                            entry.isViewed = true;
                        }
                        if (credited > 0L) {
                            TransactionLog.record(player.server, player.getUUID(), TransactionLog.MAIL_MONEY,
                                    "邮箱", credited, account.balance, "");
                        }
                        changed = true;
                    }
                    continue;
                }
                if (entry.isClaimed || entry.stack.isEmpty()) {
                    continue;
                }
                ItemStack remaining = entry.stack.copy();
                player.getInventory().add(remaining);
                if (!remaining.isEmpty()) {
                    // 背包满：该物品留在邮箱，但继续处理后面的金额邮件（金额不占背包）。
                    entry.stack = remaining;
                    full = true;
                    // 数量已在内存里减少，同样要标脏落盘（理由见 claimMail）。
                    changed = true;
                    continue;
                }
                entry.isClaimed = true;
                changed = true;
            }
            if (changed) {
                data(player.server).setDirty();
            }
            if (full) {
                notifyPlayer(player, Component.translatable("msg.server_shop_mod.inventory_full").withStyle(ChatFormatting.RED));
            }
            sendSnapshot(player, "mail");
        }
    }

    /**
     * 删除一封邮件。未领取/未查看的邮件必须带 confirmed=true（客户端弹二次确认），
     * 服务端同样强制校验，防止误删重要资产。
     */
    private static void deleteMail(ServerPlayer player, String mailId, boolean confirmed) {
        synchronized (LOCK) {
            PlayerAccount account = account(player);
            MailEntry entry = findMail(account, mailId);
            if (entry == null) {
                return;
            }
            if (entry.pending() && !confirmed) {
                notifyPlayer(player, Component.translatable("msg.server_shop_mod.mail_delete_need_confirm")
                        .withStyle(ChatFormatting.YELLOW));
                return;
            }
            account.mailbox.remove(entry);
            data(player.server).setDirty();
            sendSnapshot(player, "mail");
        }
    }

    /** 需求四：一键清理所有已经领取（物品）/已查看（金额）的邮件。 */
    private static void deleteReadMail(ServerPlayer player) {
        synchronized (LOCK) {
            PlayerAccount account = account(player);
            boolean removed = account.mailbox.removeIf(entry -> !entry.pending());
            if (!removed) {
                return;
            }
            data(player.server).setDirty();
            notifyPlayer(player, Component.translatable("msg.server_shop_mod.mail_deleted_read")
                    .withStyle(ChatFormatting.GREEN));
            sendSnapshot(player, "mail");
        }
    }

    // =========================================================== 钱包 / 转账

    private static void doTransfer(ServerPlayer player, CompoundTag tag) {
        if (!Config.INSTANCE.allowPlayerTransfer.get()) {
            notifyPlayer(player, Component.translatable("msg.server_shop_mod.transfer_disabled").withStyle(ChatFormatting.RED));
            return;
        }
        String targetName = tag.getString("target");
        long amount = parsePositive(tag.getString("amount"));
        if (targetName.isEmpty() || amount <= 0) {
            return;
        }
        ServerPlayer target = player.server.getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            notifyPlayer(player, Component.translatable("msg.server_shop_mod.target_offline").withStyle(ChatFormatting.RED));
            // 需求一备用模板：转账失败日志「向 目标玩家名 转账 金额 金币失败」。
            notifyPlayer(player, Component.translatable("msg.server_shop_mod.transfer_failed", targetName, amount)
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (target == player) {
            return;
        }
        // 需求 7：同一 IP 的玩家之间不可转账，管理员不受限制。
        if (sameIp(player, target) && !player.hasPermissions(2) && !target.hasPermissions(2)) {
            notifyPlayer(player, Component.translatable("msg.server_shop_mod.transfer_same_ip").withStyle(ChatFormatting.RED));
            return;
        }
        ShopSavedData data = data(player.server);
        // 需求 4/8：余额校验与扣款放在同一把锁内，防止并发转账把钱扣成负数。
        synchronized (LOCK) {
            PlayerAccount from = account(player);
            long now = player.level().getGameTime();
            if (now - from.lastTransferTick < Config.INSTANCE.transferCooldownTicks.get()) {
                notifyPlayer(player, Component.translatable("msg.server_shop_mod.transfer_cooldown").withStyle(ChatFormatting.RED));
                return;
            }
            if (from.balance < amount) {
                notifyPlayer(player, Component.translatable("msg.server_shop_mod.no_money").withStyle(ChatFormatting.RED));
                return;
            }
            PlayerAccount to = account(target);
            // 对方余额装不下这笔转账：整笔拒绝，避免“付款方已扣、收款方只收到一部分”。
            long room = Math.max(0L, Config.INSTANCE.maxBalance.get() - to.balance);
            if (room < amount) {
                notifyPlayer(player, Component.translatable("msg.server_shop_mod.target_balance_full",
                        target.getGameProfile().getName()).withStyle(ChatFormatting.RED));
                return;
            }
            from.balance -= amount;
            to.balance += amount;
            from.lastTransferTick = now;
            data.setDirty();
            TransactionLog.record(player.server, player.getUUID(), TransactionLog.TRANSFER_OUT,
                    target.getGameProfile().getName(), -amount, from.balance, "");
            TransactionLog.record(player.server, target.getUUID(), TransactionLog.TRANSFER_IN,
                    player.getGameProfile().getName(), amount, to.balance, "");
        }
        // 需求一：转账模板固定为「已向 目标玩家名 转账 金额 金币」/「收到 目标玩家名 的转账 金额 金币」，
        // 目标名在前、金额在后，靠字段位置区分，不再靠“文本是不是数字”判断。
        notifyPlayer(player, Component.translatable("msg.server_shop_mod.transfer_sent",
                target.getGameProfile().getName(), amount).withStyle(ChatFormatting.GREEN));
        notifyPlayer(target, Component.translatable("msg.server_shop_mod.transfer_received",
                player.getGameProfile().getName(), amount).withStyle(ChatFormatting.GREEN));
        // 需求 4：收到转账后不再在转账按钮上显示提示（移除钱包红点），只保留聊天栏通知。
        sendSnapshot(player, "wallet");
        // 收款方的界面余额也要刷新，否则他看到的还是转账前的数字（大额转入后立刻消费会误判）。
        sendSnapshot(target, "wallet", true);
    }

    /** 需求 7：比较两名玩家的连接 IP。 */
    private static boolean sameIp(ServerPlayer a, ServerPlayer b) {
        String ipA = a.getIpAddress();
        String ipB = b.getIpAddress();
        return ipA != null && !ipA.isEmpty() && ipA.equals(ipB);
    }

    // =========================================================== 管理员配置

    /** 设置页“交易记录”按钮：把某玩家的流水发给管理员本人。 */
    private static void sendHistory(ServerPlayer admin, String targetName, int page) {
        List<Component> lines = historyLines(admin.server, targetName, page <= 0 ? 1 : page);
        if (lines.isEmpty()) {
            notifyPlayer(admin, Component.translatable("msg.server_shop_mod.grant_unknown", targetName)
                    .withStyle(ChatFormatting.RED));
            return;
        }
        for (Component line : lines) {
            notifyPlayer(admin, line);
        }
    }

    private static void adminSetPriceNew(ServerPlayer player, CompoundTag tag) {
        // 需求 11：模板来自幽灵槽，不是 PENDING_ITEM。
        ItemStack stack = ADMIN_TEMPLATE.remove(player.getUUID());
        if (stack == null || stack.isEmpty()) {
            return;
        }
        long price = parsePrice(tag.getString("price"));
        if (price <= 0) {
            // 幽灵槽只保存模板副本、原物已经退回管理员背包（见 ADMIN_TEMPLATE 注释），
            // 这里绝不能再把模板当真实物品发一次，否则输入一个非数字价格就能凭空复制一件物品。
            notifyPlayer(player, Component.translatable("msg.server_shop_mod.invalid_price").withStyle(ChatFormatting.RED));
            return;
        }
        boolean shop = isShopMode(player, tag);
        ShopSavedData data = data(player.server);
        // 需求 6/7：管理员可同时配置品质与数量（数量为空则退回默认上限）。
        ConfiguredOffer offer = new ConfiguredOffer(stack, price, parseRarity(tag.getString("rarity"), price));
        offer.amount = parseInt(tag.getString("quantity"));
        if (shop) {
            data.shopPool.add(offer);
        } else {
            data.buybackPool.add(offer);
        }
        data.setDirty();
        sendSnapshot(player, "admin");
    }

    private static void adminApplyPrice(ServerPlayer player, CompoundTag tag) {
        int index = tag.getInt("index");
        long price = parsePrice(tag.getString("price"));
        if (price <= 0) {
            return;
        }
        boolean shop = isShopMode(player, tag);
        ShopSavedData data = data(player.server);
        List<ConfiguredOffer> pool = shop ? data.shopPool : data.buybackPool;
        if (index < 0 || index >= pool.size()) {
            return;
        }
        ConfiguredOffer offer = pool.get(index);
        offer.price = price;
        // 需求 6/7：商店与收购都可单独设置品质、数量；品质留空时按价格推导。
        offer.rarity = parseRarity(tag.getString("rarity"), price);
        offer.amount = parseInt(tag.getString("quantity"));
        data.setDirty();
        sendSnapshot(player, "admin");
    }

    private static void adminRemove(ServerPlayer player, CompoundTag tag) {
        int index = tag.getInt("index");
        boolean shop = isShopMode(player, tag);
        ShopSavedData data = data(player.server);
        List<ConfiguredOffer> pool = shop ? data.shopPool : data.buybackPool;
        if (index < 0 || index >= pool.size()) {
            return;
        }
        // 池里的条目来自幽灵槽模板，原物始终留在管理员背包里；这里再邮寄一次等于复制物品。
        pool.remove(index);
        data.setDirty();
        sendSnapshot(player, "admin");
    }

    // =========================================================== 工具

    /**
     * 安全入账：只加“余额还能装得下”的部分，返回实际到账金额（饱和加法，永不溢出）。
     * <p>
     * 原先各处直接写 {@code Math.min(max, balance + amount)}：收款方到顶时差额被静默销毁，
     * 而且 amount 极大时还会先溢出成负数。所有入账都应走这里，差额由调用方退回或转为邮件。
     */
    public static long credit(PlayerAccount account, long amount, long max) {
        long room = Math.max(0L, max - account.balance);
        long add = Math.min(Math.max(0L, amount), room);
        account.balance += add;
        return add;
    }

    // =========================================================== 同 IP 多账号管控

    /** IP 作为 NBT 键要去掉 . 和 : （NBT 路径分隔符），统一替换成 _。 */
    public static String ipKey(String ip) {
        return ip == null ? "" : ip.replace('.', '_').replace(':', '_');
    }

    /**
     * 同 IP 多账号管控：一个 IP 只允许一个账号登录（0=关闭；2 级权限管理员按配置豁免）。
     * 第一次见到某 IP 时把当前账号绑定上去，之后同 IP 的其它账号会被踢下线。
     * 注意动态 IP/共享宽带的副作用，可用 {@code /shop admin ip unbind <玩家或IP>} 解绑。
     */
    public static void checkIpLimit(ServerPlayer player) {
        int limit = Config.INSTANCE.ipAccountLimit.get();
        if (limit <= 0) {
            return;
        }
        if (Config.INSTANCE.ipLimitExemptOps.get() && player.hasPermissions(2)) {
            return;
        }
        String ip = player.getIpAddress();
        if (ip == null || ip.isBlank()) {
            return;
        }
        ShopSavedData data = data(player.server);
        String key = ipKey(ip);
        UUID bound = data.ipOwner.get(key);
        if (bound == null) {
            data.ipOwner.put(key, player.getUUID());
            data.setDirty();
            return;
        }
        if (bound.equals(player.getUUID())) {
            return;
        }
        PlayerAccount other = data.peek(bound);
        String otherName = other == null || other.name == null || other.name.isEmpty()
                ? bound.toString() : other.name;
        player.connection.disconnect(Component.translatable("msg.server_shop_mod.ip_limit_kick", otherName));
    }

    /** 管理员：列出 IP → 账号 的绑定关系。 */
    public static List<String> ipBindings(MinecraftServer server) {
        ShopSavedData data = data(server);
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, UUID> entry : data.ipOwner.entrySet()) {
            PlayerAccount account = data.peek(entry.getValue());
            String name = account == null || account.name == null || account.name.isEmpty()
                    ? entry.getValue().toString() : account.name;
            lines.add(entry.getKey() + " -> " + name);
        }
        return lines;
    }

    /** 管理员：解除某个玩家名或某个 IP 的绑定；返回是否解除了至少一条。 */
    public static boolean ipUnbind(MinecraftServer server, String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        ShopSavedData data = data(server);
        if (data.ipOwner.remove(ipKey(text.trim())) != null) {
            data.setDirty();
            return true;
        }
        for (Map.Entry<String, UUID> entry : new ArrayList<>(data.ipOwner.entrySet())) {
            PlayerAccount account = data.peek(entry.getValue());
            String name = account == null ? "" : account.name;
            if (name != null && name.equalsIgnoreCase(text.trim())) {
                data.ipOwner.remove(entry.getKey());
                data.setDirty();
                return true;
            }
        }
        return false;
    }

    // =========================================================== 交易流水（管理员查看）

    /**
     * 取某玩家的交易流水渲染行（管理员用）。找不到该玩家时返回空列表。
     * 玩家名支持在线名与账户表里的历史名（含离线），也支持直接写 UUID。
     */
    public static List<Component> historyLines(MinecraftServer server, String targetName, int page) {
        GrantManager.ResolveResult resolved = GrantManager.resolveTargets(server, targetName);
        if (resolved.ids().isEmpty()) {
            return List.of();
        }
        UUID id = resolved.ids().get(0);
        PlayerAccount account = data(server).account(id);
        String display = account.name == null || account.name.isEmpty() ? targetName : account.name;
        return TransactionLog.format(account, display, page);
    }

    /** 管理员命令：直接发放星币（单目标，供 /shop wallet give 使用）。 */
    public static void adminGive(ServerPlayer target, long amount) {
        PlayerAccount account = account(target);
        long credited = credit(account, amount, Config.INSTANCE.maxBalance.get());
        data(target.server).setDirty();
        notifyPlayer(target, Component.translatable("msg.server_shop_mod.admin_gave", credited, account.balance)
                .withStyle(ChatFormatting.GREEN));
        TransactionLog.record(target.server, target.getUUID(), TransactionLog.ADMIN_MONEY, "管理员",
                credited, account.balance, "");
    }

    public static long balanceOf(ServerPlayer player) {
        return account(player).balance;
    }

    /** 只读余额（找不到账号返回 0，供流水记录使用）。 */
    private static long balanceOf(ShopSavedData data, UUID id) {
        PlayerAccount account = id == null ? null : data.peek(id);
        return account == null ? 0L : account.balance;
    }

    public static void onLogin(ServerPlayer player) {
        // 同 IP 多账号管控要在建档之前判断，避免脚本小号先把数据写进来。
        checkIpLimit(player);
        PlayerAccount account = account(player);
        // 需求 13：惰性刷新——登录时检查时间戳，过期才重新生成每日商店/收购，不做全局 tick 遍历。
        ensureDaily(player, account, data(player.server));
        // 需求 10：登录时把持久化的未读数量同步给客户端，红点不会因为重连丢失。
        NotificationManager.sendCounts(player, null);
        // 已领取/已查看的邮件仍保留在邮箱中，这里只统计未处理的邮件数量。
        long pendingMails = account.mailbox.stream().filter(MailEntry::pending).count();
        if (pendingMails > 0) {
            notifyPlayer(player, Component.translatable("msg.server_shop_mod.mail_waiting", pendingMails)
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    /** 需求 12：管理员指定某玩家刷新每日商店货物与收购商品种类（离线玩家下次登录生效）。 */
    public static void refreshDaily(ServerPlayer target) {
        PlayerAccount account = account(target);
        account.dailyShopDay = Long.MIN_VALUE;
        account.dailyBuybackDay = Long.MIN_VALUE;
        account.dailyFlipped.clear();
        data(target.server).setDirty();
        ensureDaily(target, account, data(target.server));
        sendSnapshot(target, "daily", true);
    }

    public static void onLogout(ServerPlayer player) {
        returnPending(player);
        TradeManager.onLogout(player);
        DEPOSIT_CONTEXT.remove(player.getUUID());
        ADMIN_EDIT_TYPE.remove(player.getUUID());
        ADMIN_TEMPLATE.remove(player.getUUID());
    }

    public static void addItemOrMail(ServerPlayer player, ItemStack stack, String source) {
        // Inventory.add 会把传入栈原地 shrink 成“没塞进去的剩余量”，必须保留剩余量，否则半组同类物品时会丢物品。
        ItemStack remaining = stack.copy();
        player.getInventory().add(remaining);
        if (remaining.isEmpty()) {
            // 需求一：物品获取日志模板「获得 物品名×数量」，物品名可为纯数字，数量单独作字段。
            notifyPlayer(player, Component.translatable("msg.server_shop_mod.received",
                    stack.getHoverName(), stack.getCount()).withStyle(ChatFormatting.GREEN));
            return;
        }
        addMail(player.server, player.getUUID(), remaining, source);
    }

    public static void addMail(MinecraftServer server, @Nullable UUID owner, ItemStack stack, String source) {
        addMail(server, owner, stack, source, "");
    }

    /** 需求：带发送者与发送者 UUID 的物品类邮件。 */
    public static void addMail(MinecraftServer server, @Nullable UUID owner, ItemStack stack, String source, String sender) {
        if (owner == null || stack.isEmpty()) {
            return;
        }
        ShopSavedData data = data(server);
        PlayerAccount account = data.account(owner);
        ServerPlayer online = server.getPlayerList().getPlayer(owner);
        int max = Config.INSTANCE.maxMailboxSize.get();
        // 同样要处理 Inventory.add 的“部分放入”，只把真正剩下的部分继续进邮箱，避免丢物品。
        ItemStack remaining = stack.copy();
        if (account.mailbox.size() >= max) {
            // 邮箱已满：优先塞背包，仍放不下的直接掉落在玩家脚下，既防止溢出刷物品也不丢系统资产。
            if (online != null) {
                online.getInventory().add(remaining);
                if (remaining.isEmpty()) {
                    notifyPlayer(online, Component.translatable("msg.server_shop_mod.received",
                            stack.getHoverName(), stack.getCount()).withStyle(ChatFormatting.GREEN));
                    data.setDirty();
                    return;
                }
                online.drop(remaining, false);
                notifyPlayer(online, Component.translatable("msg.server_shop_mod.mailbox_full")
                        .withStyle(ChatFormatting.RED));
                data.setDirty();
                return;
            }
            // 离线玩家无法掉落，为避免系统返还的物品彻底丢失，仍然保留在邮箱（超出上限仅限系统资产）。
        }
        account.mailbox.add(MailEntry.item(remaining, source, sender, null, currentDay(server.overworld())));
        data.setDirty();
        // 需求 10：收到新物品（交易返还/管理员发放等）→ 邮箱提示红点。
        NotificationManager.notify(server, owner, NotificationType.MAIL);
        if (online != null) {
            notifyPlayer(online, Component.translatable("msg.server_shop_mod.mail_received", stack.getHoverName())
                    .withStyle(ChatFormatting.GOLD));
            online.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0F, 1.5F);
        }
    }

    /**
     * 需求：发送一封金额类邮件（拍卖成交/摊位售出所得）。
     * 邮箱满时金额不能丢，直接进账并提示，避免玩家资产凭空消失。
     */
    public static void addMoneyMail(MinecraftServer server, @Nullable UUID owner, long amount, ItemStack display,
            String source, String sender, @Nullable UUID senderUUID) {
        if (owner == null || amount <= 0L) {
            return;
        }
        ShopSavedData data = data(server);
        PlayerAccount account = data.account(owner);
        ServerPlayer online = server.getPlayerList().getPlayer(owner);
        if (account.mailbox.size() >= Config.INSTANCE.maxMailboxSize.get()) {
            // 邮箱满：先把能装下的直接到账，余额也装不下的部分仍然留在邮箱（超过上限），绝不销毁。
            long credited = credit(account, amount, Config.INSTANCE.maxBalance.get());
            long rest = amount - credited;
            if (rest > 0L) {
                account.mailbox.add(MailEntry.money(rest, display, source, sender, senderUUID,
                        currentDay(server.overworld())));
                NotificationManager.notify(server, owner, NotificationType.MAIL);
            }
            data.setDirty();
            if (online != null) {
                notifyPlayer(online, Component.translatable("msg.server_shop_mod.mailbox_full_money", credited)
                        .withStyle(ChatFormatting.GOLD));
            }
            return;
        }
        account.mailbox.add(MailEntry.money(amount, display, source, sender, senderUUID,
                currentDay(server.overworld())));
        data.setDirty();
        NotificationManager.notify(server, owner, NotificationType.MAIL);
        if (online != null) {
            notifyPlayer(online, Component.translatable("msg.server_shop_mod.mail_received_money", amount)
                    .withStyle(ChatFormatting.GOLD));
            online.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0F, 1.5F);
        }
    }

    private static void notifyPlayer(ServerPlayer player, Component message) {
        player.displayClientMessage(message, false);
    }

    /** 需求 2：拍卖手续费 = 成交价 × 配置百分比（向上取整）。 */
    private static long auctionFee(long amount) {
        double percent = Config.INSTANCE.auctionFeePercent.get();
        return percent <= 0.0D ? 0L : (long) Math.ceil(amount * percent / 100.0D);
    }

    private static long parsePositive(String text) {
        try {
            long value = Long.parseLong(text.trim());
            return Math.max(0L, value);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    /**
     * 解析“价格”：非正数、或超过余额上限（谁也付不起）一律返回 0 视为非法。
     * 之所以要加上界：价格会参与 price×count 运算，天文数字会让乘法溢出成负数，
     * 进而使“余额不足”判定失效、扣款变成加钱（刷钱漏洞）。
     */
    private static long parsePrice(String text) {
        long price = parsePositive(text);
        return price > Config.INSTANCE.maxBalance.get() ? 0L : price;
    }

    /** 乘法溢出保护：溢出或非正数统一返回 0，调用方按“价格非法”处理。 */
    private static long safeMultiply(long price, int count) {
        if (price <= 0L || count <= 0) {
            return 0L;
        }
        return price > Long.MAX_VALUE / count ? 0L : price * count;
    }

    /**
     * 交付 count 件物品：按单栈上限拆成多份，先塞背包，塞不下的进邮箱（邮箱满由 addMail 兜底）。
     *
     * @return 是否有物品进了邮箱
     */
    private static boolean deliver(ServerPlayer player, ItemStack template, int count, String source) {
        int per = Math.max(1, template.getMaxStackSize());
        int left = count;
        boolean mailed = false;
        while (left > 0) {
            ItemStack part = template.copy();
            int give = Math.min(per, left);
            part.setCount(give);
            left -= give;
            player.getInventory().add(part);
            if (!part.isEmpty()) {
                addMail(player.server, player.getUUID(), part, source);
                mailed = true;
            }
        }
        return mailed;
    }

    private static int parseInt(String text) {
        try {
            return Math.max(0, Integer.parseInt(text.trim()));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /** 需求 6/7：解析管理员输入的品质，无法识别（含留空）时按价格推导。 */
    private static Rarity parseRarity(String text, long price) {
        Rarity rarity = Rarity.byIdOrNull(text);
        return rarity != null ? rarity : Rarity.fromPrice(price);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ServerShopMod.MODID, path);
    }
}
