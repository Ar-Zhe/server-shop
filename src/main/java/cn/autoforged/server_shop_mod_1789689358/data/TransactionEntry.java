package cn.autoforged.server_shop_mod_1789689358.data;

import net.minecraft.nbt.CompoundTag;

/**
 * 一条资产变动流水（管理员用 {@code /shop admin history <玩家>} 查看）。
 * <p>
 * 只保存文本摘要，不保存 ItemStack，避免每名玩家几十条记录把存档撑大。
 */
public final class TransactionEntry {
    /** 现实时间戳（epoch 毫秒）。 */
    public long time;
    /** 流水类型 id，见 {@code shop.TransactionLog} 的常量。 */
    public String type = "";
    /** 对方玩家名（商店/系统流水写“服务器商店”等，可为空）。 */
    public String counterParty = "";
    /** 金币变动：正=收入，负=支出，0=纯物品变动。 */
    public long amount;
    /** 变动后余额。 */
    public long balanceAfter;
    /** 物品等文本说明（例如“钻石×64”）。 */
    public String note = "";

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("time", time);
        tag.putString("type", type == null ? "" : type);
        tag.putString("counterParty", counterParty == null ? "" : counterParty);
        tag.putLong("amount", amount);
        tag.putLong("balanceAfter", balanceAfter);
        tag.putString("note", note == null ? "" : note);
        return tag;
    }

    public static TransactionEntry load(CompoundTag tag) {
        TransactionEntry entry = new TransactionEntry();
        entry.time = tag.getLong("time");
        entry.type = tag.getString("type");
        entry.counterParty = tag.contains("counterParty") ? tag.getString("counterParty") : "";
        entry.amount = tag.getLong("amount");
        entry.balanceAfter = tag.getLong("balanceAfter");
        entry.note = tag.getString("note");
        return entry;
    }
}
