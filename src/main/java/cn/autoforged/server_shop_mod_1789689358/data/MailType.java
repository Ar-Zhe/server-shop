package cn.autoforged.server_shop_mod_1789689358.data;

/**
 * 邮件类型。
 * ITEM：物品类邮件，需要玩家点击“领取”把物品放进背包。
 * MONEY：金额类邮件，需要玩家点击“查看/领取”把星币加进钱包。
 */
public enum MailType {
    ITEM,
    MONEY;

    public String id() {
        return name().toLowerCase();
    }

    public static MailType byId(String id) {
        if (id == null) {
            return ITEM;
        }
        for (MailType type : values()) {
            if (type.id().equalsIgnoreCase(id) || type.name().equalsIgnoreCase(id)) {
                return type;
            }
        }
        return ITEM;
    }
}
