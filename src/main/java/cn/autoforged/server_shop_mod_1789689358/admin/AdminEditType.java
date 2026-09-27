package cn.autoforged.server_shop_mod_1789689358.admin;

/**
 * 需求 11：管理员当前正在编辑的模块。
 * 以前每日商店与收购商店的配置混在一起，服务端无法根据槽位判断物品该写入哪个列表；
 * 现在由服务端记录该状态，放入物品时按此归属写入 DailyShopConfig / BuybackConfig。
 */
public enum AdminEditType {
    DAILY_SHOP,
    BUYBACK;

    public String id() {
        return this == DAILY_SHOP ? "shop" : "buyback";
    }

    public static AdminEditType byId(String id) {
        return "buyback".equalsIgnoreCase(id) ? BUYBACK : DAILY_SHOP;
    }
}
