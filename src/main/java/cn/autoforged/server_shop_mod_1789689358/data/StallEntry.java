package cn.autoforged.server_shop_mod_1789689358.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * 玩家摊位上的一件商品。order 用于保证“按上架先后顺序”排列。
 * 需求 4/5/6：价格按单件计算，stock 为该商品剩余库存（卖到 0 显示“已售空”，可补货）。
 */
public final class StallEntry {
    public ItemStack stack = ItemStack.EMPTY;
    public long price;
    public long order;
    /** 单件价格对应的库存数量（上限 64）。 */
    public int stock;
    /** 需求 9：该商品累计已售出的件数（按商品分别统计，不与其他商品合并）。 */
    public int sold;

    public StallEntry() {
    }

    public StallEntry(ItemStack stack, long price, long order) {
        this(stack, price, order, Math.max(1, stack.getCount()));
    }

    public StallEntry(ItemStack stack, long price, long order, int stock) {
        this.stack = stack;
        this.price = price;
        this.order = order;
        this.stock = Math.max(0, stock);
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = ShopNbt.saveStack(stack, registries);
        tag.putLong("price", price);
        tag.putLong("order", order);
        tag.putInt("stock", stock);
        tag.putInt("sold", sold);
        return tag;
    }

    public static StallEntry load(CompoundTag tag, HolderLookup.Provider registries) {
        StallEntry entry = new StallEntry();
        entry.stack = ShopNbt.loadStack(tag, registries);
        entry.price = tag.getLong("price");
        entry.order = tag.getLong("order");
        // 旧存档没有 stock 字段时，以 item 栈数量回退。
        entry.stock = tag.contains("stock") ? tag.getInt("stock") : Math.max(1, entry.stack.getCount());
        entry.sold = tag.contains("sold") ? tag.getInt("sold") : 0;
        return entry;
    }
}
