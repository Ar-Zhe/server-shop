package cn.autoforged.server_shop_mod_1789689358.shop;

import cn.autoforged.server_shop_mod_1789689358.Config;
import cn.autoforged.server_shop_mod_1789689358.data.NotificationType;
import cn.autoforged.server_shop_mod_1789689358.data.PlayerAccount;
import cn.autoforged.server_shop_mod_1789689358.data.ShopNbt;
import cn.autoforged.server_shop_mod_1789689358.network.payload.ClientboundShopSyncPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 玩家间交易（服务端权威）。
 * 流程：双方各自放入物品/选择金额 -> 双方点击“同意” -> 现实时间倒计时（默认 3 秒）
 * -> 双方再次点击“确定交换” -> 服务端执行物品与星币互换。
 * 物品在交易完成前由本会话持有，取消/掉线时原路退回，避免丢失。
 */
public final class TradeManager {
    private static final String SOURCE_TRADE = "trade";
    /** 需求 1：交易界面槽位由 9 个改为 6 个，每方最多放入 6 件物品。 */
    public static final int MAX_ITEMS = 6;

    private static final Map<Long, TradeSession> SESSIONS = new HashMap<>();
    /** 已进入交易界面的玩家（双方都接受后）。 */
    private static final Map<UUID, Long> BY_PLAYER = new HashMap<>();
    /** 发起方（等待对方同意期间），用于定位“我发出的请求”。 */
    private static final Map<UUID, Long> OUTGOING = new HashMap<>();
    private static long nextId = 1L;

    private TradeManager() {
    }

    /** 发起交易：target 为对方玩家名。需求 2：发起方直接进入交易界面等待对方同意。 */
    public static void request(ServerPlayer player, String targetName) {
        if (targetName == null || targetName.isBlank()) {
            return;
        }
        ServerPlayer target = player.server.getPlayerList().getPlayerByName(targetName.trim());
        if (target == null) {
            notify(player, Component.translatable("msg.server_shop_mod.target_offline").withStyle(ChatFormatting.RED));
            return;
        }
        if (target == player) {
            return;
        }
        if (busy(player) || BY_PLAYER.containsKey(target.getUUID())) {
            notify(player, Component.translatable("msg.server_shop_mod.trade_busy").withStyle(ChatFormatting.RED));
            return;
        }
        TradeSession session = new TradeSession();
        session.id = nextId++;
        session.playerA = player.getUUID();
        session.nameA = player.getGameProfile().getName();
        session.playerB = target.getUUID();
        session.nameB = target.getGameProfile().getName();
        // 需求 1/2：请求先处于“待同意”状态，对方同意后才进入正式交易。
        session.accepted = false;
        session.requestedAt = System.currentTimeMillis();
        touch(session);
        SESSIONS.put(session.id, session);
        OUTGOING.put(session.playerA, session.id);
        notify(player, Component.translatable("msg.server_shop_mod.trade_request_sent", session.nameB)
                .withStyle(ChatFormatting.GREEN));
        // 需求 2：发起方留在交易界面等待对方同意。
        send(session, player, true);
        Component prompt = Component.translatable("msg.server_shop_mod.trade_invite_click", session.nameA)
                .withStyle(ChatFormatting.GOLD)
                .withStyle(style -> style.withClickEvent(new net.minecraft.network.chat.ClickEvent(
                        net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/shop trade open")));
        notify(target, prompt);
        sendRequestList(target, false);
        // 需求 10：收到其他玩家的交易请求 → 交易按钮提示红点。
        NotificationManager.notify(target.server, target.getUUID(), NotificationType.TRADE);
    }

    /** 需求 2：被邀请方点击聊天提示/列表后进入交易界面；若已有待同意请求则打开请求列表。 */
    public static void openFor(ServerPlayer player) {
        TradeSession session = sessionOf(player);
        if (session != null) {
            send(session, player, true);
            return;
        }
        sendRequestList(player, true);
    }

    /** 需求 1：把“收到的交易请求”列表下发给客户端；forceOpen 时打开请求列表界面。 */
    public static void sendRequestList(ServerPlayer player, boolean forceOpen) {
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", "trade_requests");
        tag.putBoolean("forceOpen", forceOpen);
        ListTag list = new ListTag();
        for (TradeSession session : SESSIONS.values()) {
            if (!session.playerB.equals(player.getUUID()) || session.accepted) {
                continue;
            }
            CompoundTag c = new CompoundTag();
            c.putLong("id", session.id);
            c.putString("from", session.nameA);
            c.putLong("age", Math.max(0L, System.currentTimeMillis() - session.requestedAt));
            list.add(c);
        }
        tag.put("requests", list);
        PacketDistributor.sendToPlayer(player, new ClientboundShopSyncPayload(tag));
    }

    /** 需求 2：同意收到的交易请求，双方进入正式交易界面。 */
    public static void acceptRequest(ServerPlayer player, long id) {
        TradeSession session = SESSIONS.get(id);
        if (session == null || !session.playerB.equals(player.getUUID()) || session.accepted) {
            return;
        }
        if (busy(player)) {
            notify(player, Component.translatable("msg.server_shop_mod.trade_busy").withStyle(ChatFormatting.RED));
            return;
        }
        ServerPlayer a = player.server.getPlayerList().getPlayer(session.playerA);
        // 需求二修复：OUTGOING 存的是 Long，session.playerA 是 UUID，不能用 UUID.equals(Long)
        // （永远为 false，会导致接受请求被误判为失效而走取消/拒绝逻辑）。改为比较会话 id。
        Long outgoingId = OUTGOING.get(session.playerA);
        if (a == null || outgoingId == null || outgoingId.longValue() != session.id) {
            cancelInternal(session, player.server, true);
            return;
        }
        session.accepted = true;
        touch(session);
        OUTGOING.remove(session.playerA);
        // 同一玩家可能收到多个请求，接受一个后清理其余待同意请求。
        // 必须先清理再写 BY_PLAYER：cancelInternal 会移除被取消会话双方的映射，
        // 若顺序反了，会把刚写好的“接受方 → 本会话”映射一并删掉，
        // 于是接受方点“同意/确定交换”全部静默失效（sessionOf 返回 null），交易永久卡死。
        for (TradeSession other : new ArrayList<>(SESSIONS.values())) {
            if (other.id != session.id && !other.accepted && other.playerB.equals(player.getUUID())) {
                cancelInternal(other, player.server, true);
            }
        }
        BY_PLAYER.put(session.playerA, session.id);
        BY_PLAYER.put(session.playerB, session.id);
        notify(player, Component.translatable("msg.server_shop_mod.trade_accepted", session.nameA)
                .withStyle(ChatFormatting.GREEN));
        // 需求二：交易模块聊天日志——发起方也收到“交易已开始”提示。
        notify(a, Component.translatable("msg.server_shop_mod.trade_started", session.nameB)
                .withStyle(ChatFormatting.GREEN));
        // 双方都进入交易界面（A 从等待态切换为可交互）。
        send(session, a, true);
        send(session, player, true);
        sendRequestList(player, false);
    }

    /** 需求 1：拒绝/忽略一个交易请求。 */
    public static void declineRequest(ServerPlayer player, long id) {
        TradeSession session = SESSIONS.get(id);
        if (session == null || session.accepted || !session.playerB.equals(player.getUUID())) {
            return;
        }
        // 需求二：交易模块聊天日志——双方都收到“拒绝/被拒绝”记录。
        String requesterName = session.nameA;
        ServerPlayer requester = player.server.getPlayerList().getPlayer(session.playerA);
        notify(player, Component.translatable("msg.server_shop_mod.trade_declined", requesterName)
                .withStyle(ChatFormatting.YELLOW));
        if (requester != null) {
            notify(requester, Component.translatable("msg.server_shop_mod.trade_request_declined", session.nameB)
                    .withStyle(ChatFormatting.YELLOW));
        }
        cancelInternal(session, player.server, true);
        sendRequestList(player, false);
    }

    /** 通过 DepositMenu 放入一件物品。 */
    public static void addItem(ServerPlayer player, ItemStack stack) {
        TradeSession session = sessionOf(player);
        if (session == null || stack.isEmpty()) {
            if (!stack.isEmpty()) {
                ShopManager.addItemOrMail(player, stack, SOURCE_TRADE);
            }
            return;
        }
        // 已进入倒计时/确认阶段，或对方还没同意请求时，不接受改动并退回物品。
        if (session.readyAt > 0 || !session.accepted) {
            ShopManager.addItemOrMail(player, stack, SOURCE_TRADE);
            // 需求 1（bug 修复）：即便物品被退回，也要让玩家回到交易界面而不是停在空屏。
            broadcastTo(session, player.server, player);
            return;
        }
        // 需求 2：网络差导致玩家在槽位已满时仍进入“放入物品”界面，服务端再次校验并拒收。
        if (itemsFor(session, player).size() >= MAX_ITEMS) {
            notify(player, Component.translatable("msg.server_shop_mod.trade_full").withStyle(ChatFormatting.RED));
            ShopManager.addItemOrMail(player, stack, SOURCE_TRADE);
            broadcastTo(session, player.server, player);
            return;
        }
        // 需求四：容器类物品（潜影盒/背包等）按完整 NBT 深拷贝，保留内容物与全部组件。
        itemsFor(session, player).add(ShopNbt.deepCopy(stack, player.level().registryAccess()));
        touch(session);
        // 需求 1（bug 修复）：服务端关闭“放入物品”容器后客户端屏幕会被直接清空，
        // 这里强制重新打开交易界面，保证确认/取消后都能回到交易界面。
        broadcastTo(session, player.server, player);
    }

    /** 需求 3：玩家从自己的交易区域删除已放入的物品，原路返还背包（背包满转邮箱）。 */
    public static void removeItem(ServerPlayer player, int index) {
        TradeSession session = sessionOf(player);
        if (session == null || session.readyAt > 0 || !session.accepted) {
            return;
        }
        List<ItemStack> items = itemsFor(session, player);
        if (index < 0 || index >= items.size()) {
            return;
        }
        ItemStack removed = items.remove(index);
        ShopManager.addItemOrMail(player, removed, SOURCE_TRADE);
        touch(session);
        broadcast(session, player.server);
    }

    /** 需求 2：判断某方是否已经放满 6 个交易槽位（用于锁定“放入物品”入口）。 */
    public static boolean itemsFull(ServerPlayer player) {
        TradeSession session = sessionOf(player);
        if (session == null || !session.accepted || session.readyAt > 0) {
            return false;
        }
        return itemsFor(session, player).size() >= MAX_ITEMS;
    }

    /** 设定本方的星币数额（不超过当前余额）。 */
    public static void setMoney(ServerPlayer player, long amount) {
        TradeSession session = sessionOf(player);
        if (session == null || session.readyAt > 0 || !session.accepted) {
            return;
        }
        long balance = ShopManager.account(player).balance;
        long clamped = Math.max(0L, Math.min(amount, balance));
        if (isA(session, player)) {
            session.moneyA = clamped;
        } else {
            session.moneyB = clamped;
        }
        touch(session);
        // 需求 1（bug 修复）：金额输入界面是独立 Screen，确认/取消后需要回到交易界面。
        broadcastTo(session, player.server, player);
    }

    public static void toggleAgree(ServerPlayer player) {
        TradeSession session = sessionOf(player);
        if (session == null || !session.accepted) {
            return;
        }
        if (isA(session, player)) {
            session.agreedA = !session.agreedA;
        } else {
            session.agreedB = !session.agreedB;
        }
        // 任何改动都重置确认与倒计时
        session.confirmedA = false;
        session.confirmedB = false;
        session.readySent = false;
        touch(session);
        if (session.agreedA && session.agreedB) {
            if (session.readyAt <= 0) {
                session.readyAt = System.currentTimeMillis()
                        + Config.INSTANCE.tradeCountdownSeconds.get() * 1000L;
            }
        } else {
            session.readyAt = 0;
        }
        broadcast(session, player.server);
    }

    public static void confirm(ServerPlayer player) {
        TradeSession session = sessionOf(player);
        if (session == null || !session.accepted) {
            return;
        }
        if (!(session.agreedA && session.agreedB)) {
            return;
        }
        if (session.readyAt <= 0 || System.currentTimeMillis() < session.readyAt) {
            return;
        }
        if (isA(session, player)) {
            session.confirmedA = true;
        } else {
            session.confirmedB = true;
        }
        touch(session);
        if (session.confirmedA && session.confirmedB) {
            execute(session, player.server);
        } else {
            broadcast(session, player.server);
        }
    }

    public static void cancel(ServerPlayer player, boolean notifyBoth) {
        // 需求 2：发起方在等待期间退出界面即代表取消交易请求。
        TradeSession session = sessionOf(player);
        if (session == null) {
            session = outgoingOf(player);
        }
        if (session != null) {
            cancelInternal(session, player.server, notifyBoth);
        }
    }

    public static void onLogout(ServerPlayer player) {
        TradeSession session = sessionOf(player);
        if (session == null) {
            session = outgoingOf(player);
        }
        if (session != null) {
            cancelInternal(session, player.server, true);
        }
    }

    /** 服务器停机前把未完成交易里的物品原路退回，保证随世界保存落盘。 */
    public static void shutdown(MinecraftServer server) {
        for (TradeSession session : new ArrayList<>(SESSIONS.values())) {
            cancelInternal(session, server, false);
        }
    }

    /** 每 tick 刷新倒计时显示。 */
    public static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (TradeSession session : new ArrayList<>(SESSIONS.values())) {
            // 需求 1：未被接受的交易请求超时自动清理。
            if (!session.accepted) {
                long expire = Config.INSTANCE.tradeRequestExpireSeconds.get() * 1000L;
                if (now - session.requestedAt >= expire) {
                    ServerPlayer target = server.getPlayerList().getPlayer(session.playerB);
                    cancelInternal(session, server, true);
                    if (target != null) {
                        sendRequestList(target, false);
                    }
                }
                continue;
            }
            // 已接受的交易：闲置超时自动结束并退回物品/金币。
            // 界面被顶掉（死亡、切界面）、双方挂机都靠这里兜底，避免物品被永久冻结在会话里。
            long timeoutMs = Config.INSTANCE.tradeSessionTimeoutSeconds.get() * 1000L;
            long idleSince = Math.max(session.lastActivityMs, session.requestedAt);
            if (timeoutMs > 0L && now - idleSince >= timeoutMs) {
                ServerPlayer a = server.getPlayerList().getPlayer(session.playerA);
                ServerPlayer b = server.getPlayerList().getPlayer(session.playerB);
                // 退回规则：在线先回背包、背包放不下转邮箱；离线直接进邮箱（见 returnItem/addItemOrMail）。
                cancelInternal(session, server, true);
                Component reason = Component.translatable("msg.server_shop_mod.trade_timeout")
                        .withStyle(ChatFormatting.YELLOW);
                if (a != null) {
                    notify(a, reason);
                }
                if (b != null) {
                    notify(b, reason);
                }
                continue;
            }
            if (session.readyAt <= 0) {
                continue;
            }
            if (now < session.readyAt) {
                // 倒计时中：约每 200ms 刷新一次显示
                if (now - session.lastBroadcast >= 200L) {
                    session.lastBroadcast = now;
                    broadcast(session, server);
                }
            } else if (!session.readySent) {
                // 倒计时归零只提示一次，避免无限广播
                session.readySent = true;
                broadcast(session, server);
            }
        }
    }

    // =========================================================== 执行与取消

    private static void execute(TradeSession session, MinecraftServer server) {
        // 需求 3/8：交易执行（扣星币、发物品）全部在服务端锁内完成，并再次校验余额。
        synchronized (ShopManager.LOCK) {
            ServerPlayer a = server.getPlayerList().getPlayer(session.playerA);
            ServerPlayer b = server.getPlayerList().getPlayer(session.playerB);
            if (a == null || b == null) {
                cancelInternal(session, server, true);
                return;
            }
            PlayerAccount accA = ShopManager.account(a);
            PlayerAccount accB = ShopManager.account(b);
            if (accA.balance < session.moneyA || accB.balance < session.moneyB) {
                notify(a, Component.translatable("msg.server_shop_mod.no_money").withStyle(ChatFormatting.RED));
                notify(b, Component.translatable("msg.server_shop_mod.no_money").withStyle(ChatFormatting.RED));
                session.confirmedA = false;
                session.confirmedB = false;
                broadcast(session, server);
                return;
            }
            long max = Config.INSTANCE.maxBalance.get();
            // 收款方余额装不下就先不执行：否则一方已扣、另一方只进一部分，差额凭空消失。
            if (Math.max(0L, max - accB.balance) < session.moneyA
                    || Math.max(0L, max - accA.balance) < session.moneyB) {
                notify(a, Component.translatable("msg.server_shop_mod.trade_balance_full").withStyle(ChatFormatting.RED));
                notify(b, Component.translatable("msg.server_shop_mod.trade_balance_full").withStyle(ChatFormatting.RED));
                session.confirmedA = false;
                session.confirmedB = false;
                broadcast(session, server);
                return;
            }
            accA.balance -= session.moneyA;
            accB.balance += session.moneyA;
            accB.balance -= session.moneyB;
            accA.balance += session.moneyB;
            ShopManager.data(server).setDirty();
            // 流水：双方各记一条（金币净额 + 自己给出的物品摘要）。
            TransactionLog.record(server, session.playerA, TransactionLog.TRADE_OUT, session.nameB,
                    session.moneyB - session.moneyA, accA.balance, itemSummary(session.itemsA));
            TransactionLog.record(server, session.playerB, TransactionLog.TRADE_IN, session.nameA,
                    session.moneyA - session.moneyB, accB.balance, itemSummary(session.itemsB));

            // 需求 10：物品互换直接拷贝 ItemStack，A 的给 B、B 的给 A，保留完整 NBT/组件。
            // 需求四：容器类物品再走一次深拷贝，确保内容物完整交接且与原栈无共享引用。
            for (ItemStack stack : session.itemsA) {
                ShopManager.addItemOrMail(b, ShopNbt.deepCopy(stack, server.registryAccess()), SOURCE_TRADE);
            }
            for (ItemStack stack : session.itemsB) {
                ShopManager.addItemOrMail(a, ShopNbt.deepCopy(stack, server.registryAccess()), SOURCE_TRADE);
            }

            SESSIONS.remove(session.id);
            BY_PLAYER.remove(session.playerA);
            BY_PLAYER.remove(session.playerB);
            OUTGOING.remove(session.playerA);
            // 需求二：完成日志附带金额字段（获得的金币数），物品获取由 addItemOrMail 单独按模板输出。
            notify(a, Component.translatable("msg.server_shop_mod.trade_done", session.nameB, session.moneyB)
                    .withStyle(ChatFormatting.GREEN));
            notify(b, Component.translatable("msg.server_shop_mod.trade_done", session.nameA, session.moneyA)
                    .withStyle(ChatFormatting.GREEN));
            sendClosed(a);
            sendClosed(b);
        }
    }

    private static void cancelInternal(TradeSession session, MinecraftServer server, boolean notifyBoth) {
        ServerPlayer a = server.getPlayerList().getPlayer(session.playerA);
        ServerPlayer b = server.getPlayerList().getPlayer(session.playerB);
        for (ItemStack stack : session.itemsA) {
            returnItem(server, a, session.playerA, stack);
        }
        for (ItemStack stack : session.itemsB) {
            returnItem(server, b, session.playerB, stack);
        }
        SESSIONS.remove(session.id);
        // 只移除“仍然指向本会话”的映射：同一玩家可能已经开了新会话，不能无差别删除。
        BY_PLAYER.remove(session.playerA, session.id);
        BY_PLAYER.remove(session.playerB, session.id);
        OUTGOING.remove(session.playerA);
        if (notifyBoth) {
            if (a != null) {
                notify(a, Component.translatable("msg.server_shop_mod.trade_cancelled").withStyle(ChatFormatting.YELLOW));
                sendClosed(a);
            }
            if (b != null) {
                notify(b, Component.translatable("msg.server_shop_mod.trade_cancelled").withStyle(ChatFormatting.YELLOW));
                sendClosed(b);
                // 刷新对方的交易请求列表。
                sendRequestList(b, false);
            }
        }
    }

    private static void returnItem(MinecraftServer server, ServerPlayer online, UUID owner, ItemStack stack) {
        if (online != null) {
            ShopManager.addItemOrMail(online, stack.copy(), SOURCE_TRADE);
        } else {
            ShopManager.addMail(server, owner, stack.copy(), SOURCE_TRADE);
        }
    }

    // =========================================================== 快照

    private static void broadcast(TradeSession session, MinecraftServer server) {
        ServerPlayer a = server.getPlayerList().getPlayer(session.playerA);
        ServerPlayer b = server.getPlayerList().getPlayer(session.playerB);
        if (a != null) {
            send(session, a, false);
        }
        if (b != null) {
            send(session, b, false);
        }
    }

    /**
     * 广播给双方，但强制重新打开发起本次操作玩家的交易界面（forceOpen=true）。
     * 需求 1（bug 修复）：放入物品/设置金额分别在容器界面与独立输入界面完成，
     * 这两个界面关闭时不会自动回到交易界面，靠服务端强制重开兜底。
     */
    private static void broadcastTo(TradeSession session, MinecraftServer server, ServerPlayer acting) {
        ServerPlayer a = server.getPlayerList().getPlayer(session.playerA);
        ServerPlayer b = server.getPlayerList().getPlayer(session.playerB);
        UUID actingId = acting.getUUID();
        if (a != null) {
            send(session, a, session.playerA.equals(actingId));
        }
        if (b != null) {
            send(session, b, session.playerB.equals(actingId));
        }
    }

    /**
     * forceOpen=true 时才让客户端主动打开交易界面；普通状态更新只刷新缓存，
     * 这样需求 3 里被邀请方不会被自动拽进界面，必须点击聊天提示才跳转。
     */
    private static void send(TradeSession session, ServerPlayer viewer, boolean forceOpen) {
        boolean selfIsA = isA(session, viewer);
        HolderLookup.Provider regs = viewer.level().registryAccess();
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", "trade");
        tag.putBoolean("forceOpen", forceOpen);
        // 需求 2：未同意前发起方看到对方那侧灰色并提示“等待对方同意”。
        tag.putBoolean("accepted", session.accepted);
        tag.putBoolean("requester", selfIsA);
        tag.putString("selfName", selfIsA ? session.nameA : session.nameB);
        tag.putString("otherName", selfIsA ? session.nameB : session.nameA);
        tag.put("selfItems", saveItems(selfIsA ? session.itemsA : session.itemsB, regs));
        tag.put("otherItems", saveItems(selfIsA ? session.itemsB : session.itemsA, regs));
        tag.putLong("selfMoney", selfIsA ? session.moneyA : session.moneyB);
        tag.putLong("otherMoney", selfIsA ? session.moneyB : session.moneyA);
        tag.putBoolean("selfAgreed", selfIsA ? session.agreedA : session.agreedB);
        tag.putBoolean("otherAgreed", selfIsA ? session.agreedB : session.agreedA);
        tag.putBoolean("selfConfirmed", selfIsA ? session.confirmedA : session.confirmedB);
        tag.putBoolean("otherConfirmed", selfIsA ? session.confirmedB : session.confirmedA);
        tag.putBoolean("agreedBoth", session.agreedA && session.agreedB);
        tag.putLong("countdown", session.readyAt > 0
                ? Math.max(0L, session.readyAt - System.currentTimeMillis()) : 0L);
        PacketDistributor.sendToPlayer(viewer, new ClientboundShopSyncPayload(tag));
    }

    private static void sendClosed(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", "trade_closed");
        PacketDistributor.sendToPlayer(player, new ClientboundShopSyncPayload(tag));
    }

    private static ListTag saveItems(List<ItemStack> items, HolderLookup.Provider regs) {
        ListTag list = new ListTag();
        for (ItemStack stack : items) {
            list.add(ShopNbt.saveStack(stack, regs));
        }
        return list;
    }

    // =========================================================== 工具

    private static TradeSession sessionOf(ServerPlayer player) {
        Long id = BY_PLAYER.get(player.getUUID());
        return id == null ? null : SESSIONS.get(id);
    }

    /** 我发出、等待对方同意的请求。 */
    private static TradeSession outgoingOf(ServerPlayer player) {
        Long id = OUTGOING.get(player.getUUID());
        return id == null ? null : SESSIONS.get(id);
    }

    /** 是否已经在某笔交易中（等待或被接受）。 */
    private static boolean busy(ServerPlayer player) {
        return BY_PLAYER.containsKey(player.getUUID()) || OUTGOING.containsKey(player.getUUID());
    }

    private static boolean isA(TradeSession session, ServerPlayer player) {
        return session.playerA.equals(player.getUUID());
    }

    /** 记录一次操作时间，用于闲置超时判定。 */
    private static void touch(TradeSession session) {
        session.lastActivityMs = System.currentTimeMillis();
    }

    /** 交易物品的摘要文本（用于流水记录，最多列 3 件）。 */
    private static String itemSummary(List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int shown = Math.min(3, items.size());
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                sb.append("、");
            }
            sb.append(TransactionLog.itemText(items.get(i)));
        }
        if (items.size() > shown) {
            sb.append(" 等").append(items.size()).append("件");
        }
        return sb.toString();
    }

    private static List<ItemStack> itemsFor(TradeSession session, ServerPlayer player) {
        return isA(session, player) ? session.itemsA : session.itemsB;
    }

    private static void notify(ServerPlayer player, Component message) {
        player.displayClientMessage(message, false);
    }

    /** 一次交易会话的完整状态。 */
    private static final class TradeSession {
        private long id;
        private UUID playerA;
        private UUID playerB;
        private String nameA = "";
        private String nameB = "";
        /** 需求 2：对方是否已同意交易请求。 */
        private boolean accepted;
        private long requestedAt;
        private final List<ItemStack> itemsA = new ArrayList<>();
        private final List<ItemStack> itemsB = new ArrayList<>();
        private long moneyA;
        private long moneyB;
        private boolean agreedA;
        private boolean agreedB;
        private boolean confirmedA;
        private boolean confirmedB;
        private long readyAt;
        private long lastBroadcast;
        private boolean readySent;
        /** 最近一次操作时间：用于“已接受的交易闲置超时自动结束并退回物品”。 */
        private long lastActivityMs;
    }
}
