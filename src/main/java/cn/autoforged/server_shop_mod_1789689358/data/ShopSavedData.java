package cn.autoforged.server_shop_mod_1789689358.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

/** 全局的世界级商店数据。所有跨维度的持久化都集中在这里。 */
public final class ShopSavedData extends SavedData {
    private static final String FILE_ID = "server_shop_mod_data";

    private final Map<UUID, PlayerAccount> accounts = new HashMap<>();
    public final List<ConfiguredOffer> shopPool = new ArrayList<>();
    public final List<ConfiguredOffer> buybackPool = new ArrayList<>();
    public final List<AuctionEntry> auctions = new ArrayList<>();
    public long listingCounter = 1L;
    public long nextAuctionId = 1L;
    public long lastStallClearDay = Long.MIN_VALUE;
    /**
     * 同 IP 多账号管控：IP（已把 . 和 : 换成 _ 以免与 NBT 路径分隔符冲突）→ 首次登录的账号。
     * 见 Config.ipAccountLimit。
     */
    public final Map<String, UUID> ipOwner = new HashMap<>();

    public PlayerAccount account(UUID uuid) {
        PlayerAccount account = accounts.get(uuid);
        if (account == null) {
            account = new PlayerAccount();
            account.balance = cn.autoforged.server_shop_mod_1789689358.Config.INSTANCE.initialBalance.get();
            accounts.put(uuid, account);
            setDirty();
        }
        return account;
    }

    public PlayerAccount peek(UUID uuid) {
        return accounts.get(uuid);
    }

    public Map<UUID, PlayerAccount> accounts() {
        return accounts;
    }

    public List<AuctionEntry> auctionsOf(UUID seller) {
        List<AuctionEntry> result = new ArrayList<>();
        for (AuctionEntry entry : auctions) {
            if (entry.seller != null && entry.seller.equals(seller)) {
                result.add(entry);
            }
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag accountsTag = new CompoundTag();
        for (Map.Entry<UUID, PlayerAccount> entry : accounts.entrySet()) {
            accountsTag.put(entry.getKey().toString(), entry.getValue().save(registries));
        }
        tag.put("accounts", accountsTag);

        tag.put("shopPool", saveOffers(shopPool, registries));
        tag.put("buybackPool", saveOffers(buybackPool, registries));
        tag.put("auctions", saveAuctions(registries));
        tag.putLong("listingCounter", listingCounter);
        tag.putLong("nextAuctionId", nextAuctionId);
        tag.putLong("lastStallClearDay", lastStallClearDay);

        CompoundTag ipTag = new CompoundTag();
        for (Map.Entry<String, UUID> entry : ipOwner.entrySet()) {
            ipTag.putString(entry.getKey(), entry.getValue().toString());
        }
        tag.put("ipOwner", ipTag);
        return tag;
    }

    public static ShopSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ShopSavedData data = new ShopSavedData();

        CompoundTag accountsTag = tag.getCompound("accounts");
        for (String key : accountsTag.getAllKeys()) {
            try {
                data.accounts.put(UUID.fromString(key), PlayerAccount.load(accountsTag.getCompound(key), registries));
            } catch (IllegalArgumentException ignored) {
            }
        }

        ListTag shopList = tag.getList("shopPool", Tag.TAG_COMPOUND);
        for (int i = 0; i < shopList.size(); i++) {
            data.shopPool.add(ConfiguredOffer.load(shopList.getCompound(i), registries));
        }
        ListTag buybackList = tag.getList("buybackPool", Tag.TAG_COMPOUND);
        for (int i = 0; i < buybackList.size(); i++) {
            data.buybackPool.add(ConfiguredOffer.load(buybackList.getCompound(i), registries));
        }
        ListTag auctionList = tag.getList("auctions", Tag.TAG_COMPOUND);
        for (int i = 0; i < auctionList.size(); i++) {
            data.auctions.add(AuctionEntry.load(auctionList.getCompound(i), registries));
        }
        data.listingCounter = tag.getLong("listingCounter");
        if (data.listingCounter <= 0) {
            data.listingCounter = 1L;
        }
        data.nextAuctionId = tag.getLong("nextAuctionId");
        if (data.nextAuctionId <= 0) {
            data.nextAuctionId = 1L;
        }
        data.lastStallClearDay = tag.contains("lastStallClearDay") ? tag.getLong("lastStallClearDay") : Long.MIN_VALUE;

        CompoundTag ipTag = tag.getCompound("ipOwner");
        for (String key : ipTag.getAllKeys()) {
            try {
                data.ipOwner.put(key, UUID.fromString(ipTag.getString(key)));
            } catch (IllegalArgumentException ignored) {
            }
        }

        data.seedDefaultsIfEmpty();
        return data;
    }

    private ListTag saveOffers(List<ConfiguredOffer> offers, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (ConfiguredOffer offer : offers) {
            list.add(offer.save(registries));
        }
        return list;
    }

    private ListTag saveAuctions(HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (AuctionEntry entry : auctions) {
            list.add(entry.save(registries));
        }
        return list;
    }

    private static ShopSavedData create() {
        ShopSavedData data = new ShopSavedData();
        data.seedDefaultsIfEmpty();
        return data;
    }

    private static final Factory<ShopSavedData> FACTORY = new Factory<>(ShopSavedData::create, ShopSavedData::load);

    public static ShopSavedData get(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        return storage.computeIfAbsent(FACTORY, FILE_ID);
    }

    // ============================ 默认商品池 ============================

    private void seedDefaultsIfEmpty() {
        if (shopPool.isEmpty() && buybackPool.isEmpty()) {
            addDefault(shopPool, Items.APPLE, 5, Rarity.COMMON);
            addDefault(shopPool, Items.BREAD, 8, Rarity.COMMON);
            addDefault(shopPool, Items.TORCH, 2, Rarity.COMMON);
            addDefault(shopPool, Items.COAL, 4, Rarity.COMMON);
            addDefault(shopPool, Items.OAK_LOG, 3, Rarity.COMMON);
            addDefault(shopPool, Items.WHEAT_SEEDS, 2, Rarity.COMMON);
            addDefault(shopPool, Items.STRING, 3, Rarity.COMMON);
            addDefault(shopPool, Items.LEATHER, 6, Rarity.COMMON);

            addDefault(shopPool, Items.IRON_INGOT, 60, Rarity.RARE);
            addDefault(shopPool, Items.GOLD_INGOT, 80, Rarity.RARE);
            addDefault(shopPool, Items.REDSTONE, 60, Rarity.RARE);
            addDefault(shopPool, Items.LAPIS_LAZULI, 70, Rarity.RARE);
            addDefault(shopPool, Items.ENDER_PEARL, 90, Rarity.RARE);

            addDefault(shopPool, Items.DIAMOND, 300, Rarity.EPIC);
            addDefault(shopPool, Items.EMERALD, 260, Rarity.EPIC);
            addDefault(shopPool, Items.NETHERITE_SCRAP, 500, Rarity.EPIC);
            addDefault(shopPool, Items.GOLDEN_APPLE, 400, Rarity.EPIC);

            addDefault(shopPool, Items.NETHERITE_INGOT, 1500, Rarity.LEGENDARY);
            addDefault(shopPool, Items.DIAMOND_BLOCK, 2500, Rarity.LEGENDARY);
            addDefault(shopPool, Items.ELYTRA, 5000, Rarity.LEGENDARY);
            addDefault(shopPool, Items.NETHER_STAR, 8000, Rarity.LEGENDARY);
            addDefault(shopPool, Items.BEACON, 3000, Rarity.LEGENDARY);

            addDefault(buybackPool, Items.WHEAT, 4, Rarity.COMMON);
            addDefault(buybackPool, Items.CARROT, 4, Rarity.COMMON);
            addDefault(buybackPool, Items.POTATO, 4, Rarity.COMMON);
            addDefault(buybackPool, Items.COBBLESTONE, 1, Rarity.COMMON);
            addDefault(buybackPool, Items.OAK_LOG, 2, Rarity.COMMON);
            addDefault(buybackPool, Items.BONE, 3, Rarity.COMMON);
            addDefault(buybackPool, Items.IRON_INGOT, 40, Rarity.RARE);
            addDefault(buybackPool, Items.GOLD_INGOT, 55, Rarity.RARE);
            addDefault(buybackPool, Items.DIAMOND, 180, Rarity.EPIC);
            addDefault(buybackPool, Items.NETHERITE_SCRAP, 320, Rarity.EPIC);
        }
    }

    private static void addDefault(List<ConfiguredOffer> pool, net.minecraft.world.item.Item item, long price, Rarity rarity) {
        pool.add(new ConfiguredOffer(new ItemStack(item), price, rarity));
    }
}
