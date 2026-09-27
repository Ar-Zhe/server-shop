package cn.autoforged.server_shop_mod_1789689358.shop;

import cn.autoforged.server_shop_mod_1789689358.Config;
import cn.autoforged.server_shop_mod_1789689358.data.PlayerAccount;
import cn.autoforged.server_shop_mod_1789689358.data.ShopSavedData;
import cn.autoforged.server_shop_mod_1789689358.menu.GrantMenu;
import cn.autoforged.server_shop_mod_1789689358.network.payload.ClientboundShopSyncPayload;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 管理员发放（发金币 / 发物品）的服务端逻辑。
 * <p>
 * 两条入口共用同一套发放代码：
 * <ul>
 *     <li>{@code /shop admin money <金额> [目标]}：直接给（在线 + 历史离线）玩家加余额；</li>
 *     <li>{@code /shop admin send}：打开发放界面（{@link GrantMenu}），管理员放入物品、勾选玩家或全体后发送，
 *     物品以邮件形式进入玩家邮箱，玩家在邮箱界面点击“领取”。</li>
 * </ul>
 * 所有数值均由服务端结算：界面里的物品存在服务端的 {@link Container} 里，客户端只提交“发给谁 / 发多少钱”。
 */
public final class GrantManager {
    /** 发放界面的物品槽数量（3 行 × 9）。 */
    public static final int GRANT_SLOTS = GrantMenu.GRANT_SLOTS;
    /** 发放邮件在邮箱里的来源标记（客户端据此显示邮件描述）。 */
    public static final String SOURCE_ADMIN_GRANT = "admin_grant";
    /**
     * 发放界面里的金额是“直接到账”（与 /shop admin money 指令一致，默认）。
     * 改成 true 则金额改为金额类邮件，玩家需要在邮箱里点“领取”才进钱包。
     */
    private static final boolean GRANT_MONEY_VIA_MAIL = false;

    /**
     * 安全入账直接复用 {@link ShopManager#credit}（饱和加法），这里不再重复实现。
     */
    private GrantManager() {
    }

    // =========================================================== 目标列表

    /** 发放界面的一个可选目标。 */
    public record Target(UUID uuid, String name, boolean online) {
    }

    /**
     * 所有可发放的玩家：模组账户表（进过服的玩家，含离线）+ 当前在线玩家。
     * 在线排前面，其余按名字排序，便于界面翻页查找。
     */
    public static List<Target> targets(MinecraftServer server) {
        ShopSavedData data = ShopManager.data(server);
        List<Target> result = new ArrayList<>();
        for (Map.Entry<UUID, PlayerAccount> entry : data.accounts().entrySet()) {
            String name = entry.getValue().name == null ? "" : entry.getValue().name;
            boolean online = server.getPlayerList().getPlayer(entry.getKey()) != null;
            result.add(new Target(entry.getKey(), name.isEmpty() ? "Unknown" : name, online));
        }
        // 账户表里理论上包含所有在线玩家，这里兜底补一遍，避免新玩家尚未建档时列表里看不到自己。
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (data.peek(online.getUUID()) == null) {
                result.add(new Target(online.getUUID(), online.getGameProfile().getName(), true));
            }
        }
        result.sort(Comparator.comparing(Target::online).reversed()
                .thenComparing(target -> target.name().toLowerCase()));
        return result;
    }

    /** 所有已知玩家 UUID（“全体发放”的权威集合）。 */
    public static List<UUID> allKnownTargets(MinecraftServer server) {
        List<UUID> ids = new ArrayList<>();
        ShopSavedData data = ShopManager.data(server);
        ids.addAll(data.accounts().keySet());
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (!ids.contains(online.getUUID())) {
                ids.add(online.getUUID());
            }
        }
        return ids;
    }

    /** 目标解析结果：命中的账户 + 没找到的名字。 */
    public record ResolveResult(List<UUID> ids, List<String> unknown) {
    }

    /**
     * 把指令里的目标文本解析成账户 UUID。支持：
     * <ul>
     *     <li>玩家名（先匹配在线玩家，再按账户表名字匹配 → 离线玩家也能收到钱）；</li>
     *     <li>UUID 字符串；</li>
     *     <li>{@code all} / {@code @a} / {@code *} → 所有已知玩家。</li>
     * </ul>
     * 名字之间用空格或逗号分隔；没匹配到的名字回传给调用方提示管理员。
     */
    public static ResolveResult resolveTargets(MinecraftServer server, String text) {
        ShopSavedData data = ShopManager.data(server);
        List<UUID> ids = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        if (text != null) {
            for (String raw : text.split("[\\s,，]+")) {
                String token = raw.trim();
                if (token.isEmpty()) {
                    continue;
                }
                if ("all".equalsIgnoreCase(token) || "@a".equalsIgnoreCase(token) || "*".equals(token)) {
                    for (UUID id : allKnownTargets(server)) {
                        if (!ids.contains(id)) {
                            ids.add(id);
                        }
                    }
                    continue;
                }
                UUID matched = matchTarget(server, data, token);
                if (matched == null) {
                    unknown.add(token);
                } else if (!ids.contains(matched)) {
                    ids.add(matched);
                }
            }
        }
        return new ResolveResult(ids, unknown);
    }

    @Nullable
    private static UUID matchTarget(MinecraftServer server, ShopSavedData data, String token) {
        // 1) 直接给 UUID
        try {
            UUID id = UUID.fromString(token);
            if (data.peek(id) != null || server.getPlayerList().getPlayer(id) != null) {
                return id;
            }
        } catch (IllegalArgumentException ignored) {
        }
        // 2) 在线玩家名
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (online.getGameProfile().getName().equalsIgnoreCase(token)) {
                return online.getUUID();
            }
        }
        // 3) 账户表里的历史玩家名（离线玩家）
        for (Map.Entry<UUID, PlayerAccount> entry : data.accounts().entrySet()) {
            String name = entry.getValue().name;
            if (name != null && !name.isEmpty() && name.equalsIgnoreCase(token)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** 把可选目标列表下发到客户端（kind=admin_grant），界面打开后也会主动请求一次。 */
    public static void sendTargets(ServerPlayer admin) {
        // 防御纵深：即使将来有新的调用点忘了校验，也不会把全服名单下发给普通玩家。
        if (!admin.hasPermissions(2)) {
            return;
        }
        ListTag list = new ListTag();
        for (Target target : targets(admin.server)) {
            CompoundTag c = new CompoundTag();
            c.putString("uuid", target.uuid().toString());
            c.putString("name", target.name());
            c.putBoolean("online", target.online());
            list.add(c);
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", "admin_grant");
        tag.put("targets", list);
        tag.putString("currency", Config.INSTANCE.currencyName.get());
        PacketDistributor.sendToPlayer(admin, new ClientboundShopSyncPayload(tag));
    }

    // =========================================================== 打开发放界面

    /** 打开发放界面（容器界面：物品真正放在服务端槽位里，关闭时安全退还）。 */
    public static void open(ServerPlayer admin) {
        if (!admin.hasPermissions(2)) {
            return;
        }
        // 先下发目标列表，再打开界面；界面 init 时还会再请求一次，避免网络时序导致列表为空。
        sendTargets(admin);
        admin.openMenu(new SimpleMenuProvider(
                (id, inventory, player) -> new GrantMenu(id, inventory, new SimpleContainer(GRANT_SLOTS)),
                Component.translatable("gui.server_shop_mod.grant.title")));
    }

    // =========================================================== 发钱（指令）

    /** 发钱结果统计，用于给管理员回执。 */
    public record MoneyResult(int total, int offline, int clamped) {
    }

    /**
     * 给一批玩家各发 amount 金币（直接到账，超过余额上限的部分不再发放）。
     * 离线玩家同样入账，下次登录即可看到余额。
     */
    public static MoneyResult giveMoney(MinecraftServer server, Collection<UUID> targets, long amount) {
        if (amount <= 0L) {
            return new MoneyResult(0, 0, 0);
        }
        ShopSavedData data = ShopManager.data(server);
        long max = Config.INSTANCE.maxBalance.get();
        int total = 0;
        int offline = 0;
        int clamped = 0;
        synchronized (ShopManager.LOCK) {
            for (UUID id : new LinkedHashSet<>(targets)) {
                if (id == null) {
                    continue;
                }
                PlayerAccount account = data.account(id);
                long credited = ShopManager.credit(account, amount, max);
                total++;
                if (credited < amount) {
                    clamped++;
                }
                if (credited > 0L) {
                    TransactionLog.record(server, id, TransactionLog.ADMIN_MONEY, "管理员",
                            credited, account.balance, "");
                }
                ServerPlayer online = server.getPlayerList().getPlayer(id);
                if (online == null) {
                    offline++;
                } else {
                    // 按实际到账金额提示，避免出现“你获得了 100 万，当前余额 999999999”这种自相矛盾的提示。
                    if (credited > 0L) {
                        notifyPlayer(online, Component.translatable("msg.server_shop_mod.admin_gave",
                                credited, account.balance).withStyle(ChatFormatting.GREEN));
                    } else {
                        notifyPlayer(online, Component.translatable("msg.server_shop_mod.grant_cap_reached")
                                .withStyle(ChatFormatting.GOLD));
                    }
                    online.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0F, 1.5F);
                }
            }
            data.setDirty();
        }
        return new MoneyResult(total, offline, clamped);
    }

    // =========================================================== 发放（界面发送）

    /** 处理发放界面的“发送”动作。 */
    public static void send(ServerPlayer admin, CompoundTag tag) {
        if (!admin.hasPermissions(2)) {
            return;
        }
        if (!(admin.containerMenu instanceof GrantMenu menu)) {
            notifyPlayer(admin, Component.translatable("msg.server_shop_mod.grant_no_menu")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        Container container = menu.grant();
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                items.add(stack.copy());
            }
        }
        // 金额按余额上限夹取：单人要不了更多，同时杜绝任何加法溢出（credit 里还有一层饱和保护）。
        long amount = Math.min(Math.max(0L, tag.getLong("amount")), Config.INSTANCE.maxBalance.get());
        boolean all = tag.getBoolean("all");
        ShopSavedData data = ShopManager.data(admin.server);
        Set<UUID> targetIds = new LinkedHashSet<>();
        if (all) {
            targetIds.addAll(allKnownTargets(admin.server));
        } else {
            ListTag list = tag.getList("targets", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                try {
                    UUID id = UUID.fromString(list.getString(i));
                    // 只接受服务端已知账户，客户端无法凭空造出目标。
                    if (data.peek(id) != null || admin.server.getPlayerList().getPlayer(id) != null) {
                        targetIds.add(id);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        if (targetIds.isEmpty()) {
            notifyPlayer(admin, Component.translatable("msg.server_shop_mod.grant_no_target")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (items.isEmpty() && amount <= 0L) {
            notifyPlayer(admin, Component.translatable("msg.server_shop_mod.grant_nothing")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        // 先清空容器再关闭界面：GrantMenu.removed() 会把剩余物品退还管理员，
        // 不清空就会“既发出去又退回来”，等于凭空复制物品。
        for (int i = 0; i < container.getContainerSize(); i++) {
            container.setItem(i, ItemStack.EMPTY);
        }
        admin.closeContainer();

        String sender = admin.getGameProfile().getName();
        int mailboxCap = Config.INSTANCE.maxMailboxSize.get();
        int clamped = 0;
        int fullMailboxes = 0;
        int failed = 0;
        synchronized (ShopManager.LOCK) {
            for (UUID id : targetIds) {
                // 邮箱已满的目标：物品会按模组既有规则改走背包/掉落，先统计出来回报管理员，
                // 免得管理员以为东西已经安静地躺在对方邮箱里。
                if (!items.isEmpty() && data.account(id).mailbox.size() >= mailboxCap) {
                    fullMailboxes++;
                }
                try {
                    for (ItemStack stack : items) {
                        // 每件物品一封邮件（MailEntry 只携带一个 ItemStack）。
                        ShopManager.addMail(admin.server, id, stack.copy(), SOURCE_ADMIN_GRANT, sender);
                    }
                    if (!items.isEmpty()) {
                        // 流水只记摘要，避免把整包 NBT 塞进历史记录。
                        StringBuilder summary = new StringBuilder();
                        int shown = Math.min(3, items.size());
                        for (int i = 0; i < shown; i++) {
                            if (i > 0) {
                                summary.append("、");
                            }
                            summary.append(TransactionLog.itemText(items.get(i)));
                        }
                        if (items.size() > shown) {
                            summary.append(" 等").append(items.size()).append("种");
                        }
                        TransactionLog.record(admin.server, id, TransactionLog.ADMIN_ITEM, sender, 0L,
                                data.account(id).balance, summary.toString());
                    }
                    if (amount > 0L) {
                        if (GRANT_MONEY_VIA_MAIL) {
                            ShopManager.addMoneyMail(admin.server, id, amount, ItemStack.EMPTY, SOURCE_ADMIN_GRANT,
                                    sender, admin.getUUID());
                        } else {
                            clamped += giveMoneyLocked(admin.server, data, id, amount).clamped();
                        }
                    }
                } catch (RuntimeException ex) {
                    // 单个目标失败不应中断整批发放：统计后继续，最后回报管理员。
                    failed++;
                }
            }
            data.setDirty();
        }

        List<String> parts = new ArrayList<>();
        if (!items.isEmpty()) {
            parts.add(Component.translatable("msg.server_shop_mod.grant_sent_items", items.size()).getString());
        }
        if (amount > 0L) {
            parts.add(Component.translatable("msg.server_shop_mod.grant_sent_money", amount).getString());
        }
        notifyPlayer(admin, Component.translatable("msg.server_shop_mod.grant_sent",
                        Math.max(0, targetIds.size() - failed), String.join("、", parts))
                .withStyle(ChatFormatting.GREEN));
        if (clamped > 0) {
            notifyPlayer(admin, Component.translatable("msg.server_shop_mod.grant_clamped", clamped)
                    .withStyle(ChatFormatting.GOLD));
        }
        if (fullMailboxes > 0) {
            notifyPlayer(admin, Component.translatable("msg.server_shop_mod.grant_mailbox_full", fullMailboxes)
                    .withStyle(ChatFormatting.GOLD));
        }
        if (failed > 0) {
            notifyPlayer(admin, Component.translatable("msg.server_shop_mod.grant_failed", failed)
                    .withStyle(ChatFormatting.RED));
        }
        // 每人邮件数超过邮箱上限时提前提醒，免得发完才发现对方邮箱爆了。
        if (!items.isEmpty() && items.size() > mailboxCap) {
            notifyPlayer(admin, Component.translatable("msg.server_shop_mod.grant_too_many_mails",
                            items.size(), mailboxCap)
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    /** 与 {@link #giveMoney} 相同的入账逻辑，供已在 LOCK 内的发送流程复用（不再重复加锁）。 */
    private static MoneyResult giveMoneyLocked(MinecraftServer server, ShopSavedData data, UUID id, long amount) {
        PlayerAccount account = data.account(id);
        long credited = ShopManager.credit(account, amount, Config.INSTANCE.maxBalance.get());
        int clamped = credited < amount ? 1 : 0;
        if (credited > 0L) {
            TransactionLog.record(server, id, TransactionLog.ADMIN_MONEY, "管理员", credited, account.balance, "");
        }
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            if (credited > 0L) {
                notifyPlayer(online, Component.translatable("msg.server_shop_mod.admin_gave", credited, account.balance)
                        .withStyle(ChatFormatting.GREEN));
            } else {
                notifyPlayer(online, Component.translatable("msg.server_shop_mod.grant_cap_reached")
                        .withStyle(ChatFormatting.GOLD));
            }
            online.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0F, 1.5F);
        }
        return new MoneyResult(1, online == null ? 1 : 0, clamped);
    }

    private static void notifyPlayer(ServerPlayer player, Component message) {
        player.displayClientMessage(message, false);
    }
}
