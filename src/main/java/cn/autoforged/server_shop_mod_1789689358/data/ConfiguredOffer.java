package cn.autoforged.server_shop_mod_1789689358.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * 管理员配置的商品/收购项。
 * 需求 6/7：除价格、品质外，还可为该条目单独配置“数量”：
 * 每日商店代表该物品的可购买上限；每日收购代表该物品可收购的次数上限。
 */
public final class ConfiguredOffer {
    public ItemStack stack = ItemStack.EMPTY;
    public long price;
    public Rarity rarity = Rarity.COMMON;
    /** 需求 6/7：数量（可购买/可收购上限）。0 表示未配置，使用对应场景的默认值。 */
    public int amount;

    public ConfiguredOffer() {
    }

    public ConfiguredOffer(ItemStack stack, long price, Rarity rarity) {
        this.stack = stack;
        this.price = price;
        this.rarity = rarity;
    }

    /** 需求 6/7：返回配置数量，未配置（<=0）时退回到调用方给出的默认值。 */
    public int amountOr(int fallback) {
        return amount > 0 ? amount : fallback;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = ShopNbt.saveStack(stack, registries);
        tag.putLong("price", price);
        tag.putString("rarity", rarity.id());
        tag.putInt("amount", amount);
        return tag;
    }

    public static ConfiguredOffer load(CompoundTag tag, HolderLookup.Provider registries) {
        ConfiguredOffer offer = new ConfiguredOffer();
        offer.stack = ShopNbt.loadStack(tag, registries);
        offer.price = tag.getLong("price");
        offer.rarity = Rarity.byId(tag.getString("rarity"));
        offer.amount = tag.getInt("amount");
        return offer;
    }
}
