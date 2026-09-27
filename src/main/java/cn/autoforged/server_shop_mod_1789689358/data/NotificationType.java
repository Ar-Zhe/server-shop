package cn.autoforged.server_shop_mod_1789689358.data;

/**
 * 需求 10：商店各类“交易请求/事项”通知类型。
 * 服务端权威维护每名玩家各类型的未读数量，客户端据此在对应按钮上渲染红点/角标。
 */
public enum NotificationType {
    MAIL("mail"),
    AUCTION("auction"),
    STALL("stall"),
    SHOP("shop"),
    WALLET("wallet"),
    TRADE("trade");

    private final String id;

    NotificationType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static NotificationType byId(String id) {
        for (NotificationType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }
}
