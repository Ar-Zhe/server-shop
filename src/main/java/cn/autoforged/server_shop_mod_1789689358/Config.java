package cn.autoforged.server_shop_mod_1789689358;

import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

/**
 * 服务器商店的公共配置。所有数值都可以由服主在 config/server_shop_mod_1789689358-common.toml 中调整。
 * 需求里出现冲突的属性，这里采用“以玩法意图优先”的折中默认值，并在注释中标注。
 */
public final class Config {
    public static final Config INSTANCE;
    public static final ModConfigSpec SPEC;

    static {
        Pair<Config, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(Config::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }

    // ---- 钱包 ----
    public final ModConfigSpec.ConfigValue<String> currencyName;
    public final ModConfigSpec.ConfigValue<String> currencySymbol;
    public final ModConfigSpec.LongValue initialBalance;
    public final ModConfigSpec.LongValue maxBalance;
    public final ModConfigSpec.IntValue transferCooldownTicks;
    public final ModConfigSpec.BooleanValue allowPlayerTransfer;

    // ---- 每日商店 ----
    public final ModConfigSpec.IntValue dailyShopSlots;
    /** 每日商店里“普通”档位最多出现几个（0=不限制）。 */
    public final ModConfigSpec.IntValue dailyShopCommonLimit;
    /** 付费刷新每日商店的价格表（按次数顺序取用：第 1 次、第 2 次…），用完即不可再刷新。 */
    public final ModConfigSpec.ConfigValue<List<? extends String>> dailyRefreshCosts;
    public final ModConfigSpec.IntValue weightLegendary;
    public final ModConfigSpec.IntValue weightEpic;
    public final ModConfigSpec.IntValue weightRare;
    public final ModConfigSpec.IntValue weightCommon;
    public final ModConfigSpec.IntValue limitLegendary;
    public final ModConfigSpec.IntValue limitEpic;
    public final ModConfigSpec.IntValue limitRare;
    public final ModConfigSpec.IntValue limitCommon;

    // ---- 每日收购 ----
    public final ModConfigSpec.IntValue buybackSlots;
    public final ModConfigSpec.IntValue buybackCapacity;
    /** 当日把收购清单全部卖完的一次性奖励（0=关闭，默认 1000）。 */
    public final ModConfigSpec.LongValue buybackAllBonus;

    // ---- 拍卖（时长全部按现实时间，单位秒）----
    public final ModConfigSpec.IntValue auctionDurationSeconds;
    public final ModConfigSpec.LongValue auctionMinIncrement;
    public final ModConfigSpec.IntValue maxConcurrentAuctions;
    /** 需求 2：拍卖成交后从卖家所得中扣除的手续费百分比（默认 5%）。 */
    public final ModConfigSpec.DoubleValue auctionFeePercent;
    public final ModConfigSpec.BooleanValue antiSnipe;
    public final ModConfigSpec.IntValue antiSnipeSeconds;
    public final ModConfigSpec.IntValue antiSnipeExtensionSeconds;

    // ---- 邮箱 ----
    /** 邮箱上限（默认 100；界面每页 45 封，可翻页）。达到上限时系统资产返还优先改为掉落入包，防止溢出刷物品。 */
    public final ModConfigSpec.IntValue maxMailboxSize;
    public final ModConfigSpec.IntValue mailExpireDays;
    /** 每名玩家保留的交易流水条数（管理员用 /shop admin history 查看）。 */
    public final ModConfigSpec.IntValue transactionHistorySize;

    // ---- 摊位 ----
    public final ModConfigSpec.IntValue stallSlots;
    public final ModConfigSpec.DoubleValue stallListingFeePercent;

    // ---- 玩家交易（现实时间，单位秒）----
    public final ModConfigSpec.IntValue tradeCountdownSeconds;
    /** 需求 1：交易请求失效时间（现实秒），超时自动清理。 */
    public final ModConfigSpec.IntValue tradeRequestExpireSeconds;
    /** 已接受的交易多久没有任何操作就自动结束并退回物品（现实秒，默认 10 分钟）。 */
    public final ModConfigSpec.IntValue tradeSessionTimeoutSeconds;

    // ---- 同 IP 多账号管控 ----
    /** 每个 IP 允许绑定的账号数：1=一个 IP 只能有一个账号登录（0=关闭）。管理员默认豁免。 */
    public final ModConfigSpec.IntValue ipAccountLimit;
    /** 拥有 2 级权限的管理员是否豁免 IP 限制（方便管理员多开测试，默认 true）。 */
    public final ModConfigSpec.BooleanValue ipLimitExemptOps;

    private Config(ModConfigSpec.Builder builder) {
        builder.push("wallet");
        currencyName = builder.comment("货币名称（需求：币种统一为金币，所有金额显示统一追加该后缀）").define("currencyName", "金币");
        currencySymbol = builder.comment("货币符号，留空则只显示数字").define("currencySymbol", "");
        initialBalance = builder.comment("新玩家初始余额").defineInRange("initialBalance", 0L, 0L, 999999999L);
        maxBalance = builder.comment("余额上限").defineInRange("maxBalance", 999999999L, 1L, Long.MAX_VALUE);
        transferCooldownTicks = builder.comment("玩家间转账冷却（tick）").defineInRange("transferCooldownTicks", 20, 0, 100000);
        allowPlayerTransfer = builder.comment("是否允许玩家间转账").define("allowPlayerTransfer", true);
        builder.pop();

        builder.push("daily_shop");
        // 界面固定 3 列 × 2 行卡片（每日商店没有翻页），配置上限就是 6。
        dailyShopSlots = builder.comment("每日商店槽位数量（界面固定 3 列 × 2 行，最多 6）").defineInRange("dailyShopSlots", 6, 1, 6);
        dailyShopCommonLimit = builder.comment("每日商店“普通”档位最多出现数量（0=不限制，默认 3）")
                .defineInRange("dailyShopCommonLimit", 3, 0, 6);
        dailyRefreshCosts = builder.comment("付费刷新每日商店的价格表（按次数顺序：默认第 1 次 200、第 2 次 500；可自行增删次数）")
                .defineList("dailyRefreshCosts", List.of("200", "500"), () -> "200", o -> o instanceof String);
        weightLegendary = builder.comment("传说权重").defineInRange("weightLegendary", 5, 0, 100000);
        weightEpic = builder.comment("史诗权重").defineInRange("weightEpic", 15, 0, 100000);
        weightRare = builder.comment("稀有权重").defineInRange("weightRare", 30, 0, 100000);
        weightCommon = builder.comment("普通权重").defineInRange("weightCommon", 50, 0, 100000);
        limitLegendary = builder.comment("每日每玩家可购买传说数量").defineInRange("limitLegendary", 1, 1, 1000);
        limitEpic = builder.comment("每日每玩家可购买史诗数量").defineInRange("limitEpic", 2, 1, 1000);
        limitRare = builder.comment("每日每玩家可购买稀有数量").defineInRange("limitRare", 5, 1, 1000);
        limitCommon = builder.comment("每日每玩家可购买普通数量").defineInRange("limitCommon", 10, 1, 1000);
        builder.pop();

        builder.push("daily_buyback");
        // 需求冲突：daily_slots=4 与 buyback_slots="6项(丰富)"。取可玩性更高的 6。
        // 界面只渲染 6 行（没有翻页），所以每日最多随机 6 项收购需求。
        buybackSlots = builder.comment("每日收购槽位数量（每日最多随机 6 项，界面只显示 6 行）").defineInRange("buybackSlots", 6, 1, 6);
        buybackCapacity = builder.comment("每个收购项每日可收购次数").defineInRange("buybackCapacity", 16, 1, 100000);
        buybackAllBonus = builder.comment("当日收购清单全部完成的一次性奖励金币（0=关闭）")
                .defineInRange("buybackAllBonus", 1000L, 0L, 999999999L);
        builder.pop();

        builder.push("auction");
        // 现实时间：7200 秒 = 2 小时
        auctionDurationSeconds = builder.comment("拍卖持续时间（现实秒，7200=2小时）").defineInRange("auctionDurationSeconds", 7200, 1, Integer.MAX_VALUE);
        // 需求冲突：min_bid_increment=1 与 bid_increment="10星币(标准)"。取标准值 10。
        auctionMinIncrement = builder.comment("最小加价幅度").defineInRange("auctionMinIncrement", 10L, 1L, Long.MAX_VALUE);
        maxConcurrentAuctions = builder.comment("每玩家同时进行的拍卖上限").defineInRange("maxConcurrentAuctions", 6, 1, 100);
        // 需求 2：交易成功扣除卖家 5% 手续费。
        auctionFeePercent = builder.comment("拍卖成交手续费百分比（从卖家所得中扣除）").defineInRange("auctionFeePercent", 5.0D, 0.0D, 100.0D);
        antiSnipe = builder.comment("是否开启防狙击").define("antiSnipe", true);
        antiSnipeSeconds = builder.comment("剩余多少秒内出价算狙击（现实秒）").defineInRange("antiSnipeSeconds", 60, 0, 3600);
        antiSnipeExtensionSeconds = builder.comment("每次狙击延长多少秒（现实秒，0 表示不延长）").defineInRange("antiSnipeExtensionSeconds", 0, 0, Integer.MAX_VALUE);
        builder.pop();

        builder.push("mailbox");
        maxMailboxSize = builder.comment("邮箱最大邮件数（默认 100，界面每页 45 封可翻页）").defineInRange("maxMailboxSize", 100, 1, 1000);
        mailExpireDays = builder.comment("邮件过期天数（0=永久，遵循 expire_time=永久；只清理已领取/已查看的邮件）").defineInRange("mailExpireDays", 0, 0, 100000);
        transactionHistorySize = builder.comment("每名玩家保留的交易流水条数（/shop admin history 查看）").defineInRange("transactionHistorySize", 50, 10, 200);
        builder.pop();

        builder.push("stall");
        stallSlots = builder.comment("摊位槽位数量").defineInRange("stallSlots", 12, 1, 45);
        // 需求冲突：free_listing=true 与 listing_fee="上架价5%"。默认免费，百分比留给服主调节。
        stallListingFeePercent = builder.comment("上架手续费百分比（0=免费上架）").defineInRange("stallListingFeePercent", 0.0D, 0.0D, 100.0D);
        builder.pop();

        builder.push("trade");
        tradeCountdownSeconds = builder.comment("双方同意后的确认倒计时（现实秒）").defineInRange("tradeCountdownSeconds", 3, 1, 60);
        // 需求 1：未被对方接受的交易请求超时后自动清理。
        tradeRequestExpireSeconds = builder.comment("交易请求失效时间（现实秒）").defineInRange("tradeRequestExpireSeconds", 60, 5, 3600);
        tradeSessionTimeoutSeconds = builder.comment("已接受的交易闲置多久自动结束并退回物品（现实秒，默认 600=10 分钟）")
                .defineInRange("tradeSessionTimeoutSeconds", 600, 30, 86400);
        builder.pop();

        builder.push("anti_alt");
        ipAccountLimit = builder.comment("每个 IP 允许绑定的账号数：1=同一个 IP 只能有一个账号登录（0=关闭；管理员默认豁免）")
                .defineInRange("ipAccountLimit", 1, 0, 5);
        ipLimitExemptOps = builder.comment("2 级权限管理员是否豁免 IP 限制（方便多开测试）").define("ipLimitExemptOps", true);
        builder.pop();
    }
}
