package cn.autoforged.server_shop_mod_1789689358.data;

import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** 一笔拍卖挂单。 */
public final class AuctionEntry {
    public long id;
    public UUID seller;
    public String sellerName = "";
    public ItemStack stack = ItemStack.EMPTY;
    public long startPrice;
    public long currentBid;
    /** 需求 3：上一次出价金额，用于竞拍界面把被超过的价格划掉展示。0 表示还没有人出过价。 */
    public long lastBid;
    public UUID topBidder;
    public String topBidderName = "";
    public long endTime;

    public AuctionEntry() {
    }

    public boolean hasBid() {
        return topBidder != null;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = ShopNbt.saveStack(stack, registries);
        tag.putLong("id", id);
        if (seller != null) {
            tag.putUUID("seller", seller);
        }
        tag.putString("sellerName", sellerName);
        tag.putLong("startPrice", startPrice);
        tag.putLong("currentBid", currentBid);
        tag.putLong("lastBid", lastBid);
        if (topBidder != null) {
            tag.putUUID("topBidder", topBidder);
        }
        tag.putString("topBidderName", topBidderName);
        tag.putLong("endTime", endTime);
        return tag;
    }

    public static AuctionEntry load(CompoundTag tag, HolderLookup.Provider registries) {
        AuctionEntry entry = new AuctionEntry();
        entry.stack = ShopNbt.loadStack(tag, registries);
        entry.id = tag.getLong("id");
        if (tag.hasUUID("seller")) {
            entry.seller = tag.getUUID("seller");
        }
        entry.sellerName = tag.getString("sellerName");
        entry.startPrice = tag.getLong("startPrice");
        entry.currentBid = tag.getLong("currentBid");
        entry.lastBid = tag.contains("lastBid") ? tag.getLong("lastBid") : 0L;
        if (tag.hasUUID("topBidder")) {
            entry.topBidder = tag.getUUID("topBidder");
        }
        entry.topBidderName = tag.getString("topBidderName");
        entry.endTime = tag.getLong("endTime");
        return entry;
    }
}
