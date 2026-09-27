package cn.autoforged.server_shop_mod_1789689358.data;

import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * 邮箱中的一封邮件。
 * <p>
 * 字段与需求文档的 MailData 对应：mailId 为唯一标识，type 区分物品/金额，
 * sender/senderUUID 记录发件人，stack 为物品类邮件的完整 ItemStack（含 NBT/组件），
 * currencyAmount 为金额类邮件的星币数额，isClaimed/isViewed 记录状态，sentAt 为现实时间戳。
 * <p>
 * 物品类邮件在 isClaimed=true 后仍需玩家手动删除；金额类邮件在 isViewed=true 后同理。
 * 变灰逻辑由客户端 Screen 渲染时根据这两个标记判断，绝不改写 ItemStack 本身。
 */
public final class MailEntry {
    public String mailId = "";
    public MailType type = MailType.ITEM;
    public ItemStack stack = ItemStack.EMPTY;
    public String source = "admin";
    /** 邮件必须带发送者（系统邮件为空字符串）。 */
    public String sender = "";
    /** 发件人 UUID（系统邮件为 null）。 */
    public UUID senderUUID;
    /** 金额类邮件携带的星币数额（物品类为 0）。 */
    public long currencyAmount;
    /** 物品类邮件是否已领取。 */
    public boolean isClaimed;
    /** 金额类邮件是否已查看/领取。 */
    public boolean isViewed;
    /** 现实时间戳（epoch 毫秒），用于排序与界面显示。 */
    public long sentAt;
    public long receivedDay;

    public MailEntry() {
    }

    public MailEntry(ItemStack stack, String source, long receivedDay) {
        this.stack = stack;
        this.source = source;
        this.receivedDay = receivedDay;
        this.sentAt = System.currentTimeMillis();
        this.mailId = UUID.randomUUID().toString();
    }

    public MailEntry(ItemStack stack, String source, String sender, long receivedDay) {
        this(stack, source, receivedDay);
        this.sender = sender == null ? "" : sender;
    }

    /** 构造一封物品类邮件（stack 需为调用方已 copy 的完整物品）。 */
    public static MailEntry item(ItemStack stack, String source, String sender, UUID senderUUID, long receivedDay) {
        MailEntry entry = new MailEntry(stack, source, sender == null ? "" : sender, receivedDay);
        entry.type = MailType.ITEM;
        entry.senderUUID = senderUUID;
        return entry;
    }

    /** 构造一封金额类邮件；display 为展示用图标（可为空，仅用于界面显示，不可领取）。 */
    public static MailEntry money(long amount, ItemStack display, String source, String sender,
            UUID senderUUID, long receivedDay) {
        MailEntry entry = new MailEntry();
        entry.mailId = UUID.randomUUID().toString();
        entry.type = MailType.MONEY;
        entry.currencyAmount = Math.max(0L, amount);
        entry.stack = display == null ? ItemStack.EMPTY : display.copy();
        entry.source = source;
        entry.sender = sender == null ? "" : sender;
        entry.senderUUID = senderUUID;
        entry.receivedDay = receivedDay;
        entry.sentAt = System.currentTimeMillis();
        return entry;
    }

    /** 需求：物品类邮件是否还没被领取。 */
    public boolean pendingItem() {
        return type == MailType.ITEM && !isClaimed;
    }

    /** 需求：金额类邮件是否还没被查看/领取。 */
    public boolean pendingMoney() {
        return type == MailType.MONEY && !isViewed;
    }

    /** 未处理（删除前需要二次确认）。 */
    public boolean pending() {
        return pendingItem() || pendingMoney();
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = ShopNbt.saveStack(stack, registries);
        tag.putString("mailId", mailId);
        tag.putString("type", type.id());
        tag.putString("source", source);
        tag.putString("sender", sender);
        if (senderUUID != null) {
            tag.putUUID("senderUUID", senderUUID);
        }
        tag.putLong("currencyAmount", currencyAmount);
        tag.putBoolean("isClaimed", isClaimed);
        tag.putBoolean("isViewed", isViewed);
        tag.putLong("sentAt", sentAt);
        tag.putLong("receivedDay", receivedDay);
        return tag;
    }

    public static MailEntry load(CompoundTag tag, HolderLookup.Provider registries) {
        MailEntry entry = new MailEntry();
        entry.stack = ShopNbt.loadStack(tag, registries);
        // 旧存档没有 mailId 时现补一个，保证每封邮件都有稳定身份。
        entry.mailId = tag.contains("mailId") ? tag.getString("mailId") : UUID.randomUUID().toString();
        entry.type = MailType.byId(tag.contains("type") ? tag.getString("type") : "item");
        entry.source = tag.getString("source");
        entry.sender = tag.contains("sender") ? tag.getString("sender") : "";
        if (tag.hasUUID("senderUUID")) {
            entry.senderUUID = tag.getUUID("senderUUID");
        }
        entry.currencyAmount = tag.contains("currencyAmount") ? tag.getLong("currencyAmount") : 0L;
        entry.isClaimed = tag.getBoolean("isClaimed");
        entry.isViewed = tag.getBoolean("isViewed");
        // 旧存档没有 sentAt 时退回当日时间，保证时间戳始终有效。
        entry.sentAt = tag.contains("sentAt") ? tag.getLong("sentAt") : 0L;
        entry.receivedDay = tag.getLong("receivedDay");
        return entry;
    }
}
