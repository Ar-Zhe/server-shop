package cn.autoforged.server_shop_mod_1789689358.data;

import cn.autoforged.server_shop_mod_1789689358.Config;
import net.minecraft.ChatFormatting;

/** 稀有度。颜色与需求给定的配色一致，权重、限购从配置读取。 */
public enum Rarity {
    LEGENDARY("legendary", 0xFFD700, ChatFormatting.GOLD, 5),
    EPIC("epic", 0x9B59B6, ChatFormatting.LIGHT_PURPLE, 4),
    RARE("rare", 0x3498DB, ChatFormatting.BLUE, 3),
    COMMON("common", 0x2ECC71, ChatFormatting.GREEN, 2);

    private final String id;
    private final int color;
    private final ChatFormatting formatting;
    private final int order;

    Rarity(String id, int color, ChatFormatting formatting, int order) {
        this.id = id;
        this.color = color;
        this.formatting = formatting;
        this.order = order;
    }

    public String id() {
        return id;
    }

    public int color() {
        return color;
    }

    public ChatFormatting formatting() {
        return formatting;
    }

    public int order() {
        return order;
    }

    public String translationKey() {
        return "rarity.server_shop_mod." + id;
    }

    public int weight() {
        return switch (this) {
            case LEGENDARY -> Config.INSTANCE.weightLegendary.get();
            case EPIC -> Config.INSTANCE.weightEpic.get();
            case RARE -> Config.INSTANCE.weightRare.get();
            case COMMON -> Config.INSTANCE.weightCommon.get();
        };
    }

    public int purchaseLimit() {
        return switch (this) {
            case LEGENDARY -> Config.INSTANCE.limitLegendary.get();
            case EPIC -> Config.INSTANCE.limitEpic.get();
            case RARE -> Config.INSTANCE.limitRare.get();
            case COMMON -> Config.INSTANCE.limitCommon.get();
        };
    }

    public static Rarity byId(String id) {
        for (Rarity rarity : values()) {
            if (rarity.id.equals(id)) {
                return rarity;
            }
        }
        return COMMON;
    }

    /** 需求 6/7：管理员输入品质时使用；无法识别返回 null，由调用方决定回退策略。 */
    @javax.annotation.Nullable
    public static Rarity byIdOrNull(String id) {
        if (id == null) {
            return null;
        }
        String trimmed = id.trim().toLowerCase();
        for (Rarity rarity : values()) {
            if (rarity.id.equals(trimmed)) {
                return rarity;
            }
        }
        return null;
    }

    /** 管理端未手动指定时，按价格猜测稀有度，保证越贵的越稀有。 */
    public static Rarity fromPrice(long price) {
        if (price >= 1000) {
            return LEGENDARY;
        }
        if (price >= 200) {
            return EPIC;
        }
        if (price >= 50) {
            return RARE;
        }
        return COMMON;
    }
}
